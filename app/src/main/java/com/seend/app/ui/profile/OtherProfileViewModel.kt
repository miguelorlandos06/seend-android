package com.seend.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seend.app.ServiceLocator
import com.seend.app.data.repo.SeendRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class OtherProfileUiState(
    val nombre: String = "",
    val bio: String = "",
    val avatar: String = "",
    val enLinea: Boolean = false,
    val ultimaConexion: Long = 0,
)

class OtherProfileViewModel(
    private val quien: String,
) : ViewModel() {

    private val repo: SeendRepository = ServiceLocator.repo

    private val _ui = MutableStateFlow(OtherProfileUiState(nombre = quien))
    val ui: StateFlow<OtherProfileUiState> = _ui.asStateFlow()

    init {
        // Pedir al servidor datos frescos del perfil.
        repo.pedirPerfil(quien)
        // Observar caché local (se actualiza con la respuesta).
        viewModelScope.launch {
            repo.usuarioPorNombreFlow(quien).collectLatest { u ->
                if (u != null) {
                    _ui.value = OtherProfileUiState(
                        nombre = u.nombre,
                        bio = u.bio,
                        avatar = u.avatar,
                        enLinea = u.enLinea,
                        ultimaConexion = u.ultima,
                    )
                }
            }
        }
    }
}
