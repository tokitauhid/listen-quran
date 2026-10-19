package com.listenquran.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.listenquran.domain.model.AudioQuality
import com.listenquran.domain.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AUDIO_QUALITY = stringPreferencesKey("audio_quality")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val LAST_RECITER_ID = intPreferencesKey("last_reciter_id")
        val LAST_SURAH_NUMBER = intPreferencesKey("last_surah_number")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val HIFZ_REPEAT_COUNT = intPreferencesKey("hifz_repeat_count")
        val HIFZ_DELAY_SECONDS = intPreferencesKey("hifz_delay_seconds")
        val WORD_BY_WORD_ENABLED = booleanPreferencesKey("word_by_word_enabled")
        val TRANSLATION_ENABLED = booleanPreferencesKey("translation_enabled")
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            when (preferences[PreferencesKeys.THEME_MODE]) {
                ThemeMode.LIGHT.name -> ThemeMode.LIGHT
                ThemeMode.AMOLED.name -> ThemeMode.AMOLED
                ThemeMode.SYSTEM.name -> ThemeMode.SYSTEM
                else -> ThemeMode.DARK // Dark is the project default
            }
        }

    val audioQuality: Flow<AudioQuality> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences ->
            if (preferences[PreferencesKeys.AUDIO_QUALITY] == AudioQuality.HIGH.name) {
                AudioQuality.HIGH
            } else {
                AudioQuality.STANDARD
            }
        }

    val playbackSpeed: Flow<Float> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[PreferencesKeys.PLAYBACK_SPEED] ?: 1.0f }

    val lastReciterId: Flow<Int> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[PreferencesKeys.LAST_RECITER_ID] ?: 7 } // Default: Mishary Rashid Al-Afasy

    val lastSurahNumber: Flow<Int> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[PreferencesKeys.LAST_SURAH_NUMBER] ?: 1 }

    val notificationsEnabled: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] ?: true }

    val hifzRepeatCount: Flow<Int> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[PreferencesKeys.HIFZ_REPEAT_COUNT] ?: 3 }

    val hifzDelaySeconds: Flow<Int> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[PreferencesKeys.HIFZ_DELAY_SECONDS] ?: 3 }

    val wordByWordEnabled: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[PreferencesKeys.WORD_BY_WORD_ENABLED] ?: false }

    val translationEnabled: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[PreferencesKeys.TRANSLATION_ENABLED] ?: false }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.name
        }
    }

    suspend fun setAudioQuality(quality: AudioQuality) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUDIO_QUALITY] = quality.name
        }
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PLAYBACK_SPEED] = speed
        }
    }

    suspend fun setLastReciterId(reciterId: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_RECITER_ID] = reciterId
        }
    }

    suspend fun setLastSurahNumber(surahNumber: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_SURAH_NUMBER] = surahNumber
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun setHifzLoop(repeatCount: Int, delaySeconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HIFZ_REPEAT_COUNT] = repeatCount
            preferences[PreferencesKeys.HIFZ_DELAY_SECONDS] = delaySeconds
        }
    }

    suspend fun setWordByWordEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.WORD_BY_WORD_ENABLED] = enabled
        }
    }

    suspend fun setTranslationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TRANSLATION_ENABLED] = enabled
        }
    }
}
