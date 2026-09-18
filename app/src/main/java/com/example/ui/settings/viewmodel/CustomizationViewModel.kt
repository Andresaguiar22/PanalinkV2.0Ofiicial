package com.example.ui.settings.viewmodel

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.supabase.SupabaseClient
import com.example.feature.settings.data.CustomizationRepository
import com.example.feature.settings.model.CustomizationAction
import com.example.feature.settings.model.CustomizationUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CustomizationViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CustomizationRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(CustomizationUiState())
    val uiState: StateFlow<CustomizationUiState> = _uiState.asStateFlow()

    private val currentUid: String
        get() = SupabaseClient.currentUser?.id ?: ""

    init {
        loadCustomization()
    }

    fun loadCustomization() {
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = repository.loadCustomization(currentUid)
            _uiState.value = loaded
            // Mirror the persisted values into ThemeManager so the whole app
            // is already styled when this screen opens (MainActivity only runs this
            // once at process start; hot configurations channel through here too).
            com.example.ui.theme.ThemeManager.themeMode.value = loaded.themeMode
            com.example.ui.theme.ThemeManager.themeKey.value = loaded.profileThemeChoice
            com.example.ui.theme.ThemeManager.bottomBarColorPreset.value = loaded.bottomBarColorChoice
            com.example.ui.theme.ThemeManager.bottomBarShapePreset.value = loaded.bottomBarShapeChoice
            com.example.ui.theme.ThemeManager.isMinimalistMode.value = loaded.isMinimalistMode
            com.example.ui.theme.ThemeManager.customPrimary.value = Color(
                android.graphics.Color.rgb(loaded.customR, loaded.customG, loaded.customB)
            )
            com.example.ui.theme.ThemeManager.customSecondary.value = Color(
                android.graphics.Color.rgb(loaded.customSecR, loaded.customSecG, loaded.customSecB)
            )
        }
    }

    fun dispatch(action: CustomizationAction) {
        when (action) {
            is CustomizationAction.SetThemeMode -> {
                _uiState.update { it.copy(themeMode = action.mode) }
                com.example.ui.theme.ThemeManager.themeMode.value = action.mode
                viewModelScope.launch(Dispatchers.IO) {
                    repository.saveThemeMode(action.mode)
                }
            }
            is CustomizationAction.SetProfileTheme -> {
                _uiState.update { it.copy(profileThemeChoice = action.theme) }
                com.example.ui.theme.ThemeManager.themeKey.value = action.theme
                viewModelScope.launch(Dispatchers.IO) {
                    repository.saveProfileTheme(currentUid, action.theme)
                }
            }
            is CustomizationAction.SetBottomBarColor -> {
                _uiState.update { it.copy(bottomBarColorChoice = action.preset) }
                // Live-apply so the bottom bar reacts instantly, not only after restart.
                com.example.ui.theme.ThemeManager.bottomBarColorPreset.value = action.preset
                viewModelScope.launch(Dispatchers.IO) {
                    repository.saveBottomBarPreset(action.preset, _uiState.value.bottomBarShapeChoice)
                }
            }
            is CustomizationAction.SetBottomBarShape -> {
                _uiState.update { it.copy(bottomBarShapeChoice = action.preset) }
                com.example.ui.theme.ThemeManager.bottomBarShapePreset.value = action.preset
                viewModelScope.launch(Dispatchers.IO) {
                    repository.saveBottomBarPreset(_uiState.value.bottomBarColorChoice, action.preset)
                }
            }
            is CustomizationAction.UpdateCustomPrimary -> {
                _uiState.update { it.copy(customR = action.r, customG = action.g, customB = action.b) }
                com.example.ui.theme.ThemeManager.customPrimary.value = Color(android.graphics.Color.rgb(action.r, action.g, action.b))
                viewModelScope.launch(Dispatchers.IO) {
                    repository.saveCustomPrimary(action.r, action.g, action.b)
                }
            }
            is CustomizationAction.UpdateCustomSecondary -> {
                _uiState.update { it.copy(customSecR = action.r, customSecG = action.g, customSecB = action.b) }
                com.example.ui.theme.ThemeManager.customSecondary.value = Color(android.graphics.Color.rgb(action.r, action.g, action.b))
                viewModelScope.launch(Dispatchers.IO) {
                    repository.saveCustomSecondary(action.r, action.g, action.b)
                }
            }
            is CustomizationAction.SetMinimalistMode -> {
                _uiState.update { it.copy(isMinimalistMode = action.enabled) }
                com.example.ui.theme.ThemeManager.isMinimalistMode.value = action.enabled
                viewModelScope.launch(Dispatchers.IO) {
                    repository.saveMinimalistMode(action.enabled)
                }
            }
        }
    }
}
