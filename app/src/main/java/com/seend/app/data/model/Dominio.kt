package com.seend.app.data.model

/**
 * Modelos de dominio (lo que ve la UI), separados del protocolo WS
 * para no acoplar Compose a la serialización. La capa Repository hace
 * el mapeo entre ambos.
 */

/** Estado de un mensaje individual, reflejando el servidor Go. */
enum class EstadoMensaje(val valor: String) {
    ENVIADO("enviado"),
    ENTREGADO("entregado"),
    VISTO("visto");

    companion object {
        fun from(s: String?): EstadoMensaje =
            entries.firstOrNull { it.valor == s } ?: ENVIADO
    }
}

/** Datos de un usuario (para listas y perfiles). */
data class Usuario(
    val nombre: String,
    val avatar: String,
    val ultimaConexion: Long, // epoch segundos
    val enLinea: Boolean,
)

/** Cita (respuesta a un mensaje). */
data class Cita(
    val autor: String,
    val texto: String,
)

/** Mensaje de chat (dominio). */
data class Mensaje(
    val id: String,
    val de: String,
    val para: String,
    val texto: String,
    val estado: EstadoMensaje,
    val hora: String,
    val ts: Long, // epoch segundos
    val cita: Cita?,
    val propio: Boolean = false,
)

/** Entrada de la lista de chats (se construye localmente desde Room). */
data class ChatPreview(
    val otro: String,
    val avatar: String,
    val ultimoTexto: String,
    val ultimoDeYo: Boolean,
    val ultimoEstado: EstadoMensaje,
    val ultimaHora: String,
    val noLeidos: Int,
    val escribiendo: Boolean = false,
)

/** Variante interna del repositorio (mismos campos). */
data class ChatPreviewLocal(
    val otro: String,
    val avatar: String,
    val ultimoTexto: String,
    val ultimoDeYo: Boolean,
    val ultimoEstado: EstadoMensaje,
    val ultimaHora: String,
    val noLeidos: Int,
    val escribiendo: Boolean = false,
)
