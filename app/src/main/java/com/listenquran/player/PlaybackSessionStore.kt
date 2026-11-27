package com.listenquran.player

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.playbackSessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "playback_session")

@JsonClass(generateAdapter = true)
data class SavedQueueItemDto(
    val mediaId: String,
    val uri: String? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val artist: String? = null,
    val displayTitle: String? = null,
    val clipStartMs: Long = 0L,
    val clipEndMs: Long = -1L,
)

@JsonClass(generateAdapter = true)
data class SavedPlaybackSession(
    val items: List<SavedQueueItemDto>,
    val currentIndex: Int,
    val positionMs: Long,
    val isShuffleEnabled: Boolean,
    val shuffledIndices: List<Int> = emptyList(),
    val repeatMode: Int,
    val playbackSpeed: Float,
    val sourceTitle: String?,
    val lastRadioStationId: String? = null,
    val savedAt: Long,
)

/**
 * Persists the user's active playback queue, index, position, shuffle order, repeat mode,
 * speed, and source label across process death and task removal.
 */
@Singleton
class PlaybackSessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(SavedPlaybackSession::class.java)
    private val sessionKey = stringPreferencesKey("saved_session_json")
    private val lastRadioKey = stringPreferencesKey("last_radio_station_id")

    suspend fun save(session: SavedPlaybackSession) {
        if (session.items.isEmpty()) return
        runCatching {
            val json = adapter.toJson(session)
            context.playbackSessionDataStore.edit { prefs ->
                prefs[sessionKey] = json
            }
        }
    }

    suspend fun load(): SavedPlaybackSession? {
        return runCatching {
            val prefs = context.playbackSessionDataStore.data.first()
            val raw = prefs[sessionKey] ?: return null
            adapter.fromJson(raw)?.takeIf { it.items.isNotEmpty() }
        }.getOrNull()
    }

    suspend fun saveLastRadioStation(stationId: String) {
        runCatching {
            context.playbackSessionDataStore.edit { prefs ->
                prefs[lastRadioKey] = stationId
            }
        }
    }

    suspend fun loadLastRadioStation(): String? {
        return runCatching {
            context.playbackSessionDataStore.data.first()[lastRadioKey]
        }.getOrNull()
    }

    fun serializeToJson(session: SavedPlaybackSession): String = adapter.toJson(session)
    fun deserializeFromJson(json: String): SavedPlaybackSession? = runCatching { adapter.fromJson(json) }.getOrNull()
}
