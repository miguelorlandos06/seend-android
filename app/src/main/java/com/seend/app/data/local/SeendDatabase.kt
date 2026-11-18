package com.seend.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base de datos local (caché de la APK). El servidor sigue siendo la
 * fuente de verdad; esta BD sólo permite que la app sea responsive y
 * construya la lista de chats a partir de los mensajes recibidos.
 *
 * Schema versioning: por ahora version = 1. Si en el futuro se añaden
 * columnas, sube el número y agrega una Migration (no uses
 * fallbackToDestructiveMigration en producción).
 */
@Database(
    entities = [
        MensajeEntity::class,
        UsuarioEntity::class,
        SesionLocalEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class SeendDatabase : RoomDatabase() {
    abstract fun mensajeDao(): MensajeDao
    abstract fun usuarioDao(): UsuarioDao
    abstract fun sesionDao(): SesionDao

    companion object {
        @Volatile private var INSTANCE: SeendDatabase? = null

        fun get(ctx: Context): SeendDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    ctx.applicationContext,
                    SeendDatabase::class.java,
                    "seend.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
