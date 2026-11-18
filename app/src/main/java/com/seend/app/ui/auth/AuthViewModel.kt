package com.seend.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seend.app.ServiceLocator
import com.seend.app.data.repo.AuthResult
import com.seend.app.data.repo.SeendRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Estado de UI de la pantalla de auth.
 */
data class AuthUiState(
    val modo: AuthModo = AuthModo.LOGIN,
    val nombre: String = "",
    val password: String = "",
    val confirmar: String = "",
    val enviando: Boolean = false,
    val error: String? = null,
)

enum class AuthModo(val label: String) {
    LOGIN("Iniciar sesión"),
    REGISTRO("Crear cuenta"),
}

class AuthViewModel : ViewModel() {

    private val repo: SeendRepository = ServiceLocator.repo

    private val _ui = MutableStateFlow(AuthUiState())
    val ui: StateFlow<AuthUiState> = _ui.asStateFlow()

    init {
        // Escuchamos los resultados one-shot del repo (auth_ok / error).
        viewModelScope.launch {
            repo.authEvents.collect { result ->
                when (result) {
                    is AuthResult.Success -> {
                        _ui.value = _ui.value.copy(enviando = false, error = null)
                        // El NavHost reaccionará a la sesión nueva; aquí
                        // sólo limpiamos el estado.
                    }
                    is AuthResult.Failure -> {
                        _ui.value = _ui.value.copy(
                            enviando = false,
                            error = result.mensaje,
                        )
                    }
                    null -> Unit
                }
            }
        }
    }

    fun setModo(modo: AuthModo) {
        _ui.value = _ui.value.copy(modo = modo, error = null)
    }

    fun setNombre(v: String) { _ui.value = _ui.value.copy(nombre = v, error = null) }
    fun setPassword(v: String) { _ui.value = _ui.value.copy(password = v, error = null) }
    fun setConfirmar(v: String) { _ui.value = _ui.value.copy(confirmar = v, error = null) }

    /** Valida y envía el formulario. Devuelve true si la validación pasó. */
    fun enviar(): Boolean {
        val s = _ui.value
        if (s.enviando) return false
        when {
            s.nombre.isBlank() || s.password.isBlank() -> {
                _ui.value = s.copy(error = "Completa todos los campos")
                return false
            }
            s.password.length < 4 -> {
                _ui.value = s.copy(error = "La contraseña debe tener al menos 4 caracteres")
                return false
            }
            s.modo == AuthModo.REGISTRO && s.password != s.confirmar -> {
                _ui.value = s.copy(error = "Las contraseñas no coinciden")
                return false
            }
        }
        _ui.value = s.copy(enviando = true, error = null)
        viewModelScope.launch {
            try {
                if (s.modo == AuthModo.LOGIN) {
                    repo.login(s.nombre.trim(), s.password)
                } else {
                    repo.register(s.nombre.trim(), s.password)
                }
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(
                    enviando = false,
                    error = "No se pudo conectar al servidor",
                )
            }
        }
        return true
    }
}
