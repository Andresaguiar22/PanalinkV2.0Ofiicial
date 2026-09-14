package com.example.feature.chat.ui.background

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

/**
 * Fondo del área de mensajes. Dibuja el spec seleccionado: gradiente premium,
 * sólido, imagen remota o imagen local de la galería.
 */
@Composable
fun ChatWallpaperBackground(
    spec: ChatWallpaperSpec,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (spec) {
            is ChatWallpaperSpec.Gradient -> {
                val base = Brush.linearGradient(
                    colors = listOf(Color(spec.start), Color(spec.end)),
                    start = Offset.Zero,
                    end = Offset.Infinite
                )
                Box(Modifier.fillMaxSize().background(base))
                spec.depthHue?.let { hue ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(Color(hue).copy(alpha = 0.35f), Color.Transparent),
                                    radius = 1200f
                                )
                            )
                    )
                }
            }
            is ChatWallpaperSpec.Solid -> {
                Box(Modifier.fillMaxSize().background(Color(spec.color)))
            }
            is ChatWallpaperSpec.Remote -> {
                AsyncImage(
                    model = spec.url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
            }
            is ChatWallpaperSpec.Custom -> {
                AsyncImage(
                    model = spec.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
            }
        }
        content()
    }
}