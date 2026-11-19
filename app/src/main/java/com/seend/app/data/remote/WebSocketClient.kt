package com.seend.app.data.remote

import android.util.Log
import com.seend.app.data.model.InEvento
import com.seend.app.data.model.InMensaje
import com.seend.app.data.model.InAuthOk
import com.seend.app.data.model.InConversacion
import com.seend.app.data.model.InError
import com.seend.app.data.model.InEscribiendo
import com.seend.app.data.model.InPendientes
import com.seend.app.data.model.InPerfil
import com.seend.app.data.model.InPerfilActualizado
import com.seend.app.data.model.InPerfilActualizadoOtro
import com.seend.app.data.model.InPresencia
import com.seend.app.data.model.InSesionInvalida
import com.seend.app.data.model.InSesionValida
import com.seend.app.data.model.InUsuarios
import com.seend.app.data.model.SobreTipo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

/**
 * Estado de conexión expuesto a la UI.
 */
sealed interface ConnState {
    object Disconnected : ConnState
    object Connecting : ConnState
    object Connected : ConnState
    data class Reconnecting(val intento: Int) : ConnState
}

/**
 * WebSocketClient: wraps a single OkHttp WebSocket with auto-reconnect
 * (exponential backoff capped at 30s). Emite un [SharedFlow] de eventos
 * del protocolo y otro de [ConnState].
 *
 * El cliente no conoce el dominio; sólo serializa y deserializa JSON.
 * El repository decide qué hacer con cada [InEvento].
 */
class WebSocketClient(
    private val baseUrl: String,
    private val httpClient: OkHttpClient,
) : MessageTransport {
    companion object {
        private const val TAG = "SeendWS"
        private const val PATH = "/ws"
        private const val MAX_BACKOFF_MS = 30_000L
        private const val NORMAL_CLOSURE = 1000
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var ws: WebSocket? = null
    private var connectedJob: Job? = null
    private var manualClose = false
    private var reconnectAttempts = 0

    private val _events = MutableSharedFlow<InEvento>(
        replay = 0,
        extraBufferCapacity = 64,
    )
    override val events: SharedFlow<InEvento> = _events.asSharedFlow()

    private val _state = MutableSharedFlow<ConnState>(extraBufferCapacity = 8)
    override val state: SharedFlow<ConnState> = _state.asSharedFlow()

    /** Conecta al WebSocket. Idempotente si ya está conectado. */
    override fun connect() {
        if (ws != null) return
        manualClose = false
        _state.tryEmit(ConnState.Connecting)
        val wsUrl = httpToWs(baseUrl) + PATH
        Log.i(TAG, "Conectando a $wsUrl")
        val req = Request.Builder().url(wsUrl).build()
        ws = httpClient.newWebSocket(req, SeendListener())
    }

    /** Cierra limpiamente (sin reintentar reconexión). */
    override fun disconnect() {
        manualClose = true
        connectedJob?.cancel()
        ws?.close(NORMAL_CLOSURE, "client close")
        ws = null
        scope.launch { _state.emit(ConnState.Disconnected) }
    }

    /** Envía un JSON ya serializado al WebSocket. false si no hay conexión. */
    override fun sendJson(jsonString: String): Boolean {
        val w = ws ?: return false
        return w.send(jsonString)
    }

    /** Convierte http(s):// a ws(s):// */
    private fun httpToWs(u: String): String =
        when {
            u.startsWith("https://") -> "wss://" + u.removePrefix("https://")
            u.startsWith("http://") -> "ws://" + u.removePrefix("http://")
            else -> u
        }.trimEnd('/')

    private inner class SeendListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i(TAG, "WebSocket abierto")
            reconnectAttempts = 0
            scope.launch { _state.emit(ConnState.Connected) }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleText(text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            handleText(bytes.utf8())
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(NORMAL_CLOSURE, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "WebSocket cerrado: $code $reason")
            ws = null
            scope.launch {
                _state.emit(ConnState.Disconnected)
                if (!manualClose) scheduleReconnect()
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "WebSocket failure: ${t.message}")
            ws = null
            scope.launch {
                _state.emit(ConnState.Disconnected)
                if (!manualClose) scheduleReconnect()
            }
        }
    }

    private fun scheduleReconnect() {
        reconnectAttempts++
        val delayMs = (500L * (1 shl (reconnectAttempts - 1)))
            .coerceAtMost(MAX_BACKOFF_MS)
        scope.launch {
            _state.emit(ConnState.Reconnecting(reconnectAttempts))
            delay(delayMs)
            if (!manualClose) connect()
        }
    }

    private fun handleText(text: String) {
        val evento = parseEvento(text)
        if (evento == null) {
            Log.w(TAG, "Error parseando mensaje")
            return
        }
        _events.tryEmit(evento)
    }
}

/** Builder de OkHttpClient compartido. */
fun buildOkHttp(): OkHttpClient = OkHttpClient.Builder()
    .pingInterval(20, TimeUnit.SECONDS) // keepalive a nivel WS
    .readTimeout(0, TimeUnit.SECONDS) // WS no tiene read timeout
    .connectTimeout(15, TimeUnit.SECONDS)
    .build()
