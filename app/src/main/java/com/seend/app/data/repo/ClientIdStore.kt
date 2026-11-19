package com.seend.app.data.repo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.UUID

private val Context.clientIdStore by preferencesDataStore("seend_client_id")

/**
 * ClientIdStore: persiste el identificador estable del dispositivo para
 * el transporte S3. Es un UUID v4 sin guiones (32 hex) que se genera una
 * vez y se reutiliza en siguientes arranques, para que el servidor
 * maintenga el buzón de respuestas (_seend_res_{cid}_*) asociado a este
 * dispositivo entre sesiones.
 *
 * El acceso es síncrono ([getOrCreateBlocking]) porque el Repository lo
 * necesita al construirse (en onCreate de la Application). La lectura de
 * DataStore se hace con runBlocking una sola vez en arranque.
 */
class ClientIdStore(private val ctx: Context) {

    /** Devuelve el clientId persistido, o genera y persiste uno nuevo. */
    fun getOrCreateBlocking(): String {
        return runBlocking {
            val current = ctx.clientIdStore.data.first()[KEY_CLIENT_ID]
            if (current != null && current.length == 32) {
                current
            } else {
                val fresh = UUID.randomUUID().toString().replace("-", "")
                ctx.clientIdStore.edit { it[KEY_CLIENT_ID] = fresh }
                fresh
            }
        }
    }

    companion object {
        private val KEY_CLIENT_ID = stringPreferencesKey("client_id")
    }
}
