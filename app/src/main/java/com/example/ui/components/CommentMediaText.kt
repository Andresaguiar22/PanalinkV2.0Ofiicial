package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import com.example.ui.theme.PanalinkPalette

/**
 * Renderiza el texto de un comentario que puede contener el marcador de GIF.
 * Si el texto es `[GIF] <url>` / `[Sticker] <url>` pinta la imagen animada,
 * si no pinta el texto normal.
 */
@Composable
fun CommentMediaText(
    text: String?,
    modifier: Modifier = Modifier,
    fallbackColor: Color = PanalinkPalette.textPrimary.copy(alpha = 0.9f)
) {
    val gif = parseCommentGif(text)
    if (gif != null) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
                .background(Color(0xFF111B21), RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = gif.url,
                contentDescription = gif.title ?: (if (gif.isGif) "GIF" else "Sticker"),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
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