package com.seend.app.data.remote.s3

import android.util.Log
import com.seend.app.data.remote.ConnState
import com.seend.app.data.remote.MessageTransport
import com.seend.app.data.remote.parseEvento
import com.seend.app.data.remote.transportJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * S3TransportClient: transporte de mensajería store-and-forward sobre el
 * bucket s3.todus.cu/stickers, equivalente al WebSocketClient pero
 * tunelizado por S3 con el protocolo TDS3 del relay.
 *
 * Sustituye al WebSocket cuando BuildConfig.USE_S3_PROXY = true. Implementa
 * la misma interfaz [MessageTransport], así el SeendRepository no cambia.
 *
 * Flujo:
 *  - connect(): arranca el bucle de polling (100ms) del buzón de respuesta
 *    + el heartbeat de presencia/registro (30s). Emite Connected.
 *  - sendJson(json): serializa la petición, la sella como TDS3 y la sube a
 *    _seend_req_{cid}_{seq}, avanzando el índice _seend_reqidx_{cid}.
 *  - pollLoop: cada 100ms lee _seend_residx_{cid} y, si avanzó, abre cada
 *    _seend_res_{cid}_{seq} nueva, la decodifica y emite el [InEvento].
 *
 * El clientId es un UUID v4 sin guiones (32 hex) persistido por la app;
 * lo recibe el S3TransportClient ya generado (ver ClientIdStore).
 */
class S3TransportClient(
    private val httpClient: okhttp3.OkHttpClient,
    private val baseUrl: String,
    sharedSecretHex: String,
    private val clientId: String,
    private val pollMs: Long = 100L,
    private val userAgent: String = "curl/8.23.0",
) : MessageTransport {

    private val secret: ByteArray = hexToBytes(sharedSecretHex)
    private val aesKey: ByteArray = Crypto.deriveAESKey(secret)
    private val codec = EnvelopeCodec(secret, aesKey)
    private val store = S3Store(httpClient, baseUrl, codec, userAgent)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)
    private val outSeq = AtomicLong(0L)

    // Último seq de respuesta ya leído. Sólo tocado por pollLoop (1 corrutina).
    @Volatile private var inSeq: Long = 0L

    private val _events = MutableSharedFlow<InEventoLike>(replay = 0, extraBufferCapacity = 64)
    override val events: SharedFlow<InEventoLike> = _events.asSharedFlow()

    private val _state = MutableSharedFlow<ConnState>(extraBufferCapacity = 8)
    override val state: SharedFlow<ConnState> = _state.asSharedFlow()

    init {
        require(clientId.length == 32 && clientId.all { it.isLetterOrDigit() }) {
            "clientId debe ser 32 hex sin guiones"
        }
        require(secret.size >= 32) { "shared secret debe ser >= 32 bytes" }
    }

    override fun connect() {
        if (!running.compareAndSet(false, true)) return
        Log.i(TAG, "S3 transport conectado cid=${clientId.take(8)} base=$baseUrl poll=${pollMs}ms")
        scope.launch {
            _state.emit(ConnState.Connecting)
            // Registro inicial + presencia.
            register()
            _state.emit(ConnState.Connected)
            // Bucles.
            launch { presenceLoop() }
            launch { pollLoop() }
        }
    }

    override fun disconnect() {
        if (!running.compareAndSet(true, false)) return
        scope.coroutineContext[Job]?.cancelChildren()
        Log.i(TAG, "S3 transport desconectado cid=${clientId.take(8)}")
        scope.launch { _state.emit(ConnState.Disconnected) }
    }

    /** Encola una petición JSON para subirla al buzón de peticiones. */
    override fun sendJson(jsonString: String): Boolean {
        if (!running.get()) return false
        val seq = outSeq.incrementAndGet()
        val data = jsonString.toByteArray(Charsets.UTF_8)
        scope.launch {
            if (!store.put(Keys.req(clientId, seq), data)) {
                Log.w(TAG, "put req fallido seq=$seq")
                return@launch
            }
            store.put(Keys.reqIdx(clientId), Counter.encode(seq))
        }
        return true
    }

    /** Publica el heartbeat de presencia y se añade al registro de clientes. */
    private suspend fun register() {
        val now = nowSecs()
        val reg = RegDoc(clientId = clientId, ts = now)
        store.put(Keys.reg(clientId), transportJson.encodeToString(RegDoc.serializer(), reg).toByteArray())

        val raw = store.get(Keys.clients())
        val existing = if (raw != null) {
            runCatching { transportJson.decodeFromString(ClientsDoc.serializer(), raw.toString(Charsets.UTF_8)) }
                .getOrNull()?.clients ?: emptyList()
        } else emptyList()
        if (clientId !in existing) {
            val merged = (existing + clientId).distinct()
            val doc = ClientsDoc(clients = merged, ts = now)
            // Last-write-wins: toleramos la carrera; el heartbeat periódico
            // re-añade el clientId si otra escritura lo pierde.
            store.put(Keys.clients(), transportJson.encodeToString(ClientsDoc.serializer(), doc).toByteArray())
        }
    }

    private suspend fun presenceLoop() {
        while (running.get()) {
            delay(PRESENCE_INTERVAL_MS)
            if (!running.get()) return
            register()
        }
    }

    /** Pool constante del buzón de respuesta cada pollMs (100ms). */
    private suspend fun pollLoop() {
        while (running.get()) {
            delay(pollMs)
            if (!running.get()) return
            val idx = Counter.decode(store.get(Keys.resIdx(clientId)))
            if (idx <= inSeq) continue
            for (seq in (inSeq + 1)..idx) {
                inSeq = seq
                val raw = store.get(Keys.res(clientId, seq)) ?: continue
                val text = raw.toString(Charsets.UTF_8)
                val ev = parseEvento(text)
                if (ev != null) {
                    _events.emit(ev)
                } else {
                    Log.w(TAG, "resp no parseable seq=$seq")
                }
            }
        }
    }

    private fun nowSecs(): Double = System.nanoTime() / 1_000_000_000.0

    private companion object {
        const val TAG = "SeendS3T"
        const val PRESENCE_INTERVAL_MS = 30_000L
    }
}

// Alias para mantener el import mínimo: InEvento viene del paquete model.
private typealias InEventoLike = com.seend.app.data.model.InEvento

@Serializable
private data class ClientsDoc(
    @SerialName("clients") val clients: List<String> = emptyList(),
    val ts: Double = 0.0,
)

@Serializable
private data class RegDoc(
    @SerialName("clientId") val clientId: String = "",
    val ts: Double = 0.0,
)

/** hexToBytes: decodifica un hex string (min 64 chars → 32 bytes). */
internal fun hexToBytes(hex: String): ByteArray {
    require(hex.length % 2 == 0) { "hex length impar" }
    val out = ByteArray(hex.length / 2)
    var i = 0
    while (i < hex.length) {
        val hi = hex[i].digitValueOrNull() ?: throw IllegalArgumentException("hex inválido")
        val lo = hex[i + 1].digitValueOrNull() ?: throw IllegalArgumentException("hex inválido")
        out[i / 2] = ((hi shl 4) or lo).toByte()
        i += 2
    }
    return out
}

private fun Char.digitValueOrNull(): Int? = when (this) {
    in '0'..'9' -> this - '0'
    in 'a'..'f' -> this - 'a' + 10
    in 'A'..'F' -> this - 'A' + 10
    else -> null
}
