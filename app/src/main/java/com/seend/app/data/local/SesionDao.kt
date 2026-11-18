package com.seend.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SesionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(s: SesionLocalEntity)

    @Query("SELECT * FROM sesion_local WHERE id = 0")
    suspend fun get(): SesionLocalEntity?

    @Query("SELECT * FROM sesion_local WHERE id = 0")
    fun getFlow(): kotlinx.coroutines.flow.Flow<SesionLocalEntity?>

    @Query("DELETE FROM sesion_local WHERE id = 0")
    suspend fun clear()
}
