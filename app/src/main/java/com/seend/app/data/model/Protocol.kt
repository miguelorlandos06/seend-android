package com.seend.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Sobres del protocolo WebSocket. El servidor discrimina por [tipo].
 *
 * El contrato es 1:1 con el servidor Go (`internal/ws/handler.go`):
 * todos los campos usan los mismos nombres JSON para no romper la
 * compatibilidad con la base de usuarios existente.
 */

// ────────────────────────────────────────────────────────────────
// Mensajes salientes (cliente → servidor)
// ────────────────────────────────────────────────────────────────

@Serializable
data class OutRegistro(
    val tipo: String = "registro",
    val nombre: String,
    val password: String,
)

@Serializable
data class OutLogin(
    val tipo: String = "login",
    val nombre: String,
    val password: String,
)

@Serializable
data class OutValidarSesion(
    val tipo: String = "validar_sesion",
    val token: String,
)

@Serializable
data class OutEntrar(
    val tipo: String = "entrar",
    val token: String,
)

@Serializable
data class OutPedirUsuarios(
    val tipo: String = "pedir_usuarios",
)

@Serializable
data class OutAbrirChat(
    val tipo: String = "abrir_chat",
    val otro: String,
)

@Serializable
data class OutAck(
    val tipo: String = "ack",
    val ids: List<String>,
)

@Serializable
data class OutMensaje(
    val tipo: String = "mensaje",
    val para: String,
    val texto: String,
    val responder_a: Boolean = false,
    val cita_autor: String? = null,
    val cita_texto: String? = null,
)

@Serializable
data class OutEscribiendo(
    val tipo: String = "escribiendo",
    val para: String,
    val activo: Boolean,
)

@Serializable
data class OutActualizarPerfil(
    val tipo: String = "actualizar_perfil",
    val nombre: String,
    val bio: String,
    @SerialName("avatar_url") val avatarUrl: String,
)

@Serializable
data class OutPedirPerfil(
    val tipo: String = "pedir_perfil",
    val quien: String,
)

// ────────────────────────────────────────────────────────────────
// Mensajes entrantes (servidor → cliente)
// ────────────────────────────────────────────────────────────────

@Serializable
data class InAuthOk(
    val tipo: String = "auth_ok",
    val token: String,
    val nombre: String,
)

@Serializable
data class InSesionValida(
    val tipo: String = "sesion_valida",
    val nombre: String,
)

@Serializable
data class InSesionInvalida(
    val tipo: String = "sesion_invalida",
)

@Serializable
data class InError(
    val tipo: String = "error",
    val texto: String = "",
)

@Serializable
data class InPendientes(
    val tipo: String = "pendientes",
    val mensajes: List<ServerMensaje> = emptyList(),
)

@Serializable
data class InUsuarios(
    val tipo: String = "usuarios",
    val usuarios: List<ServerUsuario> = emptyList(),
    @SerialName("en_linea") val enLinea: Int = 0,
)

@Serializable
data class InConversacion(
    val tipo: String = "conversacion",
    val otro: String,
    val mensajes: List<ServerMensaje> = emptyList(),
    @SerialName("en_linea") val enLinea: Boolean = false,
    val ultima: Double = 0.0,
)

@Serializable
data class InMensaje(
    val tipo: String = "mensaje",
    val mensaje: ServerMensaje,
    val eco: Boolean = false,
)

@Serializable
data class InEscribiendo(
    val tipo: String = "escribiendo",
    val de: String,
    val activo: Boolean,
)

@Serializable
data class InPresencia(
    val tipo: String = "presencia",
    val usuario: String,
    @SerialName("en_linea") val enLinea: Boolean,
    val ultima: Double = 0.0,
)

@Serializable
data class InPerfil(
    val tipo: String = "perfil",
    val nombre: String,
    val bio: String = "",
    val avatar: String = "",
    @SerialName("en_linea") val enLinea: Boolean = false,
    val ultima: Double = 0.0,
)

@Serializable
data class InPerfilActualizado(
    val tipo: String = "perfil_actualizado",
    val nombre: String,
    val bio: String = "",
    val avatar: String = "",
)

@Serializable
data class InPerfilActualizadoOtro(
    val tipo: String = "perfil_actualizado_otro",
    val nombre: String,
    val bio: String = "",
    val avatar: String = "",
)

// ────────────────────────────────────────────────────────────────
// Tipos compartidos dentro de mensajes
// ────────────────────────────────────────────────────────────────

@Serializable
data class ServerMensaje(
    val id: String,
    val de: String,
    val para: String,
    val texto: String = "",
    val estado: String = "enviado",
    val hora: String = "",
    val ts: Double = 0.0,
    val cita: ServerCita? = null,
)

@Serializable
data class ServerCita(
    val autor: String,
    val texto: String,
)

@Serializable
data class ServerUsuario(
    val nombre: String,
    val avatar: String = "",
    val ultima: Double = 0.0,
    @SerialName("en_linea") val enLinea: Boolean = false,
)

// ────────────────────────────────────────────────────────────────
// HTTP /upload/prepare
// ────────────────────────────────────────────────────────────────

@Serializable
data class UploadPrepareRequest(
    val token: String,
    @SerialName("tipo_archivo") val tipoArchivo: String = "avatar",
    val bytes: Long,
    val mime: String = "image/jpeg",
)

@Serializable
data class UploadPrepareResponse(
    val key: String = "",
    @SerialName("url_put") val urlPut: String,
    @SerialName("url_publica") val urlPublica: String,
)

@Serializable
data class HttpError(
    val error: String,
)

/**
 * Sobre genérico para decodificar el campo [tipo] y luego derivar.
 */
@Serializable
data class SobreTipo(
    val tipo: String,
)

/**
 * Resultado de parsear un mensaje entrante. Se usa un sealed class para
 * que el repositorio pueda hacer un `when` exhaustivo.
 */
sealed interface InEvento {
    data class AuthOk(val data: InAuthOk) : InEvento
    data class SesionValida(val data: InSesionValida) : InEvento
    object SesionInvalida : InEvento
    data class Error(val data: InError) : InEvento
    data class Pendientes(val data: InPendientes) : InEvento
    data class Usuarios(val data: InUsuarios) : InEvento
    data class Conversacion(val data: InConversacion) : InEvento
    data class Mensaje(val data: InMensaje) : InEvento
    data class Escribiendo(val data: InEscribiendo) : InEvento
    data class Presencia(val data: InPresencia) : InEvento
    data class Perfil(val data: InPerfil) : InEvento
    data class PerfilActualizado(val data: InPerfilActualizado) : InEvento
    data class PerfilActualizadoOtro(val data: InPerfilActualizadoOtro) : InEvento
    data class Desconocido(val tipo: String, val payload: JsonElement?) : InEvento
}
