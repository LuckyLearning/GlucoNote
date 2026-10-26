package com.gluconote.data.repository

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.gluconote.data.model.Settings
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
    }

    val settingsFlow: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            fastingMin = prefs[Keys.FASTING_MIN] ?: 4.4f,
            fastingMax = prefs[Keys.FASTING_MAX] ?: 7.0f,
            postMin = prefs[Keys.POST_MIN] ?: 4.4f,
            postMax = prefs[Keys.POST_MAX] ?: 10.0f,
            lowThreshold = prefs[Keys.LOW] ?: 3.9f
        )
    }

    suspend fun save(settings: Settings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FASTING_MIN] = settings.fastingMin
            prefs[Keys.FASTING_MAX] = settings.fastingMax
            prefs[Keys.POST_MIN] = settings.postMin
            prefs[Keys.POST_MAX] = settings.postMax
            prefs[Keys.LOW] = settings.lowThreshold
        }
    }
}
