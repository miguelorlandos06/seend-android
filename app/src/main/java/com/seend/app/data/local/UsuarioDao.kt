package com.seend.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UsuarioDao {

    @Upsert
    suspend fun upsert(u: UsuarioEntity)

    @Upsert
    suspend fun upsertAll(us: List<UsuarioEntity>)

    @Query("SELECT * FROM usuarios")
    fun todos(): Flow<List<UsuarioEntity>>

    @Query("SELECT * FROM usuarios WHERE nombre = :nombre")
    suspend fun porNombre(nombre: String): UsuarioEntity?

    @Query("SELECT * FROM usuarios WHERE nombre = :nombre")
    fun porNombreFlow(nombre: String): Flow<UsuarioEntity?>

    /** Resetea la presencia online (se recalcula al llegar "usuarios"). */
    @Query("UPDATE usuarios SET en_linea = 0")
    suspend fun resetOnline()

    @Query("UPDATE usuarios SET en_linea = 1 WHERE nombre IN (:nombres)")
    suspend fun marcarOnline(nombres: List<String>)

    @Query("UPDATE usuarios SET ultima = :ts WHERE nombre = :nombre")
    suspend fun setUltima(nombre: String, ts: Long)

    @Query("UPDATE usuarios SET en_linea = :enLinea, ultima = COALESCE(:ultima, ultima) WHERE nombre = :nombre")
    suspend fun setPresencia(nombre: String, enLinea: Boolean, ultima: Long?)

    @Query("UPDATE usuarios SET avatar = :avatar WHERE nombre = :nombre")
    suspend fun setAvatar(nombre: String, avatar: String)

    @Query("UPDATE usuarios SET bio = :bio WHERE nombre = :nombre")
    suspend fun setBio(nombre: String, bio: String)

    @Query("DELETE FROM usuarios")
    suspend fun borrarTodo()
}
