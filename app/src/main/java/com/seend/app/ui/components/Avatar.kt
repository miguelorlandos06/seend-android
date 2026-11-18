package com.seend.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.seend.app.ui.theme.BlueDark
import com.seend.app.ui.theme.BlueLight

/**
 * Avatar: muestra la imagen si [url] no está vacía, si no, la inicial
 * del nombre. Compatible con las URLs públicas del S3 del servidor Go.
 */
@Composable
fun Avatar(
    url: String?,
    nombre: String,
    size: Dp = 46.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(BlueLight),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .crossfade(true)
                    .build(),
                contentDescription = "Avatar de $nombre",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        } else {
            val inicial = nombre.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()
                ?: '?'
            Text(
                text = inicial.toString(),
                color = BlueDark,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * 0.4f).sp,
            )
        }
    }
}

/**
 * Pequeño punto de presencia: bolita verde si [enLinea], oculto si no.
 */
@Composable
fun PresenceDot(
    enLinea: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    if (!enLinea) return
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}
