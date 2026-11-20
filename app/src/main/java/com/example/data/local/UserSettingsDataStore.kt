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
    val backgroundKeepAliveEnabled: Boolean = true,
    val locationClassificationEnabled: Boolean = true,
    val detailedModeEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val hideBeginnerGuide: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val apiProvider: ApiProviderType = ApiProviderType.DEEPSEEK,
    val apiBaseUrl: String = ApiProviderType.DEEPSEEK.defaultBaseUrl,
    val apiKey: String = "",
    val apiModel: String = ApiProviderType.DEEPSEEK.defaultModel
) {
    val isApiConfigured: Boolean
        get() = apiKey.isNotBlank() && apiBaseUrl.isNotBlank() && apiModel.isNotBlank()
}

class UserSettingsDataStore(
    private val context: Context,
    private val accountId: String? = null
) {

    private fun prefix(key: String): String =
        if (accountId.isNullOrBlank()) key else "acct_${accountId}_$key"

    private val activeMonthKey = stringPreferencesKey(prefix("active_month"))
    private val reviewHourKey = intPreferencesKey(prefix("review_hour"))
    private val reviewMinuteKey = intPreferencesKey(prefix("review_minute"))
    private val aiCloudEnabledKey = booleanPreferencesKey(prefix("ai_cloud_enabled"))
    private val notificationCaptureEnabledKey = booleanPreferencesKey(prefix("notification_capture_enabled"))
    private val backgroundKeepAliveEnabledKey = booleanPreferencesKey(prefix("background_keep_alive_enabled"))
    private val locationClassificationEnabledKey = booleanPreferencesKey(prefix("location_classification_enabled"))
    private val detailedModeEnabledKey = booleanPreferencesKey(prefix("detailed_mode_enabled"))
    private val themeModeKey = stringPreferencesKey(prefix("theme_mode"))
    private val hideBeginnerGuideKey = booleanPreferencesKey(prefix("hide_beginner_guide"))
    private val onboardingCompletedKey = booleanPreferencesKey(prefix("onboarding_completed"))
    private val apiProviderKey = stringPreferencesKey(prefix("api_provider"))
    private val apiBaseUrlKey = stringPreferencesKey(prefix("api_base_url"))
    private val apiKeyPref = stringPreferencesKey(prefix("api_key"))
    private val apiModelKey = stringPreferencesKey(prefix("api_model"))

    val settingsFlow: Flow<UserSettings> = context.settingsDataStore.data.map { prefs ->
        val rawTheme = prefs[themeModeKey]
        val parsedTheme = runCatching { if (rawTheme != null) ThemeMode.valueOf(rawTheme) else ThemeMode.SYSTEM }
            .getOrDefault(ThemeMode.SYSTEM)
        val rawProvider = prefs[apiProviderKey]
        val parsedProvider = runCatching {
            if (rawProvider != null) ApiProviderType.valueOf(rawProvider) else ApiProviderType.DEEPSEEK
        }.getOrDefault(ApiProviderType.DEEPSEEK)

        UserSettings(
            activeMonthKey = prefs[activeMonthKey] ?: "2026-11",
            dailyReviewHour = prefs[reviewHourKey] ?: 21,
            dailyReviewMinute = prefs[reviewMinuteKey] ?: 30,
            aiCloudAnalysisEnabled = prefs[aiCloudEnabledKey] ?: true,
            notificationCaptureEnabled = prefs[notificationCaptureEnabledKey] ?: true,
            backgroundKeepAliveEnabled = prefs[backgroundKeepAliveEnabledKey] ?: true,
            locationClassificationEnabled = prefs[locationClassificationEnabledKey] ?: true,
            detailedModeEnabled = prefs[detailedModeEnabledKey] ?: true,
            themeMode = parsedTheme,
            hideBeginnerGuide = prefs[hideBeginnerGuideKey] ?: false,
            onboardingCompleted = prefs[onboardingCompletedKey] ?: false,
            apiProvider = parsedProvider,
            apiBaseUrl = prefs[apiBaseUrlKey]?.ifBlank { parsedProvider.defaultBaseUrl } ?: parsedProvider.defaultBaseUrl,
            apiKey = prefs[apiKeyPref] ?: "",
            apiModel = prefs[apiModelKey]?.ifBlank { parsedProvider.defaultModel } ?: parsedProvider.defaultModel
        )
    }

    suspend fun setDailyReviewTime(hour: Int, minute: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[reviewHourKey] = hour.coerceIn(0, 23)
            prefs[reviewMinuteKey] = minute.coerceIn(0, 59)
        }
    }

    suspend fun setAiCloudAnalysisEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[aiCloudEnabledKey] = enabled
        }
    }

    suspend fun setNotificationCaptureEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[notificationCaptureEnabledKey] = enabled
        }
    }

    suspend fun setBackgroundKeepAliveEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[backgroundKeepAliveEnabledKey] = enabled
        }
    }

    suspend fun setLocationClassificationEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[locationClassificationEnabledKey] = enabled
        }
    }

    suspend fun setDetailedModeEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[detailedModeEnabledKey] = enabled
        }
    }

    suspend fun setActiveMonth(monthKey: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[activeMonthKey] = monthKey
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { prefs ->
            prefs[themeModeKey] = mode.name
        }
    }

    suspend fun setHideBeginnerGuide(hidden: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[hideBeginnerGuideKey] = hidden
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[onboardingCompletedKey] = completed
        }
    }

    suspend fun saveApiConfig(
        provider: ApiProviderType,
        baseUrl: String,
        apiKey: String,
        model: String
    ) {
        context.settingsDataStore.edit { prefs ->
            prefs[apiProviderKey] = provider.name
            prefs[apiBaseUrlKey] = baseUrl.trim().trimEnd('/')
            prefs[apiKeyPref] = apiKey.trim()
            prefs[apiModelKey] = model.trim()
        }
    }
}
