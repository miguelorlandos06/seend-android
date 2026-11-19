package com.seend.app.data.repo

import android.content.Context
import android.util.Log
import com.seend.app.data.local.MensajeEntity
import com.seend.app.data.local.SeendDatabase
import com.seend.app.data.local.SesionLocalEntity
import com.seend.app.data.local.UsuarioEntity
import com.seend.app.data.model.Cita
import com.seend.app.data.model.ChatPreviewLocal
import com.seend.app.data.model.EstadoMensaje
import com.seend.app.data.model.InEvento
import com.seend.app.data.model.InPerfil
import com.seend.app.data.model.Mensaje
import com.seend.app.data.model.OutAbrirChat
import com.seend.app.data.model.OutAck
import com.seend.app.data.model.OutActualizarPerfil
import com.seend.app.data.model.OutEscribiendo
import com.seend.app.data.model.OutEntrar
import com.seend.app.data.model.OutLogin
import com.seend.app.data.model.OutMensaje
import com.seend.app.data.model.OutPedirPerfil
import com.seend.app.data.model.OutPedirUsuarios
import com.seend.app.data.model.OutRegistro
import com.seend.app.data.model.OutValidarSesion
import com.seend.app.data.model.ServerMensaje
import com.seend.app.BuildConfig
import com.seend.app.data.remote.ApiClient
import com.seend.app.data.remote.ApiException
import com.seend.app.data.remote.ConnState
import com.seend.app.data.remote.MessageTransport
import com.seend.app.data.remote.WebSocketClient
import com.seend.app.data.remote.buildOkHttp
import com.seend.app.data.remote.s3.S3TransportClient
import com.seend.app.data.remote.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.IOException

/** Resultados one-shot de auth. */
sealed interface AuthResult {
    data class Success(val token: String, val nombre: String) : AuthResult
    data class Failure(val mensaje: String) : AuthResult
}

/** Resultados one-shot de guardar perfil. */
sealed interface ProfileResult {
    data class Success(val nombre: String) : ProfileResult
    data class Failure(val mensaje: String) : ProfileResult
}

/** Resultados one-shot de subir avatar. */
sealed interface AvatarResult {
    data class Success(val url: String) : AvatarResult
    data class Failure(val mensaje: String) : AvatarResult
}

sealed interface SesionValidation {
    data class Valida(val nombre: String) : SesionValidation
    object Invalida : SesionValidation
}

/**
 * SeendRepository: punto único de orquestación entre la red (WS y HTTP)
 * y la persistencia local (Room + DataStore). Expone [StateFlow]s para
 * que los ViewModels reaccionen sin acoplarse a la implementación.
 *
 * Se crea una sola vez en [com.seend.app.SeendApp] y se inyecta en los
 * ViewModels.
 */
class SeendRepository(
    private val ctx: Context,
    private val db: SeendDatabase,
    private val baseUrl: String,
    private val clientId: String,
) {
    companion object { private const val TAG = "SeendRepo" }

    private val okHttp = buildOkHttp()

    // Transporte de mensajería. Por defecto usa el proxy S3 (relay
    // todus_relay_vps) cuando BuildConfig.USE_S3_PROXY = true; si no,
    // WebSocket directo al servidor Go. Ambos implementan MessageTransport
    // y exponen los mismos eventos/estado, así el resto del repo no cambia.
    private val ws: MessageTransport =
        if (BuildConfig.USE_S3_PROXY) {
            S3TransportClient(
                httpClient = okHttp,
                baseUrl = BuildConfig.S3_BASE_URL,
                sharedSecretHex = BuildConfig.S3_SHARED_SECRET,
                clientId = clientId,
            )
        } else {
            WebSocketClient(baseUrl, okHttp)
        }
    private val api = ApiClient(baseUrl, okHttp)
    private val sessionStore = SessionStore(ctx)
    private val msgDao = db.mensajeDao()
    private val userDao = db.usuarioDao()
    private val sesionDao = db.sesionDao()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Estado mutable interno de la conexión; se hidrata desde ws.state.
    private val _connectionState = MutableStateFlow<ConnState>(ConnState.Disconnected)
    val connectionState: StateFlow<ConnState> = _connectionState.asStateFlow()

    private val _yo = MutableStateFlow<String?>(null)
    val yo: StateFlow<String?> = _yo.asStateFlow()

    // Eventos one-shot para la UI.
    private val _authEvents = MutableStateFlow<AuthResult?>(null)
    val authEvents: StateFlow<AuthResult?> = _authEvents.asStateFlow()

    private val _sesionValidation = MutableStateFlow<SesionValidation?>(null)
    val sesionValidation: StateFlow<SesionValidation?> = _sesionValidation.asStateFlow()

    private val _perfilPropio = MutableStateFlow<ProfileResult?>(null)
    val perfilPropio: StateFlow<ProfileResult?> = _perfilPropio.asStateFlow()

    private val _perfilAjeno = MutableStateFlow<Pair<String, InPerfil>?>(null)
    val perfilAjeno: StateFlow<Pair<String, InPerfil>?> = _perfilAjeno.asStateFlow()

    private val _escribiendo = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val escribiendoFlow: StateFlow<Map<String, Boolean>> = _escribiendo.asStateFlow()

    init {
        scope.launch {
            ws.state.collect { st -> _connectionState.value = st }
        }
        scope.launch {
            ws.events.collect { ev -> handleEvento(ev) }
        }
    }

    // ────────────────────────────────────────────────────────────────
    // Sesión persistida
    // ────────────────────────────────────────────────────────────────

    val session = sessionStore.session

    suspend fun currentToken(): String? = sessionStore.token.first()

    // ────────────────────────────────────────────────────────────────
    // Conexión
    // ────────────────────────────────────────────────────────────────

    fun connect() { ws.connect() }

    fun disconnect() { ws.disconnect() }

    // ────────────────────────────────────────────────────────────────
    // Auth
    // ────────────────────────────────────────────────────────────────

    suspend fun login(nombre: String, password: String) {
        ensureConnected()
        ws.send(OutLogin(nombre = nombre, password = password))
    }

    suspend fun register(nombre: String, password: String) {
        ensureConnected()
        ws.send(OutRegistro(nombre = nombre, password = password))
    }

    suspend fun validarSesion(token: String) {
        ensureConnected()
        ws.send(OutValidarSesion(token = token))
    }

    fun entrar(token: String) {
        ws.send(OutEntrar(token = token))
    }

    private suspend fun ensureConnected() {
        if (connectionState.value !is ConnState.Connected) {
            ws.connect()
            kotlinx.coroutines.delay(400)
        }
    }

    // ────────────────────────────────────────────────────────────────
    // Chats / mensajes
    // ────────────────────────────────────────────────────────────────

    fun pedirUsuarios() { ws.send(OutPedirUsuarios()) }

    fun abrirChat(otro: String) { ws.send(OutAbrirChat(otro = otro)) }

    fun ack(ids: List<String>) {
        if (ids.isNotEmpty()) ws.send(OutAck(ids = ids))
    }

    fun enviarMensaje(para: String, texto: String, cita: Cita? = null) {
        ws.send(OutMensaje(
            para = para, texto = texto,
            responder_a = cita != null,
            cita_autor = cita?.autor, cita_texto = cita?.texto,
        ))
    }

    fun avisarEscribiendo(para: String, activo: Boolean) {
        ws.send(OutEscribiendo(para = para, activo = activo))
    }

    fun pedirPerfil(quien: String) { ws.send(OutPedirPerfil(quien = quien)) }

    fun actualizarPerfil(nombre: String, bio: String, avatarUrl: String) {
        ws.send(OutActualizarPerfil(nombre = nombre, bio = bio, avatarUrl = avatarUrl))
    }

    // ────────────────────────────────────────────────────────────────
    // Subida de avatar (HTTP)
    // ────────────────────────────────────────────────────────────────

    suspend fun subirAvatar(token: String, contentType: String, bytes: ByteArray): AvatarResult {
        return try {
            val resp = api.prepareUpload(token = token, mime = contentType, bytes = bytes.size.toLong())
            val ok = api.putFile(resp.urlPut, contentType, bytes)
            if (ok) AvatarResult.Success(resp.urlPublica)
            else AvatarResult.Failure("PUT a S3 fallido")
        } catch (e: ApiException) {
            Log.w(TAG, "subirAvatar: ${e.message}")
            AvatarResult.Failure(e.reason)
        } catch (e: IOException) {
            Log.w(TAG, "subirAvatar: ${e.message}")
            AvatarResult.Failure("Error de red")
        }
    }

    // ────────────────────────────────────────────────────────────────
    // Flujos de datos para la UI
    // ────────────────────────────────────────────────────────────────

    fun usuariosFlow() = userDao.todos()
    fun usuarioPorNombreFlow(nombre: String) = userDao.porNombreFlow(nombre)

    fun mensajesFlow(yo: String, otro: String) =
        msgDao.conversacion(owner = yo, yo = yo, otro = otro)
            .map { rows -> rows.map { it.toDomain(yo) } }

    /** Lista de chats derivada localmente desde los mensajes guardados. */
    fun chatsFlow(yo: String) = combine(
        msgDao.ultimosMensajes(owner = yo, yo = yo),
        msgDao.noLeidosPorUsuario(owner = yo, yo = yo),
        userDao.todos(),
        escribiendoFlow,
    ) { ultimos, noLeidos, usuarios, escribiendo ->
        val noLeidosMap = noLeidos.associate { it.otro to it.cuenta }
        val avatarMap = usuarios.associate { it.nombre to it.avatar }
        ultimos.map { m ->
            val otro = if (m.de == yo) m.para else m.de
            ChatPreviewLocal(
                otro = otro,
                avatar = avatarMap[otro].orEmpty(),
                ultimoTexto = m.texto,
                ultimoDeYo = m.de == yo,
                ultimoEstado = EstadoMensaje.from(m.estado),
                ultimaHora = m.hora,
                noLeidos = noLeidosMap[otro] ?: 0,
                escribiendo = escribiendo[otro] == true,
            )
        }
    }

    suspend fun marcarLeidos(yo: String, otro: String) {
        msgDao.marcarLeidos(owner = yo, yo = yo, otro = otro)
    }

    suspend fun logout() {
        ws.disconnect()
        val yo = _yo.value
        sessionStore.clear()
        sesionDao.clear()
        if (yo != null) {
            msgDao.borrarTodo(owner = yo)
            userDao.borrarTodo()
        }
        _yo.value = null
    }

    suspend fun setYo(nombre: String) {
        _yo.value = nombre
        sessionStore.save(currentToken() ?: "", nombre)
    }

    // ────────────────────────────────────────────────────────────────
    // Manejo de eventos del servidor
    // ────────────────────────────────────────────────────────────────

    private fun handleEvento(ev: InEvento) {
        when (ev) {
            is InEvento.AuthOk -> scope.launch {
                sessionStore.save(ev.data.token, ev.data.nombre)
                sesionDao.upsert(SesionLocalEntity(token = ev.data.token, nombre = ev.data.nombre))
                _yo.value = ev.data.nombre
                ws.send(OutEntrar(token = ev.data.token))
                _authEvents.emit(AuthResult.Success(ev.data.token, ev.data.nombre))
            }
            is InEvento.SesionValida -> scope.launch {
                _yo.value = ev.data.nombre
                val t = currentToken()
                if (t != null) sessionStore.save(t, ev.data.nombre)
                _sesionValidation.emit(SesionValidation.Valida(ev.data.nombre))
            }
            is InEvento.SesionInvalida -> scope.launch {
                sessionStore.clear()
                _sesionValidation.emit(SesionValidation.Invalida)
            }
            is InEvento.Error -> scope.launch {
                _authEvents.emit(AuthResult.Failure(ev.data.texto.ifEmpty { "Error" }))
            }
            is InEvento.Pendientes -> scope.launch {
                val yo = _yo.value ?: return@launch
                val rows = ev.data.mensajes.map { it.toEntity(yo, leido = false) }
                msgDao.upsertAll(rows)
                val ids = rows.map { it.id }
                if (ids.isNotEmpty()) ws.send(OutAck(ids = ids))
            }
            is InEvento.Usuarios -> scope.launch {
                val rows = ev.data.usuarios.map { u ->
                    UsuarioEntity(
                        nombre = u.nombre, avatar = u.avatar, bio = "",
                        ultima = u.ultima.toLong(), enLinea = u.enLinea,
                    )
                }
                userDao.borrarTodo()
                userDao.upsertAll(rows)
            }
            is InEvento.Conversacion -> scope.launch {
                val yo = _yo.value ?: return@launch
                val rows = ev.data.mensajes.map { it.toEntity(yo, leido = false) }
                msgDao.upsertAll(rows)
                val ids = rows.filter { it.de == ev.data.otro }.map { it.id }
                if (ids.isNotEmpty()) {
                    ws.send(OutAck(ids = ids))
                    msgDao.marcarLeidos(owner = yo, yo = yo, otro = ev.data.otro)
                }
            }
            is InEvento.Mensaje -> scope.launch {
                val yo = _yo.value ?: return@launch
                val m = ev.data.mensaje
                val leido = ev.data.eco || m.de == yo
                msgDao.upsert(m.toEntity(yo, leido = leido))
            }
            is InEvento.Escribiendo -> scope.launch {
                _escribiendo.value = _escribiendo.value.toMutableMap().apply {
                    if (ev.data.activo) put(ev.data.de, true) else remove(ev.data.de)
                }
                if (ev.data.activo) {
                    kotlinx.coroutines.delay(2500)
                    _escribiendo.value = _escribiendo.value.toMutableMap().apply { remove(ev.data.de) }
                }
            }
            is InEvento.Presencia -> scope.launch {
                userDao.setPresencia(
                    nombre = ev.data.usuario,
                    enLinea = ev.data.enLinea,
                    ultima = ev.data.ultima.toLong(),
                )
            }
            is InEvento.Perfil -> scope.launch {
                userDao.upsert(UsuarioEntity(
                    nombre = ev.data.nombre, avatar = ev.data.avatar, bio = ev.data.bio,
                    ultima = ev.data.ultima.toLong(), enLinea = ev.data.enLinea,
                ))
                _perfilAjeno.emit(ev.data.nombre to ev.data)
            }
            is InEvento.PerfilActualizado -> scope.launch {
                sessionStore.save(currentToken() ?: "", ev.data.nombre)
                _yo.value = ev.data.nombre
                userDao.upsert(UsuarioEntity(
                    nombre = ev.data.nombre, avatar = ev.data.avatar, bio = ev.data.bio,
                    ultima = 0, enLinea = true,
                ))
                _perfilPropio.emit(ProfileResult.Success(ev.data.nombre))
            }
            is InEvento.PerfilActualizadoOtro -> scope.launch {
                userDao.setAvatar(ev.data.nombre, ev.data.avatar)
                userDao.setBio(ev.data.nombre, ev.data.bio)
            }
            is InEvento.Desconocido -> Log.w(TAG, "Evento no manejado: ${ev.tipo}")
        }
    }
}

// ────────────────────────────────────────────────────────────────
// Mappers
// ────────────────────────────────────────────────────────────────

private fun ServerMensaje.toEntity(yo: String, leido: Boolean): MensajeEntity =
    MensajeEntity(
        id = id, de = de, para = para, texto = texto, estado = estado,
        hora = hora, ts = ts.toLong(),
        citaAutor = cita?.autor, citaTexto = cita?.texto,
        leido = leido, owner = yo,
    )

private fun MensajeEntity.toDomain(yo: String): Mensaje = Mensaje(
    id = id, de = de, para = para, texto = texto,
    estado = EstadoMensaje.from(estado), hora = hora, ts = ts,
    cita = citaAutor?.let { Cita(it, citaTexto.orEmpty()) },
    propio = de == yo,
)
