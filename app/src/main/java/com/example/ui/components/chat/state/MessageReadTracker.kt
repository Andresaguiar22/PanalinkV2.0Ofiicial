package com.example.ui.components.chat.state

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import com.example.data.model.Message
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

fun normalizeVisibleMessageKey(rawKey: Any?): String? {
    val key = rawKey?.toString()?.trim().orEmpty()
    if (key.isEmpty() || key == "typing_indicator" || key.contains("date_")) return null
    if (key.startsWith("temp_")) return null
    if (key.matches(Regex(".*_\\d+$"))) {
        val trimmed = key.substringBeforeLast("_")
        if (trimmed.isNotBlank() && !trimmed.startsWith("temp_")) return trimmed
    }
    return key
}

@Composable
fun MessageReadTracker(
    lazyListState: LazyListState,
    messages: List<Message>,
    onMessagesVisible: (List<String>) -> Unit
) {
    if (messages.isEmpty()) return

    LaunchedEffect(lazyListState, messages) {
        snapshotFlow {
            val layoutInfo = lazyListState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) emptyList<String>()
            else {
                visibleItems.mapNotNull { item ->
                    normalizeVisibleMessageKey(item.key)
                }
            }
        }
        .distinctUntilChanged()
        .collectLatest { visibleIds ->
            if (visibleIds.isNotEmpty()) {
                onMessagesVisible(visibleIds)
            }
        }
    }
}
