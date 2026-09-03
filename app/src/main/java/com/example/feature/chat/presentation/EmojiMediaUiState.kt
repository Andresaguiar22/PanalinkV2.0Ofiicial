package com.example.feature.chat.presentation

import androidx.compose.runtime.Immutable
import com.example.data.model.StickerResult

@Immutable
data class EmojiMediaUiState(
    val selectedTab: Int = 0, // 0 = Emoji, 1 = GIF, 2 = Sticker
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val selectedStickerCategory: String = "trends",
    val stickers: List<StickerResult> = emptyList(),
    val isStickersLoading: Boolean = false,
    val gifs: List<StickerResult> = emptyList(),
    val isGifsLoading: Boolean = false
)