package com.example.util

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import java.io.File

/**
 * Truncates a video file to the first [maxDurationMs] milliseconds without
 * re-encoding (stream copy). Used to enforce the 2-minute limit on story
 * videos: the user is warned in the editor and the uploaded file is cut so
 * everyone sees at most the first 2 minutes.
 *
 * Returns true when the file was truncated (output written and usable), false
 * otherwise (caller should fall back to the original file).
 */
object VideoTruncatorHelper {

    fun durationMs(path: String): Long {
        return try {
            android.media.MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(path)
                retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    fun needsTruncation(path: String, maxDurationMs: Long): Boolean {
        if (maxDurationMs <= 0L) return false
        val duration = durationMs(path)
        return duration > maxDurationMs
    }

    /**
     * Copies the first [maxDurationMs] of [srcPath] into [outPath].
     * If [srcPath] is already within the limit, copies nothing and returns false
     * so the caller keeps the original untouched.
     */
    fun truncateToMs(srcPath: String, outPath: String, maxDurationMs: Long): Boolean {
        val maxUs = maxDurationMs * 1000L
        if (srcPath == outPath || !File(srcPath).exists()) return false
        if (durationMs(srcPath) <= maxDurationMs) return false

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(srcPath)
            if (extractor.trackCount <= 0) return false

            // Which track indices carry video vs audio.
            val trackKinds = mutableListOf<Boolean>() // true = video
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                trackKinds.add(mime.startsWith("video/"))
            }

            // Need at least one video track to produce a playable story clip.
            if (trackKinds.none { it }) return false

            val muxer = MediaMuxer(outPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            try {
                val muxerTracks = IntArray(extractor.trackCount) { -1 }
                for (i in 0 until extractor.trackCount) {
                    muxerTracks[i] = muxer.addTrack(extractor.getTrackFormat(i))
                }
                muxer.start()

                val buffer = java.nio.ByteBuffer.allocate(1 shl 20) // 1 MiB
                val bufferInfo = MediaCodec.BufferInfo()

                // Copy each track from its start up to maxUs. For video, stop at
                // the sample that crosses the limit; the next GOP would reference
                // samples we cut, which corrupts the file.
                for (trackIndex in 0 until extractor.trackCount) {
                    extractor.selectTrack(trackIndex)
                    extractor.seekTo(0L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                    while (true) {
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) break
                        val sampleTimeUs = extractor.sampleTime
                        if (sampleTimeUs > maxUs) break
                        bufferInfo.set(0, size, sampleTimeUs, extractor.sampleFlags)
                        buffer.clear()
                        buffer.limit(size)
                        muxer.writeSampleData(muxerTracks[trackIndex], buffer, bufferInfo)
                        extractor.advance()
                    }
                }
                muxer.stop()
                return File(outPath).exists() && File(outPath).length() > 0L
            } finally {
                runCatching { muxer.release() }
            }
        } catch (e: Exception) {
            // Clean up the output so the caller never serves a corrupt file.
            runCatching { File(outPath).delete() }
            return false
        } finally {
            runCatching { extractor.release() }
        }
    }
}