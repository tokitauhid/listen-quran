package com.listenquran.update

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.updatePrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_update_prefs")

data class UpdateCacheSnapshot(
    val eTag: String? = null,
    val cachedReleaseJson: String? = null,
    val lastCheckedAtMs: Long = 0L,
    val dismissedTag: String? = null,
    val autoCheckEnabled: Boolean = true,
)

@Singleton
class UpdatePreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val ETAG = stringPreferencesKey("last_etag")
        val CACHED_RELEASE_JSON = stringPreferencesKey("cached_release_json")
        val LAST_CHECKED_AT_MS = longPreferencesKey("last_checked_at_ms")
        val DISMISSED_TAG = stringPreferencesKey("dismissed_tag")
        val AUTO_CHECK_ENABLED = booleanPreferencesKey("auto_check_enabled")
    }

    val snapshotFlow: Flow<UpdateCacheSnapshot> = context.updatePrefsDataStore.data.map { prefs ->
        UpdateCacheSnapshot(
            eTag = prefs[Keys.ETAG],
            cachedReleaseJson = prefs[Keys.CACHED_RELEASE_JSON],
            lastCheckedAtMs = prefs[Keys.LAST_CHECKED_AT_MS] ?: 0L,
            dismissedTag = prefs[Keys.DISMISSED_TAG],
            autoCheckEnabled = prefs[Keys.AUTO_CHECK_ENABLED] ?: true,
        )
    }

    suspend fun getSnapshot(): UpdateCacheSnapshot = runCatching { snapshotFlow.first() }.getOrDefault(UpdateCacheSnapshot())

    suspend fun saveCheckResult(
        eTag: String?,
        releaseJson: String?,
        checkedAtMs: Long,
    ) {
        runCatching {
            context.updatePrefsDataStore.edit { prefs ->
                if (!eTag.isNullOrBlank()) prefs[Keys.ETAG] = eTag
                if (!releaseJson.isNullOrBlank()) prefs[Keys.CACHED_RELEASE_JSON] = releaseJson
                prefs[Keys.LAST_CHECKED_AT_MS] = checkedAtMs
            }
        }
    }

    suspend fun updateCheckedTimestamp(checkedAtMs: Long) {
        runCatching {
            context.updatePrefsDataStore.edit { prefs ->
                prefs[Keys.LAST_CHECKED_AT_MS] = checkedAtMs
            }
        }
    }

    suspend fun dismissTag(tag: String) {
        runCatching {
            context.updatePrefsDataStore.edit { prefs ->
                prefs[Keys.DISMISSED_TAG] = tag
            }
        }
    }

    suspend fun setAutoCheckEnabled(enabled: Boolean) {
        runCatching {
            context.updatePrefsDataStore.edit { prefs ->
                prefs[Keys.AUTO_CHECK_ENABLED] = enabled
            }
        }
    }
}
