package com.seend.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seend.app.data.model.Usuario
import com.seend.app.util.formatearUltimaConexion

/**
 * Fila para la pantalla "Nuevo chat" (lista de usuarios).
 */
@Composable
fun UserListItem(
    usuario: Usuario,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    escribiendo: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(url = usuario.avatar, nombre = usuario.nombre, size = 42.dp)
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = usuario.nombre,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val estado = when {
                escribiendo -> "Escribiendo…" to MaterialTheme.colorScheme.primary
                usuario.enLinea -> "En línea" to MaterialTheme.colorScheme.primary
                else -> formatearUltimaConexion(usuario.ultimaConexion) to
                    MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                text = estado.first,
                style = MaterialTheme.typography.bodySmall,
                color = estado.second,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
