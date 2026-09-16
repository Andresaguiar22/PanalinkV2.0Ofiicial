package com.example.live.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.identity.bridge.LegacyIdentityBridge
import com.example.identity.memory.IdentityMemoryCache
import com.example.identity.model.IdentityUiState
import com.example.identity.model.toIdentityUiState

/**
 * Observa la identidad (nombre + avatar) de un usuario para el módulo Live,
 * partiendo de la caché en memoria para pintar algo inmediatamente.
 */
@Composable
internal fun rememberLiveIdentity(userId: String): IdentityUiState? {
    val context = LocalContext.current
    val bridge = remember(context) { LegacyIdentityBridge(context) }
    val state = produceState(
        initialValue = IdentityMemoryCache.profiles[userId]?.toIdentityUiState(),
        key1 = userId
    ) {
        if (userId.isBlank()) {
            value = null
        } else {
            bridge.identityRepository.observeIdentity(userId).collect { value = it }
        }
    }
    return state.value
}

internal fun IdentityUiState?.displayNameOr(userId: String): String =
    this?.displayName?.takeIf { it.isNotBlank() } ?: userId.take(8)
