package com.example.ui.components.chat.bubble

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest

@Composable
fun StickerBubbleContent(
    stickerUrl: String,
    modifier: Modifier = Modifier,
    fallbackUrl: String? = null
) {
    val scaleAnim = remember { Animatable(0.7f) }
    LaunchedEffect(stickerUrl) {
        scaleAnim.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }
    var isError by remember { mutableStateOf(false) }
    var usedFallback by remember { mutableStateOf(false) }

    // Fuente activa: la principal o (si falló) el fallback/thumbnail.
    val activeUrl = when {
        isError && fallbackUrl != null && !usedFallback && fallbackUrl != stickerUrl -> fallbackUrl
        else -> stickerUrl
    }
    // Determina si la fuente es un archivo local existente o una URL remota.
    val isLocalFile = !activeUrl.startsWith("http://") && !activeUrl.startsWith("https://")
    val loadable = remember(activeUrl, isLocalFile) {
        if (isLocalFile) java.io.File(activeUrl).exists() else true
    }

    Box(
        modifier = modifier
            .size(130.dp)
            .graphicsLayer {
                scaleX = scaleAnim.value
                scaleY = scaleAnim.value
            },
        contentAlignment = Alignment.Center
    ) {
        if (isError && (fallbackUrl == null || usedFallback || fallbackUrl == stickerUrl) || !loadable) {
            // Fallback elegante: nunca dejar un espacio vacío
            if (!loadable) {
                // La fuente local no existe y no hay alternativa: se intentó pero
                // no hay imagen — usar un icono discreto en vez de romper.
                Icon(
                    imageVector = Icons.Default.BrokenImage,
                    contentDescription = null,
                    tint = Color(0xFF8696A0),
                    modifier = Modifier.size(36.dp)
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("😢", fontSize = 28.sp)
                    Text("Sticker no disponible", color = Color(0xFF8696A0), fontSize = 9.sp)
                }
            }
        } else {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(
                        if (isLocalFile) java.io.File(activeUrl)
                        else activeUrl
                    )
                    .decoderFactory(if (Build.VERSION.SDK_INT >= 28) ImageDecoderDecoder.Factory() else GifDecoder.Factory())
                    .crossfade(true)
                    .listener(
                        onError = { _, _ ->
                            if (!usedFallback && fallbackUrl != null && fallbackUrl != stickerUrl) {
                                usedFallback = true
                                isError = false
                            } else {
                                isError = true
                            }
                        }
                    )
                    .build(),
                contentDescription = "Sticker",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}
