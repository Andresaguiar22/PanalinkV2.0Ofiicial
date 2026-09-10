package com.example.data.video

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

import androidx.media3.common.util.UnstableApi
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@UnstableApi
object CacheDataSourceFactory {
    private const val TAG = "CacheDataSourceFactory"

    /** URLs with a prefetch currently running — prevents duplicate downloads of the same video. */
    private val prefetchInFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** URLs already warmed into cache — bounded LRU so we never re-download on repeated swipes. */
    private val prefetchCompleted: MutableMap<String, Long> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Long>(128, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > 128
        }
    )

    /** Caps concurrent prefetches so fast flings never trigger a download storm. */
    private val prefetchSemaphore = Semaphore(2)

    /**
    * Stable cache keys based on the media path. Signed query parameters rotate
    * between sessions; including them would make an already cached video look
    * like a different resource and break offline playback.
     */
    private val stableCacheKeyFactory = CacheKeyFactory { dataSpec ->
        val uri = dataSpec.uri
        uri.path?.takeIf { it.isNotBlank() } ?: uri.toString().substringBefore('?')
    }

    fun getCacheDataSourceFactory(context: Context): DataSource.Factory {
        return getCacheDataSourceFactory(context, null)
    }

    /**
     * When [customHttpFactory] is provided, it is used instead of the default
     * DefaultHttpDataSource.Factory. This lets callers (e.g. AppFloatingPlayerManager)
     * set per-stream headers (User-Agent, Referer) on a shared, reusable
     * factory instance so that headers can be updated dynamically between
     * setMediaItem calls without rebuilding the ExoPlayer.
     *
     * The [customHttpFactory] is wrapped by [B2ResignDataSourceFactory] so B2
     * presigned URLs continue to be re-signed transparently.
     */
    fun getCacheDataSourceFactory(
        context: Context,
        customHttpFactory: DefaultHttpDataSource.Factory?
    ): DataSource.Factory {
        val httpDataSourceFactory = customHttpFactory ?: run {
            DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 Panalink/1.0")
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000)
                .setAllowCrossProtocolRedirects(true)
        }

        // Wrap the HTTP factory so any Backblaze B2 URL (presigned GETs expire after
        // 7 days) is re-signed on open() via b2-presign-download before the bytes flow.
        val upstreamFactory = B2ResignDataSourceFactory(httpDataSourceFactory)

        return try {
            val simpleCache = VideoCacheManager.getCache(context)
            if (simpleCache != null) {
                CacheDataSource.Factory()
                    .setCache(simpleCache)
                    .setCacheKeyFactory(stableCacheKeyFactory)
                    .setUpstreamDataSourceFactory(upstreamFactory)
                    .setCacheReadDataSourceFactory(FileDataSource.Factory())
                    .setCacheWriteDataSinkFactory(CacheDataSink.Factory().setCache(simpleCache))
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            } else {
                upstreamFactory
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize CacheDataSource, falling back to network-only factory", e)
            upstreamFactory
        }
    }

    /**
     * Pre-fetches video data into the shared SimpleCache in the background.
     *
     * Single-pass design: opens the DataSource exactly once to read the first
     * [maxBytes] of the stream. No separate probe/probeContentLength step,
     * eliminating redundant HTTP round-trips on HLS/CDN media.
     */
    fun prefetchVideo(context: Context, url: String?, maxBytes: Long = 20L * 1024L * 1024L) {
        if (url.isNullOrBlank() || !url.startsWith("http")) return
        // Offline: sin red el prefetch solo quemaría timeouts de red; saltar.
        // El caché existente ya se sirve desde disco por el SimpleCache.
        if (!com.example.util.NetworkMonitor.isOnline.value) {
            android.util.Log.d(TAG, "Offline: skipping video prefetch for $url")
            return
        }
        if (prefetchCompleted.containsKey(url)) return
        if (!prefetchInFlight.add(url)) return // already downloading this url

        val appCtx = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            prefetchSemaphore.withPermit {
                var dataSource: DataSource? = null
                try {
                    dataSource = getCacheDataSourceFactory(appCtx).createDataSource()
                    val dataSpec = DataSpec(Uri.parse(url), 0, maxBytes)
                    val buffer = ByteArray(65536) // 64KB buffer para faster copy

                    Log.d(TAG, "Pre-fetching up to ${maxBytes / 1024 / 1024}MB of $url")
                    var bytesRead = 0L
                    var reachedEnd = false
                    try {
                        dataSource!!.open(dataSpec)
                        while (bytesRead < maxBytes) {
                            val read = dataSource!!.read(buffer, 0, buffer.size)
                            if (read == -1) {
                                reachedEnd = true
                                break
                            }
                            bytesRead += read
                        }
                    } finally {
                        dataSource?.close()
                    }
                    if (bytesRead >= maxBytes || reachedEnd) {
                        prefetchCompleted[url] = System.currentTimeMillis()
                        Log.d(TAG, "Pre-fetched ${bytesRead / 1024} KB for video: $url")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Pre-fetch failed for $url: ${e.localizedMessage}")
                } finally {
                    prefetchInFlight.remove(url)
                }
            }
        }
    }
}

/**
 * Wraps a [DataSource.Factory] so that any Backblaze B2 presigned URL is re-signed
 * (via [com.example.data.repository.B2UrlResolver]) before delegating to the
 * underlying HTTP DataSource. B2 presigned GETs expire after 7 days; without this
 * wrapper, cached/expired B2 media would 403 on playback.
 *
 * Non-B2 URLs pass through unchanged (zero overhead for CDN/Supabase media).
 */
@UnstableApi
private class B2ResignDataSourceFactory(
    private val delegate: DataSource.Factory,
) : DataSource.Factory {
    override fun createDataSource(): DataSource = B2ResignDataSource(delegate.createDataSource())
}

/**
 * Re-signs Backblaze B2 URLs on [open] before delegating to the underlying HTTP
 * DataSource. Implements [DataSource] directly (the delegate already reports
 * transfer events to the player, so no extra bookkeeping is needed here).
 */
@UnstableApi
private class B2ResignDataSource(
    private val delegate: DataSource,
) : DataSource {

    override fun open(dataSpec: DataSpec): Long {
        val originalUri = dataSpec.uri.toString()
        // Offline: no re-firmar (requiere red) ni abrir la fuente remota; fallar
        // rápido para que el player derive al estado de error y la UI muestre Reintentar..
        if (!com.example.util.NetworkMonitor.isOnline.value) {
            throw java.io.IOException("Sin conexión: no se puede abrir media remota")
        }
        val resolvedUri = if (com.example.data.repository.B2UrlResolver.isB2Url(originalUri)) {
            // open() is called on ExoPlayer's loading thread; a blocking re-sign is
            // appropriate here (no main-thread concerns).
            runBlocking {
                com.example.data.repository.B2UrlResolver.resolve(originalUri)
            }
        } else originalUri

        val effectiveSpec = if (resolvedUri != originalUri) {
            dataSpec.withUri(Uri.parse(resolvedUri))
        } else dataSpec
        return delegate.open(effectiveSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = delegate.read(buffer, offset, length)

    override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
        delegate.addTransferListener(transferListener)
    }

    override fun getUri(): Uri? = delegate.uri

    override fun close() { delegate.close() }
}
