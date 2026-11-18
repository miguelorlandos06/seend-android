package com.seend.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seend.app.data.model.ChatPreviewLocal
import com.seend.app.data.model.EstadoMensaje
import com.seend.app.ui.theme.BlueDark

/**
 * Fila de la lista de chats (estilo WhatsApp/Telegram): avatar, nombre,
 * preview del último mensaje, hora y badge de no leídos.
 */
@Composable
fun ChatListItem(
    chat: ChatPreviewLocal,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    activo: Boolean = false,
) {
    val bg = if (activo) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surface

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(url = chat.avatar, nombre = chat.otro, size = 46.dp)

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // Fila 1: nombre + hora
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = chat.otro,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = chat.ultimaHora,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Fila 2: preview + check/badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                val previewText = if (chat.escribiendo) {
                    "Escribiendo…"
                } else {
                    (if (chat.ultimoDeYo) "Tú: " else "") + chat.ultimoTexto
                }
                val previewColor = if (chat.escribiendo) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                if (chat.ultimoDeYo && !chat.escribiendo) {
                    EstadoCheck(estado = chat.ultimoEstado, tint = previewColor)
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = previewText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = previewColor,
                    fontStyle = if (chat.escribiendo) FontStyle.Italic else FontStyle.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (chat.noLeidos > 0) {
                    Spacer(Modifier.width(6.dp))
                    BadgeConteo(chat.noLeidos)
                }
            }
        }
    }
}

@Composable
private fun EstadoCheck(estado: EstadoMensaje, tint: androidx.compose.ui.graphics.Color) {
    when (estado) {
        EstadoMensaje.ENVIADO -> Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
        EstadoMensaje.ENTREGADO, EstadoMensaje.VISTO -> Icon(
            imageVector = Icons.Filled.DoneAll,
            contentDescription = null,
            tint = if (estado == EstadoMensaje.VISTO) BlueDark else tint,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun BadgeConteo(n: Int) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (n > 99) "99+" else n.toString(),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}
