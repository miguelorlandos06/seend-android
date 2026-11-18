package com.seend.app.data.repo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionStore by preferencesDataStore("seend_session")

/**
 * SessionStore: persistencia ligera (DataStore) del token y nombre del
 * usuario logueado. Se accede desde Auth y desde el Repository para
 * saber si hay que mandar `entrar` al reconectar.
 *
 * También se mantiene una copia en Room (SesionLocalEntity) como
 * respaldo para queries. Para evitar sincronizar dos fuentes, el
 * DataStore es la fuente canónica y Room sólo se hidrata al arrancar.
 */
class SessionStore(private val ctx: Context) {

    val token: Flow<String?> = ctx.sessionStore.data.map { it[KEY_TOKEN] }
    val nombre: Flow<String?> = ctx.sessionStore.data.map { it[KEY_NOMBRE] }
    val session: Flow<Session?> = ctx.sessionStore.data.map {
        val t = it[KEY_TOKEN]
        val n = it[KEY_NOMBRE]
        if (t != null && n != null) Session(t, n) else null
    }

    suspend fun save(token: String, nombre: String) {
        ctx.sessionStore.edit {
            it[KEY_TOKEN] = token
            it[KEY_NOMBRE] = nombre
        }
    }

    suspend fun clear() {
        ctx.sessionStore.edit { it.clear() }
    }

    companion object {
        private val KEY_TOKEN = stringPreferencesKey("token")
        private val KEY_NOMBRE = stringPreferencesKey("nombre")
    }
}

data class Session(val token: String, val nombre: String)
