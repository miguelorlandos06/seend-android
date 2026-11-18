package com.seend.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MensajeDao {

    /** Inserta o reemplaza un mensaje (mismo id → reemplaza). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(m: MensajeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(m: List<MensajeEntity>)

    /** Mensajes de una conversación, ordenados por ts asc. */
    @Query("SELECT * FROM mensajes WHERE owner = :owner AND ((de = :yo AND para = :otro) OR (de = :otro AND para = :yo)) ORDER BY ts ASC")
    fun conversacion(owner: String, yo: String, otro: String): Flow<List<MensajeEntity>>

    /** Último mensaje de cada "otro" para construir la lista de chats. */
    @Query("""
        SELECT m.* FROM mensajes m
        INNER JOIN (
            SELECT
                CASE WHEN de = :yo THEN para ELSE de END AS otro,
                MAX(ts) AS max_ts
            FROM mensajes
            WHERE owner = :yo AND (de = :yo OR para = :yo)
            GROUP BY otro
        ) t
        ON (
            (m.de = :yo AND m.para = t.otro) OR
            (m.para = :yo AND m.de = t.otro)
        ) AND m.ts = t.max_ts
        WHERE m.owner = :yo
    """)
    fun ultimosMensajes(owner: String, yo: String): Flow<List<MensajeEntity>>

    /** Cuenta no leídos por conversación (entrantes no leídos). */
    @Query("""
        SELECT
            de AS otro,
            COUNT(*) AS cuenta
        FROM mensajes
        WHERE owner = :owner AND para = :yo AND leido = 0
        GROUP BY de
    """)
    fun noLeidosPorUsuario(owner: String, yo: String): Flow<List<NoLeidosRow>>

    /** Marca como leídos todos los entrantes de [otro]. */
    @Query("UPDATE mensajes SET leido = 1 WHERE owner = :owner AND de = :otro AND para = :yo AND leido = 0")
    suspend fun marcarLeidos(owner: String, yo: String, otro: String): Int

    /** Borra todos los mensajes de una conversación. */
    @Query("DELETE FROM mensajes WHERE owner = :owner AND ((de = :yo AND para = :otro) OR (de = :otro AND para = :yo))")
    suspend fun borrarConversacion(owner: String, yo: String, otro: String): Int

    @Query("DELETE FROM mensajes WHERE owner = :owner")
    suspend fun borrarTodo(owner: String): Int

    /** Cambia el estado de un mensaje (enviado/entregado/visto). */
    @Query("UPDATE mensajes SET estado = :estado WHERE id = :id AND owner = :owner")
    suspend fun actualizarEstado(owner: String, id: String, estado: String)
}

data class NoLeidosRow(
    val otro: String,
    val cuenta: Int,
)