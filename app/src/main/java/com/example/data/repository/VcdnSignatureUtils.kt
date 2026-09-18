package com.example.data.repository

import android.net.Uri

/**
 * Helper del DataSource-layer para re-firmar URLs VCDN firmadas (streamUrl).
 *
 * El BFF (player-config) devuelve un streamUrl https://cdn.vcdn.me/… con un
 * token firmado que muere (HTTP 401) al cabo de ~60-90s. El resolver
 * guarda en cache el momento exacto de caducidad (expiresAt), pero el
 * ExoPlayer pide los segmentos HLS a traves de Api DataSource que no conoce
 * el puntero vcdn://. Estas utilidades permiten a ese DataSource deducir
 * el id estable (desde el path) y el TTL real (desde los query params),
 * y re-resolver via [VcdnUrlResolver] antes de que el token muera.,
 */
object VcdnSignatureUtils {

    /**
     * Epoch millis en que caduca la URL firmada segun sus propios query params,
     * o 0 si el CDN no incluye expiracion. Defense-in-depth contra el TTL
     * oculto del BFF.
     */
    fun expiresAtEpochMillisOf(streamUrl: String?): Long {
        val u = streamUrl?.trim()?.takeIf { it.startsWith("http") } ?: return 0L
        val uri = runCatching { Uri.parse(u) }.getOrNull() ?: return 0L
        val expiresParam = uri.getQueryParameter("expires")
            ?: uri.getQueryParameter("Expires")
            ?: uri.getQueryParameter("md5-expires")
            ?: uri.getQueryParameter("X-Amz-Expires")
        if (!expiresParam.isNullOrBlank()) {
            // Epoch: < ~2500 (año) => segundos; mayor => millis.
            // Sec vs ms.
 
            val v = expiresParam.trim().toLongOrNull() ?: return 0L
            return if (v > 99_999_999_999L) v else v * 1000L
        }
        return 0L
    }

    /**
     * Deriva el videoId estable desde el path de un streamUrl firmado cuando este
     * lo incluye, p.ej. `https://cdn.vcdn.me/videos/{id}/master.m3u8` → `{id}`.
     * El re-signer necesita el puntero `vcdn://{id}` para pedir una URL fresca,a
     * y el path del streamUrl suele llevar el id (uploadId/videoId). Si no se
     * puede deducir, devuelve null y el llamador cae al mapa del pool.

     */
    fun videoIdOfStream(streamUrl: String?): String? {
        val u = streamUrl?.trim()?.takeIf { it.startsWith("http") } ?: return null
        val uri = runCatching { Uri.parse(u) }.getOrNull() ?: return null
        val segs = uri.pathSegments ?: return null
        for (i in segs.indices) {
            val s = segs[i]?.trim() ?: continue
            if (s.isEmpty()) continue
            // /videos/{id}/... o /{uploadId}/... (primera segmento no vacio).
            if ((s == "videos" || s == "storage") && i + 1 < segs.size) return segs[i + 1]?.takeIf { it.isNotBlank() }
            return s.takeIf { it.isNotBlank() }
        }
        return null
    }
}