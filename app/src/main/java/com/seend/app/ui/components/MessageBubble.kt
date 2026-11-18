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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.seend.app.data.model.Cita
import com.seend.app.data.model.EstadoMensaje
import com.seend.app.data.model.Mensaje
import com.seend.app.ui.theme.BlueDark

/**
 * Burbuja de mensaje. Soporta cita (respuesta) y marca de estado
 * (enviado/entregado/visto). Alinea a la derecha si [propio].
 */
@Composable
fun MessageBubble(
    mensaje: Mensaje,
    modifier: Modifier = Modifier,
    onResponder: (Mensaje) -> Unit = {},
) {
    val isOwn = mensaje.propio
    val bg = if (isOwn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fg = if (isOwn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    val align = if (isOwn) Alignment.End else Alignment.Start
    val shape = if (isOwn) {
        RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)
    } else {
        RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = align,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .background(bg)
                .clickable { onResponder(mensaje) }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
                mensaje.cita?.let { c ->
                    CitaBlock(cita = c, fg = fg.copy(alpha = 0.85f))
                    Spacer(Modifier.size(4.dp))
                }
                if (mensaje.texto.isNotEmpty()) {
                    Text(
                        text = mensaje.texto,
                        color = fg,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        text = mensaje.hora,
                        color = fg.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    if (isOwn) {
                        Spacer(Modifier.width(4.dp))
                        EstadoCheck(estado = mensaje.estado, tint = fg.copy(alpha = 0.85f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CitaBlock(cita: Cita, fg: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(fg.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Column {
            Text(
                text = cita.autor,
                color = fg,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = cita.texto,
                color = fg.copy(alpha = 0.9f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun EstadoCheck(estado: EstadoMensaje, tint: Color) {
    when (estado) {
        EstadoMensaje.ENVIADO -> Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = "Enviado",
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
        EstadoMensaje.ENTREGADO, EstadoMensaje.VISTO -> Icon(
            imageVector = Icons.Filled.DoneAll,
            contentDescription = estado.name,
            tint = if (estado == EstadoMensaje.VISTO) BlueDark else tint,
            modifier = Modifier.size(16.dp),
        )
    }
}
