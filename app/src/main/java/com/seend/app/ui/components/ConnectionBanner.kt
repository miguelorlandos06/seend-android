package com.seend.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.seend.app.data.remote.ConnState
import com.seend.app.ui.theme.Danger

/**
 * Banner fino que aparece bajo la top bar cuando hay problemas de
 * conexión. No ocupa espacio cuando está conectado.
 */
@Composable
fun ConnectionBanner(
    state: ConnState,
    modifier: Modifier = Modifier,
) {
    val (texto, color) = when (state) {
        ConnState.Connected -> null to null
        ConnState.Connecting -> "Conectando…" to MaterialTheme.colorScheme.primary
        is ConnState.Reconnecting -> "Reconectando… (intento ${state.intento})" to MaterialTheme.colorScheme.primary
        ConnState.Disconnected -> "Desconectado" to Danger
    }
    AnimatedVisibility(
        visible = texto != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        if (texto != null && color != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color)
                    .padding(vertical = 4.dp, horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = texto,
                    color = androidx.compose.ui.graphics.Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
