package com.seend.app

import android.app.Application
import com.seend.app.data.local.SeendDatabase
import com.seend.app.data.repo.ClientIdStore
import com.seend.app.data.repo.SeendRepository

/**
 * Application class. Sirve como contenedor de dependencias (DI manual):
 * creamos una sola instancia de [SeendRepository] y la exponemos a los
 * ViewModels vía [ServiceLocator].
 *
 * No usamos Hilt/Koin para mantener la build simple y sin KSP extra.
 */
class SeendApp : Application() {

    lateinit var repo: SeendRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        val baseUrl = BuildConfig.SERVER_URL
        val db = SeendDatabase.get(this)
        // clientId estable por dispositivo para el transporte S3 (proxy).
        // Se genera una vez y se reutiliza; si USE_S3_PROXY=false no se usa.
        val clientId = ClientIdStore(this).getOrCreateBlocking()
        repo = SeendRepository(this, db, baseUrl, clientId)
        ServiceLocator.init(repo)
    }

    companion object {
        lateinit var instance: SeendApp
            private set
    }
}

/**
 * ServiceLocator: acceso global al [SeendRepository]. Los ViewModels lo
 * consultan desde una factory simple.
 */
object ServiceLocator {
    lateinit var repo: SeendRepository
        private set

    fun init(repo: SeendRepository) {
        this.repo = repo
    }
}
