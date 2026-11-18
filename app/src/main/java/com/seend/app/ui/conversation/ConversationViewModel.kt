package com.seend.app.ui.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seend.app.ServiceLocator
import com.seend.app.data.model.Cita
import com.seend.app.data.model.Mensaje
import com.seend.app.data.repo.SeendRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class ConversationUiState(
    val yo: String = "",
    val otro: String = "",
    val otroAvatar: String = "",
    val otroEnLinea: Boolean = false,
    val otraUltimaConexion: Long = 0,
    val otroEscribiendo: Boolean = false,
    val mensajes: List<Mensaje> = emptyList(),
    val texto: String = "",
    val respondiendoA: Mensaje? = null,
)

class ConversationViewModel(
    private val otro: String,
) : ViewModel() {

    private val repo: SeendRepository = ServiceLocator.repo

    private val _ui = MutableStateFlow(ConversationUiState(otro = otro))
    val ui: StateFlow<ConversationUiState> = _ui.asStateFlow()

    init {
        // Pedir al servidor la conversación (recibir pendientes).
        repo.abrirChat(otro)

        viewModelScope.launch {
            repo.yo.collectLatest { yo ->
                _ui.value = _ui.value.copy(yo = yo.orEmpty())
                if (yo != null) {
                    // Marcar como leídos al entrar en la conversación.
                    repo.marcarLeidos(yo, otro)
                    // Observar mensajes locales.
                    repo.mensajesFlow(yo, otro).collectLatest { msgs ->
                        _ui.value = _ui.value.copy(mensajes = msgs)
                    }
                }
            }
        }
        // Observar al usuario "otro" (avatar / estado).
        viewModelScope.launch {
            repo.usuarioPorNombreFlow(otro).collectLatest { u ->
                _ui.value = _ui.value.copy(
                    otroAvatar = u?.avatar.orEmpty(),
                    otroEnLinea = u?.enLinea ?: false,
                    otraUltimaConexion = u?.ultima ?: 0,
                )
            }
        }
        // Observar escribiendo del "otro".
        viewModelScope.launch {
            repo.escribiendoFlow.collectLatest { m ->
                _ui.value = _ui.value.copy(otroEscribiendo = m[otro] == true)
            }
        }
    }

    fun setTexto(v: String) {
        _ui.value = _ui.value.copy(texto = v)
        // Throttle de escribiendo: lo manda el UI vía avisarEscribiendo para
        // no saturar el WS en cada keystroke.
    }

    fun setRespondiendo(m: Mensaje?) {
        _ui.value = _ui.value.copy(respondiendoA = m)
    }

    fun enviar() {
        val s = _ui.value
        val txt = s.texto.trim()
        if (txt.isEmpty() || s.yo.isEmpty()) return
        val cita = s.respondiendoA?.let {
            Cita(
                autor = if (it.de == s.yo) "Tú" else it.de,
                texto = it.texto.ifEmpty { "[Mensaje]" },
            )
        }
        repo.enviarMensaje(para = s.otro, texto = txt, cita = cita)
        _ui.value = s.copy(texto = "", respondiendoA = null)
        repo.avisarEscribiendo(para = s.otro, activo = false)
    }

    /**
     * Llamado por el UI cuando el usuario está tecleando. Hacemos un
     * throttle de ~1s para no mandar escribiendo=true en cada tecla.
     */
    private var ultimoEnvio = 0L
    fun onTextoCambiado(texto: String, yo: String) {
        setTexto(texto)
        val ahora = System.currentTimeMillis()
        val activo = texto.isNotEmpty()
        if (ultimoEnvio == 0L || ahora - ultimoEnvio >= 1000) {
            repo.avisarEscribiendo(para = s_otro, activo = activo)
            ultimoEnvio = ahora
        }
        if (texto.isEmpty()) {
            repo.avisarEscribiendo(para = s_otro, activo = false)
            ultimoEnvio = 0L
        }
    }

    private val s_otro: String get() = _ui.value.otro

    override fun onCleared() {
        // Al salir de la pantalla, dejar de avisar escribiendo.
        if (_ui.value.texto.isNotEmpty()) {
            repo.avisarEscribiendo(para = s_otro, activo = false)
        }
    }
}
