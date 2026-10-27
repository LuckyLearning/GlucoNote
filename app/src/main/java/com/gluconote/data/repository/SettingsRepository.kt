package com.gluconote.data.repository

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.gluconote.data.model.Settings
import com.gluconote.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val FASTING_MIN = floatPreferencesKey("fasting_min")
        val FASTING_MAX = floatPreferencesKey("fasting_max")
        val POST_MIN = floatPreferencesKey("post_min")
        val POST_MAX = floatPreferencesKey("post_max")
        val LOW = floatPreferencesKey("low_threshold")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val settingsFlow: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            fastingMin = prefs[Keys.FASTING_MIN] ?: 4.4f,
            fastingMax = prefs[Keys.FASTING_MAX] ?: 7.0f,
            postMin = prefs[Keys.POST_MIN] ?: 4.4f,
            postMax = prefs[Keys.POST_MAX] ?: 10.0f,
            lowThreshold = prefs[Keys.LOW] ?: 3.9f,
            themeMode = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.key
        )
    }

    /**
     * 保存目标血糖范围。
     * **刻意不写 `theme_mode`** —— 主题有自己的 [saveThemeMode]，两处互不覆盖。
     */
    suspend fun save(settings: Settings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FASTING_MIN] = settings.fastingMin
            prefs[Keys.FASTING_MAX] = settings.fastingMax
            prefs[Keys.POST_MIN] = settings.postMin
            prefs[Keys.POST_MAX] = settings.postMax
            prefs[Keys.LOW] = settings.lowThreshold
        }
    }

    /** 只改主题模式，不动其它设置 */
    suspend fun saveThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs -> prefs[Keys.THEME_MODE] = mode.key }
    }
}
