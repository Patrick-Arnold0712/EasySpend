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

enum class ApiProviderType(
    val label: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val isGeminiNative: Boolean
) {
    DEEPSEEK(
        label = "DeepSeek",
        defaultBaseUrl = "https://api.deepseek.com/v1",
        defaultModel = "deepseek-chat",
        isGeminiNative = false
    ),
    QWEN(
        label = "通义千问",
        defaultBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        defaultModel = "qwen-plus",
        isGeminiNative = false
    ),
    OPENAI_COMPAT(
        label = "OpenAI 兼容",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o-mini",
        isGeminiNative = false
    ),
    GEMINI_REST(
        label = "Gemini API",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
        defaultModel = "gemini-2.5-flash",
        isGeminiNative = true
    )
}

data class UserSettings(
    val activeMonthKey: String = "2026-11",
    val dailyReviewHour: Int = 21,
    val dailyReviewMinute: Int = 30,
    val aiCloudAnalysisEnabled: Boolean = true,
    val notificationCaptureEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val apiProvider: ApiProviderType = ApiProviderType.DEEPSEEK,
    val apiBaseUrl: String = ApiProviderType.DEEPSEEK.defaultBaseUrl,
    val apiKey: String = "",
    val apiModel: String = ApiProviderType.DEEPSEEK.defaultModel
) {
    val isApiConfigured: Boolean
        get() = apiKey.isNotBlank() && apiBaseUrl.isNotBlank() && apiModel.isNotBlank()
}

class UserSettingsDataStore(private val context: Context) {

    private object Keys {
        val ACTIVE_MONTH = stringPreferencesKey("active_month")
        val REVIEW_HOUR = intPreferencesKey("review_hour")
        val REVIEW_MINUTE = intPreferencesKey("review_minute")
        val AI_CLOUD_ENABLED = booleanPreferencesKey("ai_cloud_enabled")
        val NOTIFICATION_CAPTURE_ENABLED = booleanPreferencesKey("notification_capture_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val API_PROVIDER = stringPreferencesKey("api_provider")
        val API_BASE_URL = stringPreferencesKey("api_base_url")
        val API_KEY = stringPreferencesKey("api_key")
        val API_MODEL = stringPreferencesKey("api_model")
    }

    val settingsFlow: Flow<UserSettings> = context.settingsDataStore.data.map { prefs ->
        val rawTheme = prefs[Keys.THEME_MODE]
        val parsedTheme = runCatching { if (rawTheme != null) ThemeMode.valueOf(rawTheme) else ThemeMode.SYSTEM }
            .getOrDefault(ThemeMode.SYSTEM)
        val rawProvider = prefs[Keys.API_PROVIDER]
        val parsedProvider = runCatching {
            if (rawProvider != null) ApiProviderType.valueOf(rawProvider) else ApiProviderType.DEEPSEEK
        }.getOrDefault(ApiProviderType.DEEPSEEK)

        UserSettings(
            activeMonthKey = prefs[Keys.ACTIVE_MONTH] ?: "2026-11",
            dailyReviewHour = prefs[Keys.REVIEW_HOUR] ?: 21,
            dailyReviewMinute = prefs[Keys.REVIEW_MINUTE] ?: 30,
            aiCloudAnalysisEnabled = prefs[Keys.AI_CLOUD_ENABLED] ?: true,
            notificationCaptureEnabled = prefs[Keys.NOTIFICATION_CAPTURE_ENABLED] ?: true,
            themeMode = parsedTheme,
            apiProvider = parsedProvider,
            apiBaseUrl = prefs[Keys.API_BASE_URL]?.ifBlank { parsedProvider.defaultBaseUrl } ?: parsedProvider.defaultBaseUrl,
            apiKey = prefs[Keys.API_KEY] ?: "",
            apiModel = prefs[Keys.API_MODEL]?.ifBlank { parsedProvider.defaultModel } ?: parsedProvider.defaultModel
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

    suspend fun saveApiConfig(
        provider: ApiProviderType,
        baseUrl: String,
        apiKey: String,
        model: String
    ) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.API_PROVIDER] = provider.name
            prefs[Keys.API_BASE_URL] = baseUrl.trim().trimEnd('/')
            prefs[Keys.API_KEY] = apiKey.trim()
            prefs[Keys.API_MODEL] = model.trim()
        }
    }
}
