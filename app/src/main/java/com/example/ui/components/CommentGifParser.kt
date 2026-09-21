package com.example.ui.components

import com.example.data.model.StickerResult

private val GIF_COMMENT_REGEX = Regex(
    """\[(GIF|Sticker)\]\s*(\S+)""",
    RegexOption.IGNORE_CASE
)

/** Marcador que viaja en el texto del comentario para representar un GIF/sticker. */
const val GIF_COMMENT_PREFIX = "[GIF]"
const val STICKER_COMMENT_PREFIX = "[Sticker]"

/**
 * Convierte el texto de un comentario espectro marcador GIF/sticker en el modelo que
 * la UI debe renderizar. Devuelve null si el texto es un comentario normal.
 */
fun parseCommentGif(text: String?): StickerResult? {
    val trimmed = text?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    val match = GIF_COMMENT_REGEX.find(trimmed) ?: return null
    val kind = match.groupValues[1]
    val url = match.groupValues[2]
    if (url.isBlank()) return null
    val isGif = kind.equals("GIF", ignoreCase = true)
    return StickerResult(
        id = "comment_gif_${kotlin.math.abs(url.hashCode())}",
        title = null,
        url = url,
        preview = url,
        width = null,
        height = null,
        isGif = isGif
    )
}

/** Construye el texto del comentario para un GIF/sticker elegido. */
fun buildCommentGifText(sticker: StickerResult): String {
    val kind = if (sticker.isGif) GIF_COMMENT_PREFIX else STICKER_COMMENT_PREFIX
    return "$kind ${sticker.url}"
}