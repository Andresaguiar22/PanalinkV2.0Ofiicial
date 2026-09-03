package com.example.features.stickers.studio

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import com.example.data.model.StickerResult
import com.example.features.stickers.editor.StickerCreationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Panalink's own sticker identity: a default pack generated on-device the first
 * time it is needed, stored in the app sticker memory (filesDir/stickers/default).
 * Works fully offline. When a default sticker is sent, it is uploaded to the CDN
 * once (cached) so every contact receives a normal remote sticker.
 */
object PanalinkDefaultStickers {
    private const val PREFS = "panalink_default_stickers"
    private const val KEY_VERSION = "pack_version"
    private const val PACK_VERSION = 1
    private val uploadMutex = Mutex()

    private data class DefaultSpec(val id: String, val text: String, val color: Int, val stroke: Int, val style: Typeface)

    private val SPECS = listOf(
        DefaultSpec("pana_epale", "EPALE 👋", Color.WHITE, 0xFF005C4B.toInt(), Typeface.DEFAULT_BOLD),
        DefaultSpec("pana_hola", "HOLA", 0xFF00A884.toInt(), 0xFF0B141A.toInt(), Typeface.DEFAULT_BOLD),
        DefaultSpec("pana_jaja", "JAJA 😂", 0xFFFFD54F.toInt(), 0xFF5D4037.toInt(), Typeface.DEFAULT_BOLD),
        DefaultSpec("pana_love", "❤️", 0xFFFF5252.toInt(), 0xFF7F0000.toInt(), Typeface.DEFAULT),
        DefaultSpec("pana_fire", "🔥🔥", 0xFFFF9800.toInt(), 0xFF4E2600.toInt(), Typeface.DEFAULT_BOLD),
        DefaultSpec("pana_like", "👍", 0xFF64B5F6.toInt(), 0xFF0D47A1.toInt(), Typeface.DEFAULT),
        DefaultSpec("pana_cool", "😎", 0xFF80DEEA.toInt(), 0xFF004D40.toInt(), Typeface.DEFAULT),
        DefaultSpec("pana_party", "🎉", 0xFFCE93D8.toInt(), 0xFF4A148C.toInt(), Typeface.DEFAULT),
        DefaultSpec("pana_wow", "🤯", 0xFFFFAB91.toInt(), 0xFF3E2723.toInt(), Typeface.DEFAULT),
        DefaultSpec("pana_bless", "🙏", 0xFFFFF59D.toInt(), 0xFF5D4037.toInt(), Typeface.DEFAULT),
        DefaultSpec("pana_100", "💯", 0xFFFF8A80.toInt(), 0xFF7F0000.toInt(), Typeface.DEFAULT_BOLD),
        DefaultSpec("pana_chamo", "CHAMO 🇻🇪", 0xFFFFF176.toInt(), 0xFF1A237E.toInt(), Typeface.DEFAULT_BOLD)
    )

    private fun defaultDir(context: Context): File =
        File(StickerStudioRenderer.stickersDir(context), "default").apply { mkdirs() }

    /** Generates (once) and returns the default pack from local storage. */
    suspend fun getDefaultPack(context: Context): List<StickerResult> = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val needsGeneration = prefs.getInt(KEY_VERSION, 0) < PACK_VERSION ||
            SPECS.any { !File(defaultDir(context), "${it.id}.webp").exists() }

        if (needsGeneration) {
            for (spec in SPECS) {
                val file = File(defaultDir(context), "${spec.id}.webp")
                if (file.exists()) continue
                val bitmap = StickerStudioRenderer.renderTextSticker(
                    text = spec.text,
                    textColor = spec.color,
                    strokeColor = spec.stroke,
                    typeface = spec.style
                )
                val saved = StickerStudioRenderer.saveAsWebp(context, bitmap)
                bitmap.recycle()
                if (saved != null && saved != file) {
                    saved.renameTo(file)
                }
            }
            prefs.edit().putInt(KEY_VERSION, PACK_VERSION).apply()
        }

        SPECS.mapNotNull { spec ->
            val file = File(defaultDir(context), "${spec.id}.webp")
            if (file.exists()) {
                StickerResult(id = spec.id, url = file.absolutePath, preview = file.absolutePath)
            } else null
        }
    }

    /**
     * Returns a remote URL for a local sticker file, uploading it to the CDN the
     * first time. Results are cached in SharedPreferences so each file is only
     * uploaded once per install.
     */
    suspend fun resolveRemoteUrl(context: Context, localPath: String): String? = withContext(Dispatchers.IO) {
        val file = File(localPath)
        if (!file.exists()) return@withContext null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cacheKey = "remote_${file.name}_${file.length()}"
        prefs.getString(cacheKey, null)?.let { return@withContext it }

        uploadMutex.withLock {
            prefs.getString(cacheKey, null)?.let { return@withContext it }
            val mime = if (localPath.endsWith(".gif")) "image/gif" else "image/webp"
            val result = StickerCreationRepository.uploadAndCreateSticker(
                context = context,
                file = file,
                name = "Panalink Sticker",
                emoji = "🟢",
                mimeType = mime
            )
            val url = result.getOrNull() ?: return@withContext null
            prefs.edit().putString(cacheKey, url).apply()
            url
        }
    }

    fun isLocalStickerPath(url: String): Boolean =
        !url.startsWith("http://") && !url.startsWith("https://")
}
