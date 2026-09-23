package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.StickerResult
import com.example.data.model.SearchStickersRequest
import com.example.data.supabase.SessionManager
import com.example.data.supabase.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

object StickerRepository {
    private const val TAG = "StickerRepository"

    // Store recent and favorite stickers in SharedPreferences locally for clean performance
    private const val PREFS_NAME = "sticker_prefs"
    private const val KEY_RECENTS = "recent_stickers"
    private const val KEY_FAVORITES = "favorite_stickers"
    private const val MAX_RECENTS = 24

    // In-memory cache for search queries
    private val searchCache = java.util.Collections.synchronizedMap(HashMap<String, List<StickerResult>>())


    suspend fun getStickers(context: Context, query: String?, limit: Int = 40): List<StickerResult> = withContext(Dispatchers.IO) {
        val trimmed = query?.trim().orEmpty()
        val cacheKey = if (trimmed.isEmpty()) "sticker_$TRENDING_KEY" else "sticker_${trimmed.lowercase()}"
        if (searchCache.containsKey(cacheKey)) {
            Log.d(TAG, "Returning cached stickers for query: '$cacheKey'")
            return@withContext searchCache[cacheKey]!!
        }

        // 1. Klipy (fuente principal). Query vacío => trending.
        val klipy = fetchStickersFromKlipy(trimmed, limit)
        if (klipy != null) {
            searchCache[cacheKey] = klipy
            return@withContext klipy
        }

        // 2. Legacy: Giphy si sigue configurado y Klipy no está disponible.
        val giphy = fetchStickersFromGiphy(trimmed, limit)
        if (giphy != null) {
            searchCache[cacheKey] = giphy
            return@withContext giphy
        }

        // 3. Last Resort Fallback: static stickers if offline or APIs fail
        Log.w(TAG, "Proveedores remotos no disponibles. Cargando stickers de respaldo estáticos.")
        val backups = getBackupStickers(query)
        searchCache[cacheKey] = backups
        return@withContext backups
    }

    /** Busca GIFs en Klipy. Query vacío => trending.
     *  Cache en memoria y fallback estático si falla o no hay red. */
    suspend fun searchGifs(query: String, limit: Int = 40): List<StickerResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        val cacheKey = "gif_" + (if (trimmed.isEmpty()) TRENDING_KEY else trimmed.lowercase())
        if (searchCache.containsKey(cacheKey)) {
            return@withContext searchCache[cacheKey]!!
        }

        // 1. Klipy (fuente principal).
        val klipy = fetchGifsFromKlipy(trimmed, limit)
        if (klipy != null) {
            searchCache[cacheKey] = klipy
            return@withContext klipy
        }

        // 2. Legacy: Giphy si sigue configurado y Klipy no está disponible.
        val giphy = fetchGifsFromGiphy(trimmed, limit)
        if (giphy != null) {
            searchCache[cacheKey] = giphy
            return@withContext giphy
        }

        // 3. Fallback estático (offline / APIs caídas).
        Log.w(TAG, "Proveedores remotos no disponibles. Cargando GIFs de respaldo estáticos.")
        val fallbackGifs = getFallbackGifs()
        searchCache[cacheKey] = fallbackGifs
        return@withContext fallbackGifs
    }

    /** GIFs trending de Klipy (sin término de búsqueda). */
    suspend fun getTrendingGifs(limit: Int = 40): List<StickerResult> = searchGifs("", limit)

    /** Categorías de GIFs de Klipy (SEO queries para chips/exploración). */
    suspend fun getGifCategories(): List<String> = withContext(Dispatchers.IO){
        if (BuildConfig.KLIPY_API_KEY.isBlank()) return@withContext emptyList()
        try {
            val response = com.example.service.KlipyClient.apiService.gifCategories(
                apiKey = BuildConfig.KLIPY_API_KEY,
                limit = 40
            )
            if (!response.isSuccessful) return@withContext emptyList()
            val raw = response.body()?.data?.categories.orEmpty()
            raw.mapNotNull { it.query ?: it.category }.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        } catch (e: Exception) {
            Log.w(TAG, "Klipy GIF categorías falló", e)
            emptyList()
        }
    }

    /**
     * Klipy devuelve null cuando la API no está configurada o la llamada falló
     * (para poder encadenar el fallback). Una lista vacía es una respuesta válida
     * (el término no tuvo resultados) y NO dispara el fallback.
     */
    private suspend fun fetchGifsFromKlipy(query: String, limit: Int): List<StickerResult>? {
        if (BuildConfig.KLIPY_API_KEY.isBlank()) return null
        return try {
            // Paginacion multi-pagina: la API devuelve como maximo `limit` items por
            // pagina. Para que una busqueda como "oso" rinda 200-300 resultados,
            // se pide page tras page (hasta DESIRED_RESULT_COUNT) y se deduplica.



            val mapped = fetchKlipyPaged(query = query, pageLoader = { page, pageLimit ->
                if (query.isEmpty()) {
                    com.example.service.KlipyClient.apiService.trendingGifs(
                        apiKey = BuildConfig.KLIPY_API_KEY,
                        limit = pageLimit,
                        page = page
                    )
                } else {
                    com.example.service.KlipyClient.apiService.searchGifs(
                        apiKey = BuildConfig.KLIPY_API_KEY,
                        query = query,
                        limit = pageLimit,
                        page = page
                    )
                }
            })
            Log.d(TAG, "Klipy GIFs (" + (if (query.isEmpty()) "trending+categorias" else "q=$query") + "): ${mapped.size}")
            mapped
        } catch (e: Exception) {
            Log.w(TAG, "Klipy GIF API fallo", e)
            null
        }
    }
    private suspend fun fetchGifsFromKlipyPage(query: String, limit: Int, page: Int): List<StickerResult>? {
        if (BuildConfig.KLIPY_API_KEY.isBlank()) return null

        val response = if (query.isEmpty()) {
            com.example.service.KlipyClient.apiService.trendingGifs(
                apiKey = BuildConfig.KLIPY_API_KEY,
                limit = limit,
                page = page
            )
        } else {
            com.example.service.KlipyClient.apiService.searchGifs(
                apiKey = BuildConfig.KLIPY_API_KEY,
                query = query,
                limit = limit,
                page = page
            )
        }
        if (!response.isSuccessful) {
            Log.w(TAG, "Klipy GIF API HTTP ${response.code()}")
            return null
        }
        return response.body()?.data?.items.orEmpty().mapNotNull { it.toStickerResult() }
    }

    /**
     * Pagina una consulta de Klipy pidiendo paginas de `limit` items hasta
     * alcanzar DESIRED_RESULT_COUNT (o agotar `has_next`/errores), deduplicando.
    */




    private suspend fun fetchKlipyPaged(
        query: String,
        pageLoader: suspend (page: Int, pageLimit: Int) -> retrofit2.Response<com.example.data.model.KlipyResponse>
    ): List<StickerResult> {
        val pageLimit = KLIPY_PAGE_SIZE
        val seenIds = hashSetOf<String>()
        val acc = mutableListOf<StickerResult>()
        var page = 1
        var hasMore = true
        while (hasMore && acc.size < DESIRED_RESULT_COUNT && page <= MAX_KLIPY_PAGES) {

            val response = pageLoader(page, pageLimit) ?: return acc
            if (!response.isSuccessful) break
            val data = response.body()?.data ?: break
            val items = data.items.orEmpty().mapNotNull { it.toStickerResult() }
            if (items.isEmpty()) break
            for (item in items) {
                val itemId = item.id ?: continue
                if (acc.size >= DESIRED_RESULT_COUNT) break
                if (seenIds.add(itemId)) acc.add(item)
            }
            hasMore = data.hasNext == true
            page++
        }
        return acc
    }

    private suspend fun fetchStickersFromKlipy(query: String, limit: Int): List<StickerResult>? {
        if (BuildConfig.KLIPY_API_KEY.isBlank()) return null

        return try {
            val mapped = fetchKlipyPaged(query = query, pageLoader = { page, pageLimit ->
                if (query.isEmpty()) {
                    com.example.service.KlipyClient.apiService.trendingStickers(
                        apiKey = BuildConfig.KLIPY_API_KEY,
                        limit = pageLimit,
                        page = page
                    )
                } else {
                    com.example.service.KlipyClient.apiService.searchStickers(
                        apiKey = BuildConfig.KLIPY_API_KEY,
                        query = query,
                        limit = pageLimit,
                        page = page
                    )
                }
            })
            Log.d(TAG, "Klipy stickers (" + (if (query.isEmpty()) "trending" else "q=$query") + "): ${mapped.size}")
            mapped
        } catch (e: Exception) {
            Log.w(TAG, "Klipy sticker API fallo", e)
            null
        }
    }

    private suspend fun fetchGifsFromGiphy(query: String, limit: Int): List<StickerResult>? {
        if (BuildConfig.GIPHY_API_KEY.isBlank()) return null
        return try {
            val response = com.example.service.GiphyClient.apiService.searchGifs(
                apiKey = BuildConfig.GIPHY_API_KEY,
                query = query.ifEmpty { "funny" },
                limit = DESIRED_RESULT_COUNT
            )
            if (!response.isSuccessful) {
                Log.w(TAG, "Giphy GIF API HTTP ${response.code()}")
                return null
            }
            response.body()?.data?.map { gif ->
                StickerResult(
                    id = gif.id,
                    url = gif.images.fixedWidth.url,
                    preview = gif.images.fixedWidth.url,
                    width = gif.images.fixedWidth.width.toIntOrNull(),
                    height = gif.images.fixedWidth.height.toIntOrNull()
                )
            }.orEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "Giphy GIF API falló", e)
            null
        }
    }

    private suspend fun fetchStickersFromGiphy(query: String, limit: Int): List<StickerResult>? {
        if (BuildConfig.GIPHY_API_KEY.isBlank()) return null
        return try {
            val response = com.example.service.GiphyClient.apiService.searchStickers(
                apiKey = BuildConfig.GIPHY_API_KEY,
                query = query.ifEmpty { "funny" },
                limit = DESIRED_RESULT_COUNT
            )
            if (!response.isSuccessful) {
                Log.w(TAG, "Giphy sticker API HTTP ${response.code()}")
                return null
            }
            response.body()?.data?.map { sticker ->
                StickerResult(
                    id = sticker.id,
                    url = sticker.images.fixedWidth.url,
                    preview = sticker.images.fixedWidth.url,
                    width = sticker.images.fixedWidth.width.toIntOrNull(),
                    height = sticker.images.fixedWidth.height.toIntOrNull()
                )
            }.orEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "Giphy sticker API falló", e)
            null
        }
    }

    /**
     * Mapea una pieza de Klipy al modelo de la app.
     * url = GIF en mayor calidad (lo que se envía en el mensaje).
     * preview = GIF animado pequeño (lo que pinta el selector).
     * Las piezas marcadas como publicidad se descartan.
     */
    internal fun com.example.data.model.KlipyItem.toStickerResult(): StickerResult? {
        if (type?.equals("ad", ignoreCase = true) == true || type?.equals("ads", ignoreCase = true) == true) return null
        val file = file ?: return null
        // md da mejor resolución, pero algunos superan los ~5 MB; si el md es
        // demasiado pesado se usa el sm (220 px, equivalente al fixed_width previo).
        val md = file.md?.gif
        val sm = file.sm?.gif
        val full = when {
            md == null -> file.hd?.gif ?: sm ?: file.md?.webp
            (md.size ?: 0L) <= MAX_SEND_BYTES -> md
            else -> sm ?: md
        } ?: return null
        val url = full.url ?: return null
        // sm/xs: GIF animado liviano para el selector.
        val preview = sm?.url ?: file.xs?.gif?.url ?: file.sm?.jpg?.url ?: url
        return StickerResult(
            id = id?.toString() ?: slug,
            title = title,
            url = url,
            preview = preview,
            width = full.width ?: sm?.width,
            height = full.height ?: sm?.height,
            isGif = true
        )
    }

    private const val DESIRED_RESULT_COUNT = 280
    private const val KLIPY_PAGE_SIZE = 100
    private const val MAX_KLIPY_PAGES = 6

    private const val MAX_SEND_BYTES = 2_500_000L

    private fun getFallbackGifs(): List<StickerResult> = listOf(
        StickerResult(id = "gif_fb_1", url = "https://media.giphy.com/media/l0HlSgH9bXWbBMtQ4/giphy.gif", preview = "https://media.giphy.com/media/l0HlSgH9bXWbBMtQ4/giphy.gif"),
        StickerResult(id = "gif_fb_2", url = "https://media.giphy.com/media/3o7TKSjRrfIPjeiVyM/giphy.gif", preview = "https://media.giphy.com/media/3o7TKSjRrfIPjeiVyM/giphy.gif"),
        StickerResult(id = "gif_fb_3", url = "https://media.giphy.com/media/26AHONQ79FdYzhAI0/giphy.gif", preview = "https://media.giphy.com/media/26AHONQ79FdYzhAI0/giphy.gif"),
        StickerResult(id = "gif_fb_4", url = "https://media.giphy.com/media/l41YptBC8A0gD9XEY/giphy.gif", preview = "https://media.giphy.com/media/l41YptBC8A0gD9XEY/giphy.gif")
    )

    private const val TRENDING_KEY = "__trending__"

    private fun getBackupStickers(query: String?): List<StickerResult> {
        val backups = listOf(
            StickerResult(
                url = "https://media1.giphy.com/media/v1.Y2lkPTc5MGI3NjExM2ZicTlycWRtOGFhNDdhaTMyMzA4ZXhndW1hcGswYmF0cjB5NTA4ZiZlcD12MV9zdGlja2Vyc19zZWFyY2gmY3Q9cw/Lp71UIpGgajCbaSg48/giphy.gif",
                preview = "https://media1.giphy.com/media/v1.Y2lkPTc5MGI3NjExM2ZicTlycWRtOGFhNDdhaTMyMzA4ZXhndW1hcGswYmF0cjB5NTA4ZiZlcD12MV9zdGlja2Vyc19zZWFyY2gmY3Q9cw/Lp71UIpGgajCbaSg48/giphy-preview.gif"
            ),
            StickerResult(
                url = "https://media2.giphy.com/media/v1.Y2lkPTc5MGI3NjExdzBpaDBlN254MWp6YWN3bHhndHhzNDJmOWlxcThicjhsOTNsM2ptdSZlcD12MV9zdGlja2Vyc19zZWFyY2gmY3Q9cw/mGMcg3OovpWvD7A6bI/giphy.gif",
                preview = "https://media2.giphy.com/media/v1.Y2lkPTc5MGI3NjExdzBpaDBlN254MWp6YWN3bHhndHhzNDJmOWlxcThicjhsOTNsM2ptdSZlcD12MV9zdGlja2Vyc19zZWFyY2gmY3Q9cw/mGMcg3OovpWvD7A6bI/giphy-preview.gif"
            ),
            StickerResult(
                url = "https://media3.giphy.com/media/v1.Y2lkPTc5MGI3NjExOW11cGJ2MXNoMDZpZjhuMjVnaXhhajQyeWlyYWtsaHBsdXFobHFkOCZlcD12MV9zdGlja2Vyc19zZWFyY2gmY3Q9cw/xTk9ZY0C9ZADnqiSE8/giphy.gif",
                preview = "https://media3.giphy.com/media/v1.Y2lkPTc5MGI3NjExOW11cGJ2MXNoMDZpZjhuMjVnaXhhajQyeWlyYWtsaHBsdXFobHFkOCZlcD12MV9zdGlja2Vyc19zZWFyY2gmY3Q9cw/xTk9ZY0C9ZADnqiSE8/giphy-preview.gif"
            ),
            StickerResult(
                url = "https://media0.giphy.com/media/v1.Y2lkPTc5MGI3NjExNXdycnI2azVldXUzNW85MTFpYnMydWZkcXRma3ExZ3g5MHphNWhsOSZlcD12MV9zdGlja2Vyc19zZWFyY2gmY3Q9cw/bF55Zon7jVPDqE1pCO/giphy.gif",
                preview = "https://media0.giphy.com/media/v1.Y2lkPTc5MGI3NjExNXdycnI2azVldXUzNW85MTFpYnMydWZkcXRma3ExZ3g5MHphNWhsOSZlcD12MV9zdGlja2Vyc19zZWFyY2gmY3Q9cw/bF55Zon7jVPDqE1pCO/giphy-preview.gif"
            ),
            StickerResult(
                url = "https://media4.giphy.com/media/v1.Y2lkPTc5MGI3NjExaTJvdHRzNmE3ZnYzdHFubms0N2lrdWhqenBrOGVzbjNldTcxOHFxbSZlcD12MV9zdGlja2Vyc19zZWFyY2gmY3Q9cw/v9N2Sct8Gst8eI2Kby/giphy.gif",
                preview = "https://media4.giphy.com/media/v1.Y2lkPTc5MGI3NjExaTJvdHRzNmE3ZnYzdHFubms0N2lrdWhqenBrOGVzbjNldTcxOHFxbSZlcD12MV9zdGlja2Vyc19zZWFyY2gmY3Q9cw/v9N2Sct8Gst8eI2Kby/giphy-preview.gif"
            ),
            StickerResult(
                url = "https://media3.giphy.com/media/v1.Y2lkPTc5MGI3NjExa2k2bjBzNmR2dnlhcHptZ2Y2Znd5eDNndjJ0dWQzYWZlbm8xeHlhOCZlcD12MV9zdGlja2Vyc19zZWFyY2gmY3Q9cw/yFQ0ywscgobJK/giphy.gif",
                preview = "https://media3.giphy.com/media/v1.Y2lkPTc5MGI3NjExa2k2bjBzNmR2dnlhcHptZ2Y2Znd5eDNndjJ0dWQzYWZlbm8xeHlhOCZlcD12MV9zdGlja2Vyc19zZWFyY2gmY3Q9cw/yFQ0ywscgobJK/giphy-preview.gif"
            ),
            StickerResult(
                url = "https://media2.giphy.com/media/v1.Y2lkPTc5MGI3NjExdGs3aGxrdmRzdmJtdmMyczZmdWphcHhhbndrcDhrYzN2ZjdycHdyOCZlcD12MV9zdGlja2Vyc19zZWFyY2gmY3Q9cw/P31RoFejRclZkODN9m/giphy.gif",
                preview = "https://media2.giphy.com/media/v1.Y2lkPTc5MGI3NjExdGs3aGxrdmRzdmJtdmMyczZmdWphcHhhbndrcDhrYzN2ZjdycHdyOCZlcD12MV9zdGlja2Vyc19zZWFyY2gmY3Q9cw/P31RoFejRclZkODN9m/giphy-preview.gif"
            ),
            StickerResult(
                url = "https://media4.giphy.com/media/v1.Y2lkPTc5MGI3NjExZnpjdGsxbHh3NXR4MWhqMzZibms4MGpxOXVpODZ0cThtcnkxaTUzNCZlcD12MV9zdGlja2Vyc19zZWFyY2gmY3Q9cw/L3Z6YyvcoVn0hO07b7/giphy.gif",
                preview = "https://media4.giphy.com/media/v1.Y2lkPTc5MGI3NjExZnpjdGsxbHh3NXR4MWhqMzZibms4MGpxOXVpODZ0cThtcnkxaTUzNCZlcD12MV9zdGlja2Vyc19zZWFyY2gmY3Q9cw/L3Z6YyvcoVn0hO07b7/giphy-preview.gif"
            )
        )
        if (query.isNullOrEmpty()) return backups
        return backups.filter { 
            it.url.contains(query, ignoreCase = true) || query.contains("cat", ignoreCase = true) || query.contains("love", ignoreCase = true)
        }.ifEmpty { backups }
    }

    // --- RPC Helpers for Recents, Favorites & Saved ---
    
    // In-memory cache to prevent constant fetching
    private var cachedSaved = mutableListOf<StickerResult>()
    private var cachedFavorites = mutableListOf<StickerResult>()
    private var cachedRecents = mutableListOf<StickerResult>()
    private var cacheInitialized = false

    suspend fun syncStickersFromRemote(context: Context) = withContext(Dispatchers.IO) {
        if (!SupabaseClient.isConfigured) return@withContext
        try {
            val service = SupabaseClient.apiService ?: return@withContext
            val token = SupabaseClient.currentToken ?: return@withContext
            val apiKey = SupabaseClient.supabaseAnonKey
            val auth = "Bearer $token"

            val favRes = service.getFavoriteStickers(apiKey, auth)
            if (favRes.isSuccessful) {
                cachedFavorites = parseStickerResultList(favRes.body())
            }

            val savedRes = service.getSavedStickers(apiKey, auth)
            if (savedRes.isSuccessful) {
                cachedSaved = parseStickerResultList(savedRes.body())
            }

            val recentsRes = service.getRecentStickers(apiKey, auth)
            if (recentsRes.isSuccessful) {
                cachedRecents = parseStickerResultList(recentsRes.body())
            }
            cacheInitialized = true
            
            // Also sync to shared preferences for offline mode
            saveToPrefs(context, KEY_FAVORITES, cachedFavorites)
            saveToPrefs(context, KEY_RECENTS, cachedRecents)
            saveToPrefs(context, "saved_stickers", cachedSaved)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing stickers from remote", e)
        }
    }
    
    private fun parseStickerResultList(body: List<Map<String, Any>>?): MutableList<StickerResult> {
        val list = mutableListOf<StickerResult>()
        body?.forEach { map ->
            val url = map["url"] as? String ?: map["sticker_url"] as? String
            val preview = map["preview"] as? String ?: map["preview_url"] as? String ?: url
            if (url != null) {
                list.add(StickerResult(url = url, preview = preview!!))
            }
        }
        return list
    }

    suspend fun getRecentStickers(context: Context): List<StickerResult> {
        if (!cacheInitialized) {
            cachedRecents = getFromPrefs(context, KEY_RECENTS).toMutableList()
        }
        return cachedRecents
    }

    suspend fun addRecentSticker(context: Context, sticker: StickerResult) = withContext(Dispatchers.IO) {
        cachedRecents.removeAll { it.url == sticker.url }
        cachedRecents.add(0, sticker)
        if (cachedRecents.size > MAX_RECENTS) {
            cachedRecents.removeAt(cachedRecents.lastIndex)
        }
        saveToPrefs(context, KEY_RECENTS, cachedRecents)
        
        if (SupabaseClient.isConfigured) {
            try {
                val service = SupabaseClient.apiService ?: return@withContext
                val token = SupabaseClient.currentToken ?: return@withContext
                val params = mapOf("sticker_url" to sticker.url, "preview_url" to sticker.preview)
                service.registerStickerUsage(SupabaseClient.supabaseAnonKey, "Bearer $token", params)
            } catch (e: Exception) {
                Log.e(TAG, "Error registering sticker usage", e)
            }
        }
    }

    suspend fun getFavoriteStickers(context: Context): List<StickerResult> {
        if (!cacheInitialized) {
            cachedFavorites = getFromPrefs(context, KEY_FAVORITES).toMutableList()
        }
        return cachedFavorites
    }

    suspend fun toggleFavoriteSticker(context: Context, sticker: StickerResult): Boolean = withContext(Dispatchers.IO) {
        val exists = cachedFavorites.find { it.url == sticker.url }
        val isFav: Boolean
        
        val service = SupabaseClient.apiService
        val token = SupabaseClient.currentToken
        val apiKey = SupabaseClient.supabaseAnonKey
        
        if (exists != null) {
            cachedFavorites.remove(exists)
            isFav = false
            if (SupabaseClient.isConfigured && service != null && token != null) {
                try {
                    service.unfavoriteSticker(apiKey, "Bearer $token", mapOf("sticker_url" to sticker.url))
                } catch(e: Exception) {}
            }
        } else {
            cachedFavorites.add(0, sticker)
            isFav = true
            if (SupabaseClient.isConfigured && service != null && token != null) {
                try {
                    service.favoriteSticker(apiKey, "Bearer $token", mapOf("sticker_url" to sticker.url, "preview_url" to sticker.preview))
                } catch(e: Exception) {}
            }
        }
        saveToPrefs(context, KEY_FAVORITES, cachedFavorites)
        return@withContext isFav
    }

    fun isStickerFavorite(context: Context, stickerUrl: String): Boolean {
        if (!cacheInitialized) {
            cachedFavorites = getFromPrefs(context, KEY_FAVORITES).toMutableList()
        }
        return cachedFavorites.any { it.url == stickerUrl }
    }
    
    suspend fun saveSticker(context: Context, sticker: StickerResult) = withContext(Dispatchers.IO) {
        if (!cacheInitialized) {
            cachedSaved = getFromPrefs(context, "saved_stickers").toMutableList()
        }
        if (cachedSaved.none { it.url == sticker.url }) {
            cachedSaved.add(0, sticker)
            saveToPrefs(context, "saved_stickers", cachedSaved)
        }
        
        if (SupabaseClient.isConfigured) {
            try {
                val service = SupabaseClient.apiService ?: return@withContext
                val token = SupabaseClient.currentToken ?: return@withContext
                val params = mapOf("sticker_url" to sticker.url, "preview_url" to sticker.preview)
                service.saveSticker(SupabaseClient.supabaseAnonKey, "Bearer $token", params)
            } catch (e: Exception) {
                Log.e(TAG, "Error saving sticker", e)
            }
        }
    }
    
    suspend fun getSavedStickers(context: Context): List<StickerResult> {
        if (!cacheInitialized) {
            cachedSaved = getFromPrefs(context, "saved_stickers").toMutableList()
        }
        return cachedSaved
    }

    private fun saveToPrefs(context: Context, key: String, list: List<StickerResult>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(key, serializeStickerList(list)).apply()
    }
    
    private fun getFromPrefs(context: Context, key: String): List<StickerResult> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(key, null) ?: return emptyList()
        return deserializeStickerList(jsonStr)
    }

    private fun serializeStickerList(list: List<StickerResult>): String {
        return try {
            val array = org.json.JSONArray()
            for (sticker in list) {
                val obj = JSONObject()
                obj.put("url", sticker.url)
                obj.put("preview", sticker.preview)
                array.put(obj)
            }
            array.toString()
        } catch (e: Exception) {
            ""
        }
    }

    private fun deserializeStickerList(jsonStr: String): List<StickerResult> {
        val list = mutableListOf<StickerResult>()
        try {
            val array = org.json.JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(StickerResult(url = obj.getString("url"), preview = obj.getString("preview")))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deserialize sticker list", e)
        }
        return list
    }
}
