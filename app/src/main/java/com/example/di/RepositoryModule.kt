package com.example.di

import android.content.Context
import com.example.feature.auth.data.SessionRepository
import com.example.feature.settings.data.PrivacyRepository
import com.example.feature.settings.data.SecurityRepository
import com.example.feature.settings.data.PresenceSettingsRepository
import com.example.feature.settings.data.ActivityRepository
import com.example.feature.settings.data.ChatsSettingsRepository
import com.example.feature.settings.data.CustomizationRepository
import com.example.feature.settings.data.NotificationSettingsRepository

object RepositoryModule {
    fun provideSessionRepository(context: Context): SessionRepository = SessionRepository(context)
    fun providePrivacyRepository(context: Context): PrivacyRepository = PrivacyRepository(context)
    fun provideSecurityRepository(context: Context): SecurityRepository = SecurityRepository(context)
    fun providePresenceSettingsRepository(context: Context): PresenceSettingsRepository = PresenceSettingsRepository(context)
    fun provideActivityRepository(context: Context): ActivityRepository = ActivityRepository(context)
    fun provideChatsSettingsRepository(context: Context): ChatsSettingsRepository = ChatsSettingsRepository(context)
    fun provideCustomizationRepository(context: Context): CustomizationRepository = CustomizationRepository(context)
    fun provideNotificationSettingsRepository(context: Context): NotificationSettingsRepository = NotificationSettingsRepository(context)
}
