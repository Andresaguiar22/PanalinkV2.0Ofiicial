package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.data.repository.CdnManager

/**
 * Resolves media URLs off the Compose/Main thread. The synchronous resolver is
 * intentionally not used here because VCDN resolution may perform blocking I/O.
 */
@Composable
fun rememberAsyncMediaUrl(rawUrl: String?): String {
    val raw = rawUrl?.trim().orEmpty()
    var resolvedUrl by remember(raw) { mutableStateOf("") }

    LaunchedEffect(raw) {
        resolvedUrl = if (raw.isBlank()) {
            ""
        } else {
            runCatching { CdnManager.resolveMediaUrl(raw) }.getOrDefault("")
        }
    }

    return resolvedUrl
}
