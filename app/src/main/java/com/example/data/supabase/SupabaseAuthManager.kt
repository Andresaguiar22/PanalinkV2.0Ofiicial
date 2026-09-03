package com.example.data.supabase

import com.example.data.model.AuthUser
import com.example.data.model.Profile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object SupabaseAuthManager {
    var currentToken: String? = null
    var currentRefreshToken: String? = null
    var currentUser: AuthUser? = null
    var currentProfile: Profile? = null
        set(value) {
            field = value
            currentProfileState.value = value
        }
    
    val currentProfileState = MutableStateFlow<Profile?>(null)
}
