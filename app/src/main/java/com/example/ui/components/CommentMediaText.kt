package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.material3.Text
import com.example.ui.settings.ios.IosSettingsColors

/**
 * Renderiza el texto de un comentario que puede contener el marcador de GIF.
 * Si el texto es `[GIF] <url>` / `[Sticker] <url>` pinta la imagen animada,
 * si no pinta el texto normal.
 */
@Composable
fun CommentMediaText(
    text: String?,
    modifier: Modifier = Modifier,
    fallbackColor: Color = IosSettingsColors.label,
    /** true = versión compacta para el chat de comentarios en directo (GIFs más pequeños). */
    compact: Boolean = false
) {
    val gif = parseCommentGif(text)
    if (gif != null) {
        val gifSize = if (compact) 64.dp else 130.dp
        Box(
            modifier = modifier
                .size(gifSize)
                .padding(top = 2.dp)
                .background(IosSettingsColors.cellElevated, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.CenterStart
        ) {
            AsyncImage(
                model = gif.url,
                contentDescription = gif.title ?: (if (gif.isGif) "GIF" else "Sticker"),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                contentScale = ContentScale.Fit
            )
        }
    } else {
        Text(
            text = text ?: "",
            color = fallbackColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            modifier = modifier
        )
    }
}