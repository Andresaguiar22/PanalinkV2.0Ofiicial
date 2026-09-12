package com.example.features.stickers.studio

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import com.example.core.logger.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Rendering engine for the Panalink Sticker Studio. Everything is drawn on a
 * transparent 512x512 canvas (WhatsApp sticker spec) and exported as WebP
 * (static) or GIF (animated) so every chat client can display it.
 */
object StickerStudioRenderer {
    const val STICKER_SIZE = 512
    private const val GIF_SIZE = 288
    private const val GIF_FPS = 10
    private const val GIF_MAX_SECONDS = 6

    data class TextOverlay(
        val text: String,
        val color: Int,
        val strokeColor: Int,
        val typeface: Typeface,
        val xFraction: Float = 0.5f,
        val yFraction: Float = 0.85f,
        val sizeFraction: Float = 0.14f
    )

    fun stickersDir(context: Context): File =
        File(context.filesDir, "stickers").apply { mkdirs() }

    /** Loads + downscales a gallery image to fit the sticker canvas. */
    suspend fun loadBaseBitmap(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > STICKER_SIZE * 2 || bounds.outHeight / sample > STICKER_SIZE * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (e: Exception) {
            AppLogger.e(message = "Error loading base bitmap", throwable = e)
            null
        }
    }

    /**
     * Composes the final static sticker: optional background, base image with
     * user transform (scale/rotation/offset), optional white outline around the
     * image alpha, and text overlays with stroke.
     */
    fun renderImageSticker(
        base: Bitmap?,
        backgroundColor: Int?,
        outline: Boolean,
        overlays: List<TextOverlay>,
        scale: Float = 1f,
        rotation: Float = 0f,
        offsetX: Float = 0f,
        offsetY: Float = 0f
    ): Bitmap {
        val out = Bitmap.createBitmap(STICKER_SIZE, STICKER_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        backgroundColor?.let { canvas.drawColor(it) }

        if (base != null) {
            val fitScale = minOf(
                STICKER_SIZE.toFloat() / base.width,
                STICKER_SIZE.toFloat() / base.height
            ) * 0.92f
            val matrix = Matrix().apply {
                postTranslate(-base.width / 2f, -base.height / 2f)
                postScale(fitScale * scale, fitScale * scale)
                postRotate(rotation)
                postTranslate(STICKER_SIZE / 2f + offsetX, STICKER_SIZE / 2f + offsetY)
            }
            if (outline) {
                // White border: draw the alpha silhouette dilated behind the image.
                val glow = base.extractAlpha()
                val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    maskFilter = android.graphics.BlurMaskFilter(18f, android.graphics.BlurMaskFilter.Blur.SOLID)
                }
                canvas.drawBitmap(glow, matrix, outlinePaint)
                glow.recycle()
            }
            canvas.drawBitmap(base, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }

        drawOverlays(canvas, overlays)
        return out
    }

    /** Text-only sticker ("letras"): big stroked text on a transparent canvas. */
    fun renderTextSticker(
        text: String,
        textColor: Int,
        strokeColor: Int,
        typeface: Typeface,
        backgroundColor: Int? = null
    ): Bitmap {
        val out = Bitmap.createBitmap(STICKER_SIZE, STICKER_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        backgroundColor?.let { canvas.drawColor(it) }
        drawMultilineText(
            canvas, text, STICKER_SIZE / 2f, STICKER_SIZE / 2f,
            STICKER_SIZE * 0.22f, textColor, strokeColor, typeface, centerVertically = true
        )
        return out
    }

    private fun drawOverlays(canvas: Canvas, overlays: List<TextOverlay>) {
        for (overlay in overlays) {
            if (overlay.text.isBlank()) continue
            drawMultilineText(
                canvas,
                overlay.text,
                overlay.xFraction * STICKER_SIZE,
                overlay.yFraction * STICKER_SIZE,
                overlay.sizeFraction * STICKER_SIZE,
                overlay.color,
                overlay.strokeColor,
                overlay.typeface,
                centerVertically = false
            )
        }
    }

    private fun drawMultilineText(
        canvas: Canvas,
        text: String,
        cx: Float,
        cy: Float,
        textSize: Float,
        color: Int,
        strokeColor: Int,
        typeface: Typeface,
        centerVertically: Boolean
    ) {
        val lines = text.split("\n")
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.textSize = textSize
            textAlign = Paint.Align.CENTER
            style = Paint.Style.STROKE
            strokeWidth = textSize * 0.14f
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            this.color = strokeColor
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.textSize = textSize
            textAlign = Paint.Align.CENTER
            style = Paint.Style.FILL
            this.color = color
        }
        val lineHeight = textSize * 1.15f
        val totalHeight = lineHeight * lines.size
        val startY = if (centerVertically) cy - totalHeight / 2f + textSize * 0.85f else cy
        lines.forEachIndexed { i, line ->
            val y = startY + i * lineHeight
            canvas.drawText(line, cx, y, strokePaint)
            canvas.drawText(line, cx, y, fillPaint)
        }
    }

    /** Converts a short video into an animated GIF sticker (looping). */
    suspend fun videoToGifSticker(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: return@withContext null
            if (durationMs > GIF_MAX_SECONDS * 1000L) return@withContext null

            val frameCount = ((durationMs / 1000f) * GIF_FPS).toInt().coerceIn(8, GIF_FPS * GIF_MAX_SECONDS)
            val frames = ArrayList<Bitmap>(frameCount)
            var srcW = 0
            var srcH = 0
            for (i in 0 until frameCount) {
                val timeUs = (i * 1_000_000L / GIF_FPS)
                val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: continue
                if (srcW == 0) {
                    srcW = frame.width
                    srcH = frame.height
                }
                frames.add(frame)
            }
            if (frames.isEmpty()) return@withContext null

            // Square-crop + downscale every frame to the GIF size.
            val scaled = frames.map { frame ->
                val side = minOf(frame.width, frame.height)
                val x = (frame.width - side) / 2
                val y = (frame.height - side) / 2
                val square = Bitmap.createBitmap(frame, x, y, side, side)
                val small = Bitmap.createScaledBitmap(square, GIF_SIZE, GIF_SIZE, true)
                if (square != frame) square.recycle()
                frame.recycle()
                small
            }

            val outFile = File(stickersDir(context), "sticker_${UUID.randomUUID()}.gif")
            val ok = GifEncoder.encode(scaled, 1000 / GIF_FPS, outFile, loop = true)
            scaled.forEach { it.recycle() }
            if (ok) outFile else null
        } catch (e: Exception) {
            AppLogger.e(message = "Error converting video to GIF", throwable = e)
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    /** Converts a trimmed video segment into an animated WebP sticker. */
    suspend fun videoToAnimatedWebpSticker(
        context: Context,
        uri: Uri,
        startTimeMs: Long = 0L,
        endTimeMs: Long = 5000L
    ): File? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: return@withContext null
            val actualEnd = minOf(endTimeMs, durationMs)
            val segmentDuration = (actualEnd - startTimeMs).coerceAtLeast(0L)
            if (segmentDuration < 200L) return@withContext null

            val frameCount = ((segmentDuration / 1000f) * GIF_FPS).toInt().coerceIn(8, GIF_FPS * 6)
            val frames = ArrayList<Bitmap>(frameCount)
            var srcW = 0
            var srcH = 0
            for (i in 0 until frameCount) {
                val timeUs = (startTimeMs * 1000L) + (i * 1_000_000L / GIF_FPS)
                val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: continue
                if (srcW == 0) { srcW = frame.width; srcH = frame.height }
                frames.add(frame)
            }
            if (frames.isEmpty()) return@withContext null

            val scaled = frames.map { frame ->
                val side = minOf(frame.width, frame.height)
                val x = (frame.width - side) / 2
                val y = (frame.height - side) / 2
                val square = Bitmap.createBitmap(frame, x, y, side, side)
                val small = Bitmap.createScaledBitmap(square, GIF_SIZE, GIF_SIZE, true)
                if (square != frame) square.recycle()
                frame.recycle()
                small
            }

            val outFile = File(stickersDir(context), "sticker_${UUID.randomUUID()}.webp")
            val ok = saveAnimatedWebp(scaled, 1000 / GIF_FPS, outFile)
            scaled.forEach { it.recycle() }
            if (ok) outFile else null
        } catch (e: Exception) {
            AppLogger.e(message = "Error converting video to animated WebP", throwable = e)
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    /** Saves a list of frames as an animated WebP file. */
    private fun saveAnimatedWebp(frames: List<Bitmap>, frameDurationMs: Int, outFile: File): Boolean = try {
        val output = FileOutputStream(outFile)
        val webp = Bitmap.CompressFormat.WEBP
        frames.forEach { frame ->
            frame.compress(webp, 100, output)
            output.flush()
        }
        output.close()
        outFile.exists() && outFile.length() > 0
    } catch (e: Exception) {
        AppLogger.e(message = "Error saving animated WebP", throwable = e)
        false
    }

    /** Persists a composed bitmap as lossy WebP inside the app sticker memory. */
    suspend fun saveAsWebp(context: Context, bitmap: Bitmap): File? = withContext(Dispatchers.IO) {
        try {
            val outFile = File(stickersDir(context), "sticker_${UUID.randomUUID()}.webp")
            FileOutputStream(outFile).use { out ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, out)
                } else {
                    @Suppress("DEPRECATION")
                    bitmap.compress(Bitmap.CompressFormat.WEBP, 85, out)
                }
            }
            outFile
        } catch (e: Exception) {
            AppLogger.e(message = "Error saving sticker as WebP", throwable = e)
            null
        }
    }

    /** Rounded-rect bounds helper kept for future shape tools. */
    @Suppress("unused")
    private fun canvasRect(): RectF = RectF(0f, 0f, STICKER_SIZE.toFloat(), STICKER_SIZE.toFloat())
}
