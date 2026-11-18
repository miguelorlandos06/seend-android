package com.seend.app.ui.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seend.app.ServiceLocator
import com.seend.app.data.local.UsuarioEntity
import com.seend.app.data.model.Usuario
import com.seend.app.data.repo.SeendRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class UsersUiState(
    val yo: String? = null,
    val usuarios: List<Usuario> = emptyList(),
    val escribiendo: Map<String, Boolean> = emptyMap(),
    val cargando: Boolean = true,
)

class UsersViewModel : ViewModel() {

    private val repo: SeendRepository = ServiceLocator.repo

    private val _ui = MutableStateFlow(UsersUiState())
    val ui: StateFlow<UsersUiState> = _ui.asStateFlow()

    init {
        // Pedimos la lista al servidor al abrir la pantalla.
        repo.pedirUsuarios()

        viewModelScope.launch {
            combine(
                repo.yo,
                repo.usuariosFlow(),
                repo.escribiendoFlow,
            ) { yo, usuarios, escribiendo ->
                UsersUiState(
                    yo = yo,
                    usuarios = usuarios.filter { it.nombre != yo }
                        .map { it.toDomain() },
                    escribiendo = escribiendo,
                    cargando = false,
                )
            }.collect { _ui.value = it }
        }
    }
}

private fun UsuarioEntity.toDomain() = Usuario(
    nombre = nombre,
    avatar = avatar,
    ultimaConexion = ultima,
    enLinea = enLinea,
)
