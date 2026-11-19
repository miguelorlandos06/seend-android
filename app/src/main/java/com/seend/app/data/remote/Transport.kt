package com.seend.app.data.remote

import com.seend.app.data.model.InAuthOk
import com.seend.app.data.model.InConversacion
import com.seend.app.data.model.InEscribiendo
import com.seend.app.data.model.InError
import com.seend.app.data.model.InEvento
import com.seend.app.data.model.InMensaje
import com.seend.app.data.model.InPendientes
import com.seend.app.data.model.InPerfil
import com.seend.app.data.model.InPerfilActualizado
import com.seend.app.data.model.InPerfilActualizadoOtro
import com.seend.app.data.model.InPresencia
import com.seend.app.data.model.InSesionInvalida
import com.seend.app.data.model.InSesionValida
import com.seend.app.data.model.InUsuarios
import com.seend.app.data.model.SobreTipo
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * MessageTransport: abstracción común al transporte WebSocket y al
 * transporte S3 (bridge). El [SeendRepository] programa contra esta
 * interfaz, así puede usar uno u otro según BuildConfig.USE_S3_PROXY
 * sin tocar la lógica de negocio.
 */
interface MessageTransport {
    /** Conecta / arranca el bucle de polling. Idempotente. */
    fun connect()

    /** Cierra limpiamente. */
    fun disconnect()

    /** Flujo de eventos entrantes ya decodificados. */
    val events: SharedFlow<InEvento>

    /** Estado de conexión expuesto a la UI. */
    val state: SharedFlow<ConnState>

    /** Envía un JSON ya serializado. false si no hay conexión / cola. */
    fun sendJson(jsonString: String): Boolean
}

/** Instancia Json compartida (mismos flags que el WebSocketClient original). */
val transportJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

/** Serializa y envía un objeto. Atajo usado por Repository/ViewModels. */
inline fun <reified T> MessageTransport.send(value: T): Boolean =
    sendJson(transportJson.encodeToString(value))

/**
 * Decodifica un mensaje JSON del servidor en un [InEvento], o null si el
 * parseo falla. Compartido por WebSocketClient y S3TransportClient para
 * que ambos interpreten el protocolo de forma idéntica.
 */
fun parseEvento(text: String): InEvento? {
    return try {
        val element = transportJson.parseToJsonElement(text)
        val tipo = transportJson.decodeFromJsonElement<SobreTipo>(element).tipo
        when (tipo) {
            "auth_ok" -> InEvento.AuthOk(transportJson.decodeFromJsonElement<InAuthOk>(element))
            "sesion_valida" -> InEvento.SesionValida(transportJson.decodeFromJsonElement<InSesionValida>(element))
            "sesion_invalida" -> InEvento.SesionInvalida
            "error" -> InEvento.Error(transportJson.decodeFromJsonElement<InError>(element))
            "pendientes" -> InEvento.Pendientes(transportJson.decodeFromJsonElement<InPendientes>(element))
            "usuarios" -> InEvento.Usuarios(transportJson.decodeFromJsonElement<InUsuarios>(element))
            "conversacion" -> InEvento.Conversacion(transportJson.decodeFromJsonElement<InConversacion>(element))
            "mensaje" -> InEvento.Mensaje(transportJson.decodeFromJsonElement<InMensaje>(element))
            "escribiendo" -> InEvento.Escribiendo(transportJson.decodeFromJsonElement<InEscribiendo>(element))
            "presencia" -> InEvento.Presencia(transportJson.decodeFromJsonElement<InPresencia>(element))
            "perfil" -> InEvento.Perfil(transportJson.decodeFromJsonElement<InPerfil>(element))
            "perfil_actualizado" -> InEvento.PerfilActualizado(transportJson.decodeFromJsonElement<InPerfilActualizado>(element))
            "perfil_actualizado_otro" -> InEvento.PerfilActualizadoOtro(transportJson.decodeFromJsonElement<InPerfilActualizadoOtro>(element))
            else -> {
                val raw: JsonElement? = if (element.toString().length <= 8192) element else null
                InEvento.Desconocido(tipo, raw)
            }
        }
    } catch (t: Throwable) {
        null
    }
}
