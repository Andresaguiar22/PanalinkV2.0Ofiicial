package com.example.features.stickers.data

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.features.stickers.domain.Sticker
import com.example.features.stickers.domain.StickerPack
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object StickerCatalogRepository {
    private const val TAG = "StickerCatalogRepository"
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private var cachedPacks: List<StickerPack>? = null

    suspend fun getCatalog(context: Context): List<StickerPack> = withContext(Dispatchers.IO) {
        cachedPacks?.let { return@withContext it }

        val serverUrl = try {
            val field = BuildConfig::class.java.getField("SERVER_URL")
            field.get(null) as? String ?: "https://panalink.app"
        } catch (e: Exception) {
            "https://panalink.app"
        }

        val catalogUrl = "$serverUrl/stickers/catalog.json"
        try {
            val request = Request.Builder()
                .url(catalogUrl)
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonStr = response.body?.string()
                if (!jsonStr.isNullOrBlank()) {
                    val type = Types.newParameterizedType(List::class.java, StickerPack::class.java)
                    val adapter = moshi.adapter<List<StickerPack>>(type)
                    val packs = adapter.fromJson(jsonStr)
                    if (!packs.isNullOrEmpty()) {
                        Log.d(TAG, "Successfully loaded ${packs.size} sticker packs from catalog")
                        cachedPacks = packs
                        return@withContext packs
                    }
                }
            } else {
                Log.w(TAG, "Catalog request returned HTTP code ${response.code}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load catalog.json from server; only local packs will be shown", e)
        }

        // No remote catalog available: return empty so the panel shows only the
        // on-device packs (Panalink default pack, saved, favorites, recents).
        val defaultPacks = emptyList<StickerPack>()
        cachedPacks = defaultPacks
        defaultPacks
    }
}
