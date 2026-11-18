package com.seend.app.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seend.app.ServiceLocator
import com.seend.app.data.remote.ConnState
import com.seend.app.data.repo.SeendRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * ConnectionViewModel: observa el estado de conexión WS y la sesión
 * persistida. Lo comparten todas las pantallas porque es el "corazón"
 * de la app: si no hay sesión, vamos a /auth; si la hay, a /chat_list.
 *
 * También dispara la validación de sesión contra el servidor al
 * arrancar.
 */
class ConnectionViewModel : ViewModel() {

    private val repo: SeendRepository = ServiceLocator.repo

    val connectionState: StateFlow<ConnState> = repo.connectionState
    val session = repo.session

    private val _iniciando = MutableStateFlow(true)
    val iniciando: StateFlow<Boolean> = _iniciando.asStateFlow()

    init {
        // Arranca el WS y, si hay token guardado, valida la sesión.
        repo.connect()
        viewModelScope.launch {
            val s = repo.session.first()
            if (s != null) {
                repo.validarSesion(s.token)
            }
            _iniciando.value = false
        }
    }

    override fun onCleared() {
        // El repo se limpia con la app, no cerramos el WS aquí.
    }
}
