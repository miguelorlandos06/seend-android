package com.seend.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entidad de mensaje en Room. Refleja la fila del servidor Go pero
 * añade columnas locales:
 *  - [leido]: si ya se vio en el cliente (para el badge de no leídos).
 *  - [owner]: usuario local que tiene esa conversación. Permite
 *    multi-cuenta sin re-instalar (no se usa hoy pero queda listo).
 *
 * La tabla se llama `mensajes` para coincidir conceptualmente con el
 * servidor, aunque el contenido es el caché local de la APK.
 */
@Entity(
    tableName = "mensajes",
    indices = [Index("para"), Index("de"), Index("ts")]
)
data class MensajeEntity(
    @PrimaryKey val id: String,
    @ColumnInfo("de") val de: String,
    @ColumnInfo("para") val para: String,
    @ColumnInfo("texto") val texto: String,
    @ColumnInfo("estado") val estado: String,
    @ColumnInfo("hora") val hora: String,
    @ColumnInfo("ts") val ts: Long,
    @ColumnInfo("cita_autor") val citaAutor: String?,
    @ColumnInfo("cita_texto") val citaTexto: String?,
    @ColumnInfo("leido") val leido: Boolean,
    @ColumnInfo("owner") val owner: String,
)

/** Usuario cacheado para mostrar avatar/bio/última conexión. */
@Entity(tableName = "usuarios", indices = [Index("nombre", unique = true)])
data class UsuarioEntity(
    @PrimaryKey val nombre: String,
    @ColumnInfo("avatar") val avatar: String,
    @ColumnInfo("bio") val bio: String,
    @ColumnInfo("ultima") val ultima: Long,
    @ColumnInfo("en_linea") val enLinea: Boolean,
)

/** Sesión local (token + usuario). Se persiste en Room para sobrevivir
 *  reinicios; también se mantiene en DataStore para acceso rápido. */
@Entity(tableName = "sesion_local")
data class SesionLocalEntity(
    @PrimaryKey val id: Int = 0, // singleton
    val token: String,
    val nombre: String,
)
