package com.example.identity.repository

import android.content.Context
import androidx.annotation.Keep
import com.example.data.database.PanalinkDatabase
import com.example.data.repository.CdnManager
import com.example.data.repository.PublicProfileFetchResult
import com.example.data.repository.PublicProfileRepository
import com.example.data.repository.PublicProfileResolver
import com.example.identity.memory.IdentityMemoryCache
import com.example.identity.model.IdentityUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transformLatest

@Keep
class IdentityRepository(context: Context) {
    private val db = PanalinkDatabase.getDatabase(context)
    private val profileDao = db.profileDao()
    private val publicProfileDao = db.publicProfileDao()
    private val publicProfileRepository = PublicProfileRepository.getInstance(context)

    private fun isUsableName(name: String?): Boolean = !PublicProfileResolver.isGenericOrUuid(name)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeIdentity(userId: String): Flow<IdentityUiState?> {
        return profileDao.observeProfile(userId).transformLatest { localEntity ->
            val localNameIsUsable = isUsableName(localEntity?.displayName)
            val localHasAvatar = !localEntity?.avatarUrl.isNullOrEmpty()

            // Emit cached/local identity immediately only when it is a real identity.
            // Generic values such as "Pana" must never win over the public profile.
            if (localEntity != null && (localNameIsUsable || localHasAvatar)) {
                val state = IdentityUiState(
                    userId = localEntity.id,
                    displayName = localEntity.displayName,
                    avatarUrl = localEntity.avatarUrl,
                    avatarLocalPath = localEntity.avatarLocalPath
                )
                IdentityMemoryCache.profiles[userId] = state
                emit(state)
            } else {
                val cached = IdentityMemoryCache.profiles[userId]
                if (cached != null && isUsableName(cached.displayName)) {
                    emit(cached)
                }
            }

            // Always resolve the canonical public identity when the local name is
            // missing/generic, and also refresh profiles that have no usable avatar.
            val mustResolvePublic = !localNameIsUsable || !localHasAvatar
            if (mustResolvePublic) {
                val result = publicProfileRepository.getPublicProfile(userId)
                if (result is PublicProfileFetchResult.Success) {
                    val pub = result.data
                    val resolvedName = PublicProfileResolver.resolveDisplayName(
                        publicProfile = pub,
                        fallbackName = localEntity?.displayName,
                        userId = userId
                    )
                    val state = IdentityUiState(
                        userId = pub.id,
                        displayName = resolvedName,
                        avatarUrl = pub.avatarUrl,
                        avatarLocalPath = localEntity?.avatarLocalPath
                    )
                    IdentityMemoryCache.profiles[userId] = state
                    emit(state)
                    persistPublicProfile(pub, resolvedName, localEntity?.avatarLocalPath)
                } else {
                    // Offline / NetworkError: fall back to whatever public profile Room
                    // already has so the avatar survives a dead connection (no initiales).
                    val roomPub = publicProfileDao.getById(userId)
                    if (roomPub != null && !roomPub.avatarUrl.isNullOrBlank()) {
                        val pubModel = com.example.data.mapper.PublicProfileMapper.entityToModel(roomPub)
                        val resolvedName = PublicProfileResolver.resolveDisplayName(
                            publicProfile = pubModel,
                            fallbackName = localEntity?.displayName,
                            userId = userId
                        )
                        val state = IdentityUiState(
                            userId = pubModel.id,
                            displayName = resolvedName,
                            avatarUrl = pubModel.avatarUrl,
                            avatarLocalPath = localEntity?.avatarLocalPath
                        )
                        IdentityMemoryCache.profiles[userId] = state
                        emit(state)
                    }
                }
            }
        }
    }

    suspend fun getProfile(userId: String): IdentityUiState? {
        val localEntity = profileDao.getProfileById(userId)
        val localNameIsUsable = isUsableName(localEntity?.displayName)
        val localHasAvatar = !localEntity?.avatarUrl.isNullOrEmpty()

        if (localEntity != null && localNameIsUsable && localHasAvatar) {
            return IdentityUiState(
                userId = localEntity.id,
                displayName = localEntity.displayName,
                avatarUrl = localEntity.avatarUrl,
                avatarLocalPath = localEntity.avatarLocalPath
            )
        }

        val result = publicProfileRepository.getPublicProfile(userId)
        if (result is PublicProfileFetchResult.Success) {
            val pub = result.data
            val resolvedName = PublicProfileResolver.resolveDisplayName(
                publicProfile = pub,
                fallbackName = localEntity?.displayName,
                userId = userId
            )
            val resolvedState = IdentityUiState(
                userId = pub.id,
                displayName = resolvedName,
                avatarUrl = pub.avatarUrl,
                avatarLocalPath = localEntity?.avatarLocalPath
            )
            IdentityMemoryCache.profiles[userId] = resolvedState
            persistPublicProfile(pub, resolvedName, localEntity?.avatarLocalPath)
            return resolvedState
        }

        if (localEntity != null && localNameIsUsable) {
            return IdentityUiState(
                userId = localEntity.id,
                displayName = localEntity.displayName,
                avatarUrl = localEntity.avatarUrl,
                avatarLocalPath = localEntity.avatarLocalPath
            )
        }

        val cached = IdentityMemoryCache.profiles[userId]
        return cached?.takeIf { isUsableName(it.displayName) }
    }

    suspend fun saveProfile(state: IdentityUiState) {
        val existing = profileDao.getProfileById(state.userId)
        // Never persist generic placeholders such as "Pana" as a user's canonical
        // name, but do not throw away a usable avatar just because the incoming
        // name is generic (that path silently lost avatars).
        val nameForSave = state.displayName?.takeIf { isUsableName(it) }
            ?: existing?.displayName?.takeIf { isUsableName(it) }
        if (nameForSave == null) return

        if (existing != null) {
            // Un avatar vacio ("" no nulo) NO es un avatar: si se guardara tal cual
            // pisaria el bueno que ya teniamos (el publico), y PanaAvatar caeria a
            // iniciales. Igual para el nombre y la ruta local.
            val merged = existing.copy(
                displayName = state.displayName?.takeIf { isUsableName(it) } ?: existing.displayName,
                avatarUrl = state.avatarUrl?.takeIf { it.isNotBlank() } ?: existing.avatarUrl,
                avatarLocalPath = state.avatarLocalPath?.takeIf { it.isNotBlank() } ?: existing.avatarLocalPath
            )
            profileDao.insertProfile(merged)
            // La cache en memoria alimenta PanaAvatar: guardar el estado fusionado
            // (no el entrante) para que un avatar vacio no deje la inicial.
            IdentityMemoryCache.profiles[state.userId] = IdentityUiState(
                userId = merged.id,
                displayName = merged.displayName,
                avatarUrl = merged.avatarUrl,
                avatarLocalPath = merged.avatarLocalPath
            )
            return
        }
        IdentityMemoryCache.profiles[state.userId] = state
    }

    /**
     * Mirrors a fetched public profile into the local identity cache (local_profiles)
     * to that avatars/names survive cold starts and fully offline opens of any screen.
     */
    /**
     * Resolves the freshest available avatar URL for a user, preferring the
     * public profile's avatar (CDN-resolved) over stale local cache.
     * Used by floating reactions to always show a current avatar.
     */
    suspend fun resolveFreshAvatar(userId: String): String? {
        val localEntity = profileDao.getProfileById(userId)
        val localHasAvatar = !localEntity?.avatarUrl.isNullOrEmpty()
        val mustResolvePublic = !localHasAvatar
        return if (mustResolvePublic) {
            val result = publicProfileRepository.getPublicProfile(userId)
            if (result is PublicProfileFetchResult.Success) {
                val pub = result.data
                CdnManager.resolveAvatarUrl(pub.avatarUrl) ?: localEntity?.avatarUrl
            } else {
                localEntity?.avatarUrl
            }
        } else {
            localEntity?.avatarUrl
        }
    }

    private suspend fun persistPublicProfile(
        pub: com.example.data.model.PublicProfile,
        resolvedName: String,
        avatarLocalPath: String?
    ) {
        if (!isUsableName(resolvedName)) return
        val local = profileDao.getProfileById(pub.id)
        val existingName = local?.displayName
        val finalName = if (!isUsableName(existingName)) resolvedName else existingName
        val finalAvatar = CdnManager.resolveAvatarUrl(pub.avatarUrl) ?: local?.avatarUrl
        runCatching {
            profileDao.insertProfile(
                com.example.data.database.ProfileEntity(
                    id = pub.id,
                    displayName = finalName ?: pub.id,
                    avatarUrl = finalAvatar,
                    profileTheme = local?.profileTheme ?: "dark_teal",
                    avatarLocalPath = avatarLocalPath ?: local?.avatarLocalPath,
                    firstName = pub.firstName ?: local?.firstName,
                    lastName = pub.lastName ?: local?.lastName,
                    updatedAt = local?.updatedAt ?: pub.updatedAt,
                    lastSyncedAt = local?.lastSyncedAt ?: System.currentTimeMillis()
                )
            )
        }.onFailure { e -> android.util.Log.e("IdentityRepository", "Failed to persist public profile for ${pub.id}", e) }
    }
}
