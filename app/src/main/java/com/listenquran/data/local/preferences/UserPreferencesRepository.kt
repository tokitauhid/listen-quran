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

data class SettingsPreferences(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val defaultReciterId: Int = 7,
    val playbackSpeed: Float = 1.0f,
    val wordByWordEnabled: Boolean = false,
    val translationEnabled: Boolean = false,
    val hifzRepeatCount: Int = 3,
    val hifzDelaySeconds: Int = 3,
    val customSleepTimerMinutes: Int = 20,
    val dailyReminderEnabled: Boolean = true,
    val dailyReminderHour: Int = 20,
    val dailyReminderMinute: Int = 0,
    val fridayReminderEnabled: Boolean = true,
    val fridayReminderHour: Int = 8,
    val fridayReminderMinute: Int = 0,
    val statsDetailsExpanded: Boolean = false,
    val lastBackupTimestamp: Long = 0L,
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AUDIO_QUALITY = stringPreferencesKey("audio_quality")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val LAST_RECITER_ID = intPreferencesKey("last_reciter_id")
        val DEFAULT_RECITER_ID = intPreferencesKey("default_reciter_id")
        val LAST_SURAH_NUMBER = intPreferencesKey("last_surah_number")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val DAILY_REMINDER_ENABLED = booleanPreferencesKey("daily_reminder_enabled")
        val DAILY_REMINDER_HOUR = intPreferencesKey("daily_reminder_hour")
        val DAILY_REMINDER_MINUTE = intPreferencesKey("daily_reminder_minute")
        val FRIDAY_REMINDER_ENABLED = booleanPreferencesKey("friday_reminder_enabled")
        val FRIDAY_REMINDER_HOUR = intPreferencesKey("friday_reminder_hour")
        val FRIDAY_REMINDER_MINUTE = intPreferencesKey("friday_reminder_minute")
        val HIFZ_REPEAT_COUNT = intPreferencesKey("hifz_repeat_count")
        val HIFZ_DELAY_SECONDS = intPreferencesKey("hifz_delay_seconds")
        val WORD_BY_WORD_ENABLED = booleanPreferencesKey("word_by_word_enabled")
        val TRANSLATION_ENABLED = booleanPreferencesKey("translation_enabled")
        val CUSTOM_SLEEP_TIMER_MINUTES = intPreferencesKey("custom_sleep_timer_minutes")
        val STATS_DETAILS_EXPANDED = booleanPreferencesKey("stats_details_expanded")
        val LAST_BACKUP_TIMESTAMP = longPreferencesKey("last_backup_timestamp")
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
                else -> ThemeMode.DARK // Dark is project default
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
        .map { preferences -> preferences[PreferencesKeys.DEFAULT_RECITER_ID] ?: preferences[PreferencesKeys.LAST_RECITER_ID] ?: 7 }

    val lastSurahNumber: Flow<Int> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[PreferencesKeys.LAST_SURAH_NUMBER] ?: 1 }

    val notificationsEnabled: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] ?: true }

    val dailyReminderEnabled: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[PreferencesKeys.DAILY_REMINDER_ENABLED] ?: true }

    val dailyReminderHour: Flow<Int> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[PreferencesKeys.DAILY_REMINDER_HOUR] ?: 20 }

    val dailyReminderMinute: Flow<Int> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[PreferencesKeys.DAILY_REMINDER_MINUTE] ?: 0 }

    val fridayReminderEnabled: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[PreferencesKeys.FRIDAY_REMINDER_ENABLED] ?: true }

    val fridayReminderHour: Flow<Int> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[PreferencesKeys.FRIDAY_REMINDER_HOUR] ?: 8 }

    val fridayReminderMinute: Flow<Int> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[PreferencesKeys.FRIDAY_REMINDER_MINUTE] ?: 0 }

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

    val customSleepTimerMinutes: Flow<Int> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[PreferencesKeys.CUSTOM_SLEEP_TIMER_MINUTES] ?: 20 }

    val statsDetailsExpanded: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[PreferencesKeys.STATS_DETAILS_EXPANDED] ?: false }

    val lastBackupTimestamp: Flow<Long> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[PreferencesKeys.LAST_BACKUP_TIMESTAMP] ?: 0L }

    val settingsPreferences: Flow<SettingsPreferences> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences ->
            val theme = when (preferences[PreferencesKeys.THEME_MODE]) {
                ThemeMode.LIGHT.name -> ThemeMode.LIGHT
                ThemeMode.AMOLED.name -> ThemeMode.AMOLED
                ThemeMode.SYSTEM.name -> ThemeMode.SYSTEM
                else -> ThemeMode.DARK
            }
            SettingsPreferences(
                themeMode = theme,
                defaultReciterId = preferences[PreferencesKeys.DEFAULT_RECITER_ID] ?: preferences[PreferencesKeys.LAST_RECITER_ID] ?: 7,
                playbackSpeed = preferences[PreferencesKeys.PLAYBACK_SPEED] ?: 1.0f,
                wordByWordEnabled = preferences[PreferencesKeys.WORD_BY_WORD_ENABLED] ?: false,
                translationEnabled = preferences[PreferencesKeys.TRANSLATION_ENABLED] ?: false,
                hifzRepeatCount = preferences[PreferencesKeys.HIFZ_REPEAT_COUNT] ?: 3,
                hifzDelaySeconds = preferences[PreferencesKeys.HIFZ_DELAY_SECONDS] ?: 3,
                customSleepTimerMinutes = preferences[PreferencesKeys.CUSTOM_SLEEP_TIMER_MINUTES] ?: 20,
                dailyReminderEnabled = preferences[PreferencesKeys.DAILY_REMINDER_ENABLED] ?: preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] ?: true,
                dailyReminderHour = preferences[PreferencesKeys.DAILY_REMINDER_HOUR] ?: 20,
                dailyReminderMinute = preferences[PreferencesKeys.DAILY_REMINDER_MINUTE] ?: 0,
                fridayReminderEnabled = preferences[PreferencesKeys.FRIDAY_REMINDER_ENABLED] ?: preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] ?: true,
                fridayReminderHour = preferences[PreferencesKeys.FRIDAY_REMINDER_HOUR] ?: 8,
                fridayReminderMinute = preferences[PreferencesKeys.FRIDAY_REMINDER_MINUTE] ?: 0,
                statsDetailsExpanded = preferences[PreferencesKeys.STATS_DETAILS_EXPANDED] ?: false,
                lastBackupTimestamp = preferences[PreferencesKeys.LAST_BACKUP_TIMESTAMP] ?: 0L,
            )
        }

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
            preferences[PreferencesKeys.DEFAULT_RECITER_ID] = reciterId
        }
    }

    suspend fun setDefaultReciterId(reciterId: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_RECITER_ID] = reciterId
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
            preferences[PreferencesKeys.DAILY_REMINDER_ENABLED] = enabled
            preferences[PreferencesKeys.FRIDAY_REMINDER_ENABLED] = enabled
        }
    }

    suspend fun setDailyReminder(enabled: Boolean, hour: Int? = null, minute: Int? = null) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DAILY_REMINDER_ENABLED] = enabled
            if (hour != null) preferences[PreferencesKeys.DAILY_REMINDER_HOUR] = hour
            if (minute != null) preferences[PreferencesKeys.DAILY_REMINDER_MINUTE] = minute
        }
    }

    suspend fun setFridayReminder(enabled: Boolean, hour: Int? = null, minute: Int? = null) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.FRIDAY_REMINDER_ENABLED] = enabled
            if (hour != null) preferences[PreferencesKeys.FRIDAY_REMINDER_HOUR] = hour
            if (minute != null) preferences[PreferencesKeys.FRIDAY_REMINDER_MINUTE] = minute
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

    suspend fun setCustomSleepTimerMinutes(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CUSTOM_SLEEP_TIMER_MINUTES] = minutes
        }
    }

    suspend fun setStatsDetailsExpanded(expanded: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.STATS_DETAILS_EXPANDED] = expanded
        }
    }

    suspend fun setLastBackupTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_BACKUP_TIMESTAMP] = timestamp
        }
    }
}
