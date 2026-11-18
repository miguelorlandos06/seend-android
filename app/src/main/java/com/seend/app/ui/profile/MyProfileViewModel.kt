package com.seend.app.ui.profile

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.seend.app.ServiceLocator
import com.seend.app.data.repo.AvatarResult
import com.seend.app.data.repo.ProfileResult
import com.seend.app.data.repo.SeendRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class MyProfileUiState(
    val yo: String = "",
    val nombre: String = "",
    val bio: String = "",
    val avatar: String = "",
    val guardando: Boolean = false,
    val subiendoAvatar: Boolean = false,
    val mensajeInfo: String? = null,
    val error: String? = null,
)

class MyProfileViewModel(
    app: Application,
) : AndroidViewModel(app) {

    private val repo: SeendRepository = ServiceLocator.repo

    private val _ui = MutableStateFlow(MyProfileUiState())
    val ui: StateFlow<MyProfileUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            repo.yo.collectLatest { yo ->
                _ui.value = _ui.value.copy(yo = yo.orEmpty())
                if (yo != null) {
                    repo.usuarioPorNombreFlow(yo).collectLatest { u ->
                        _ui.value = _ui.value.copy(
                            nombre = u?.nombre ?: yo,
                            bio = u?.bio.orEmpty(),
                            avatar = u?.avatar.orEmpty(),
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            repo.perfilPropio.collectLatest { r ->
                when (r) {
                    is ProfileResult.Success -> {
                        _ui.value = _ui.value.copy(
                            guardando = false,
                            mensajeInfo = "Perfil actualizado",
                            nombre = r.nombre,
                        )
                    }
                    is ProfileResult.Failure -> {
                        _ui.value = _ui.value.copy(
                            guardando = false,
                            error = r.mensaje,
                        )
                    }
                    null -> Unit
                }
            }
        }
    }

    fun setNombre(v: String) { _ui.value = _ui.value.copy(nombre = v) }
    fun setBio(v: String) { _ui.value = _ui.value.copy(bio = v) }
    fun dismissInfo() { _ui.value = _ui.value.copy(mensajeInfo = null, error = null) }

    fun guardar() {
        val s = _ui.value
        if (s.guardando) return
        if (s.nombre.isBlank()) {
            _ui.value = s.copy(error = "Nombre requerido")
            return
        }
        _ui.value = s.copy(guardando = true, error = null, mensajeInfo = null)
        repo.actualizarPerfil(
            nombre = s.nombre.trim(),
            bio = s.bio.trim(),
            avatarUrl = s.avatar,
        )
    }

    /**
     * Lee el Uri de una imagen, la comprime a JPEG bajo 300KB y la
     * sube a S3 via el servidor. Actualiza el perfil automáticamente.
     */
    fun subirAvatar(uri: Uri) {
        val token = runCatching {
            kotlinx.coroutines.runBlocking { repo.currentToken() }
        }.getOrNull()
        if (token.isNullOrBlank()) {
            _ui.value = _ui.value.copy(error = "Sesión no válida")
            return
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(subiendoAvatar = true, error = null)
            try {
                val bytes = withContext(Dispatchers.IO) {
                    comprimirImagen(getApplication(), uri, maxBytes = 300 * 1024)
                }
                if (bytes == null) {
                    _ui.value = _ui.value.copy(
                        subiendoAvatar = false,
                        error = "No se pudo leer la imagen",
                    )
                    return@launch
                }
                val res = repo.subirAvatar(token, "image/jpeg", bytes)
                when (res) {
                    is AvatarResult.Success -> {
                        // Guardar perfil con la nueva URL.
                        val s = _ui.value
                        _ui.value = s.copy(avatar = res.url, subiendoAvatar = false)
                        repo.actualizarPerfil(
                            nombre = s.nombre.trim(),
                            bio = s.bio.trim(),
                            avatarUrl = res.url,
                        )
                    }
                    is AvatarResult.Failure -> {
                        _ui.value = _ui.value.copy(
                            subiendoAvatar = false,
                            error = "Error al subir la foto: ${res.mensaje}",
                        )
                    }
                }
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(
                    subiendoAvatar = false,
                    error = "Error al subir la foto: ${e.message ?: "desconocido"}",
                )
            }
        }
    }

    fun logout() {
        viewModelScope.launch { repo.logout() }
    }
}

/**
 * Lee una imagen desde el [uri] y la comprime a JPEG bajo [maxBytes].
 * Devuelve null si no puede decodificar la imagen.
 */
private fun comprimirImagen(ctx: Context, uri: Uri, maxBytes: Int): ByteArray? {
    return try {
        val input = ctx.contentResolver.openInputStream(uri) ?: return null
        val bmp = BitmapFactory.decodeStream(input) ?: return null
        input.close()

        var quality = 90
        var out: ByteArrayOutputStream
        do {
            out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
            quality -= 10
        } while (out.size() > maxBytes && quality > 20)
        out.toByteArray()
    } catch (e: Exception) {
        null
    }
}
