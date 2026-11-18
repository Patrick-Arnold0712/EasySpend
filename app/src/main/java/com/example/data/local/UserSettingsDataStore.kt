package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "easyspend_settings")

enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色模式"),
    DARK("深色模式")
}

data class UserSettings(
    val activeMonthKey: String = "2026-11",
    val dailyReviewHour: Int = 21,
    val dailyReviewMinute: Int = 30,
    val aiCloudAnalysisEnabled: Boolean = true,
    val notificationCaptureEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)

class UserSettingsDataStore(private val context: Context) {

    private object Keys {
        val ACTIVE_MONTH = stringPreferencesKey("active_month")
        val REVIEW_HOUR = intPreferencesKey("review_hour")
        val REVIEW_MINUTE = intPreferencesKey("review_minute")
        val AI_CLOUD_ENABLED = booleanPreferencesKey("ai_cloud_enabled")
        val NOTIFICATION_CAPTURE_ENABLED = booleanPreferencesKey("notification_capture_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val settingsFlow: Flow<UserSettings> = context.settingsDataStore.data.map { prefs ->
        val rawTheme = prefs[Keys.THEME_MODE]
        val parsedTheme = runCatching { if (rawTheme != null) ThemeMode.valueOf(rawTheme) else ThemeMode.SYSTEM }
            .getOrDefault(ThemeMode.SYSTEM)
        UserSettings(
            activeMonthKey = prefs[Keys.ACTIVE_MONTH] ?: "2026-11",
            dailyReviewHour = prefs[Keys.REVIEW_HOUR] ?: 21,
            dailyReviewMinute = prefs[Keys.REVIEW_MINUTE] ?: 30,
            aiCloudAnalysisEnabled = prefs[Keys.AI_CLOUD_ENABLED] ?: true,
            notificationCaptureEnabled = prefs[Keys.NOTIFICATION_CAPTURE_ENABLED] ?: true,
            themeMode = parsedTheme
        )
    }

    suspend fun setDailyReviewTime(hour: Int, minute: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.REVIEW_HOUR] = hour.coerceIn(0, 23)
            prefs[Keys.REVIEW_MINUTE] = minute.coerceIn(0, 59)
        }
    }

    suspend fun setAiCloudAnalysisEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.AI_CLOUD_ENABLED] = enabled
        }
    }

    suspend fun setNotificationCaptureEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.NOTIFICATION_CAPTURE_ENABLED] = enabled
        }
    }

    suspend fun setActiveMonth(monthKey: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.ACTIVE_MONTH] = monthKey
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }
}
