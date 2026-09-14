package com.example.feature.chat.ui.background

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Almacén reactivo de personalización del chat (fondos + paletas de burbuja).
 * Persiste en SharedPreferences con un StateFlow que permite recomposición
 * inmediata de la UI sin reiniciar la app.
 */
object ChatPersonalizationStore {

    private const val PREF_NAME = "chat_personalization"
    private const val KEY_WALLPAPER_PREFIX = "wallpaper_chat_"
    private const val KEY_WALLPAPER_CUSTOM_PREFIX = "wallpaper_custom_chat_"
    private const val KEY_BUBBLE_PALETTE_PREFIX = "bubble_palette_chat_"

    private data class PerChat(
        var wallpaper: String?,
        var wallpaperCustomUri: String?,
        var paletteId: String?
    )

    private val cache = mutableMapOf<String, PerChat>()
    private val flows = mutableMapOf<String, MutableStateFlow<ChatPersonalization>>()
    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        }
    }

    private fun prefsOf(context: Context): android.content.SharedPreferences {
        init(context)
        return prefs!!
    }

    private fun perChat(context: Context, key: String): PerChat {
        val prefs = prefsOf(context)
        return cache.getOrPut(key) {
            PerChat(
                wallpaper = prefs.getString("$KEY_WALLPAPER_PREFIX$key", null),
                wallpaperCustomUri = prefs.getString("$KEY_WALLPAPER_CUSTOM_PREFIX$key", null),
                paletteId = prefs.getString("$KEY_BUBBLE_PALETTE_PREFIX$key", null)
            )
        }
    }

    private fun cacheKey(currentUserId: String, chatId: String) = "$currentUserId::$chatId"

    /** Flow reactivo del estado de personalización para un chat concreto. */
    fun observePersonalization(
        context: Context,
        currentUserId: String,
        chatId: String
    ): StateFlow<ChatPersonalization> {
        init(context)
        val key = cacheKey(currentUserId, chatId)
        return flows.getOrPut(key) {
            MutableStateFlow(load(context, currentUserId, chatId))
        }.asStateFlow()
    }

    private fun load(context: Context, currentUserId: String, chatId: String): ChatPersonalization {
        val data = perChat(context, cacheKey(currentUserId, chatId))
        return ChatPersonalization(
            wallpaperId = data.wallpaper ?: ChatWallpaperSpec.DEFAULT_ACTIVE,
            wallpaperCustomUri = data.wallpaperCustomUri,
            bubblePalette = ChatBubblePalette.fromId(data.paletteId)
        )
    }

    /** Guarda el fondo elegido y actualiza el flow reactivo. */
    fun setWallpaper(
        context: Context,
        currentUserId: String,
        chatId: String,
        spec: ChatWallpaperSpec
    ) {
        init(context)
        val key = cacheKey(currentUserId, chatId)
        val editor = prefsOf(context).edit()
        when (spec) {
            is ChatWallpaperSpec.Custom -> {
                editor.putString("$KEY_WALLPAPER_PREFIX$key", spec.id)
                editor.putString("$KEY_WALLPAPER_CUSTOM_PREFIX$key", spec.uri)
            }
            else -> {
                editor.putString("$KEY_WALLPAPER_PREFIX$key", spec.id)
                if (spec !is ChatWallpaperSpec.Remote) {
                    editor.remove("$KEY_WALLPAPER_CUSTOM_PREFIX$key")
                }
            }
        }
        editor.apply()
        perChat(context, key).let {
            it.wallpaper = when (spec) {
                is ChatWallpaperSpec.Custom -> spec.id
                else -> spec.id
            }
            it.wallpaperCustomUri = if (spec is ChatWallpaperSpec.Custom) spec.uri else null
        }
        flows[key]?.value = load(context, currentUserId, chatId)
    }

    /** Guarda la paleta de burbuja saliente y actualiza el flow reactivo. */
    fun setBubblePalette(
        context: Context,
        currentUserId: String,
        chatId: String,
        palette: ChatBubblePalette
    ) {
        init(context)
        val key = cacheKey(currentUserId, chatId)
        prefsOf(context).edit().putString("$KEY_BUBBLE_PALETTE_PREFIX$key", palette.id).apply()
        perChat(context, key).paletteId = palette.id
        flows[key]?.value = load(context, currentUserId, chatId)
    }
}

data class ChatPersonalization(
    val wallpaperId: String = ChatWallpaperSpec.DEFAULT_ACTIVE,
    val wallpaperCustomUri: String? = null,
    val bubblePalette: ChatBubblePalette = ChatBubblePalette.PANALINK_BLUE
)