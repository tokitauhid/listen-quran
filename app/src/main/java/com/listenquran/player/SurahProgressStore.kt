package com.listenquran.player

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.surahProgressDataStore: DataStore<Preferences> by preferencesDataStore(name = "surah_progress")

@Immutable
@JsonClass(generateAdapter = true)
data class SurahResumeProgress(
    val surahId: Int,
    val reciterId: Int,
    val positionMs: Long,
    val durationMs: Long,
    val lastAyahNumber: Int = 1,
    val updatedAt: Long,
) {
    val fraction: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val remainingMinutes: Int
        get() = if (durationMs > positionMs) (((durationMs - positionMs) / 60_000L).toInt()).coerceAtLeast(1) else 0
}

@JsonClass(generateAdapter = true)
data class SurahProgressPayload(
    val entries: List<SurahResumeProgress> = emptyList(),
)

/**
 * Pure threshold & list-maintenance rules for partial-surah resume tracking,
 * exposed cleanly for unit testing as well as DataStore persistence.
 */
object SurahProgressRules {
    const val MIN_RESUME_POSITION_MS = 30_000L
    const val COMPLETION_THRESHOLD = 0.95
    const val MAX_ENTRIES = 50

    fun applyUpdate(
        current: List<SurahResumeProgress>,
        surahId: Int,
        reciterId: Int,
        positionMs: Long,
        durationMs: Long,
        lastAyahNumber: Int,
        nowMs: Long = System.currentTimeMillis(),
    ): List<SurahResumeProgress> {
        if (durationMs <= 0L) return current
        val filtered = current.filterNot { it.surahId == surahId && it.reciterId == reciterId }
        val completedThreshold = (durationMs * COMPLETION_THRESHOLD).toLong()
        if (positionMs >= completedThreshold) {
            return filtered.sortedByDescending { it.updatedAt }
        }
        if (positionMs < MIN_RESUME_POSITION_MS) {
            return current
        }
        val entry = SurahResumeProgress(
            surahId = surahId,
            reciterId = reciterId,
            positionMs = positionMs,
            durationMs = durationMs,
            lastAyahNumber = lastAyahNumber.coerceAtLeast(1),
            updatedAt = nowMs,
        )
        return (listOf(entry) + filtered)
            .sortedByDescending { it.updatedAt }
            .take(MAX_ENTRIES)
    }

    fun removeEntry(
        current: List<SurahResumeProgress>,
        surahId: Int,
        reciterId: Int,
    ): List<SurahResumeProgress> {
        return current.filterNot { it.surahId == surahId && it.reciterId == reciterId }
    }
}

/**
 * Tracks up to 50 partially-listened surahs so listeners can leave long surahs mid-way
 * and pick up right where they left off from the Home screen.
 */
@Singleton
class SurahProgressStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(SurahProgressPayload::class.java)
    private val entriesKey = stringPreferencesKey("surah_progress_json")

    val progressList: Flow<List<SurahResumeProgress>> = context.surahProgressDataStore.data
        .catch { e -> if (e is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw e }
        .map { prefs ->
            val raw = prefs[entriesKey] ?: return@map emptyList()
            runCatching { adapter.fromJson(raw)?.entries.orEmpty() }
                .getOrDefault(emptyList())
                .sortedByDescending { it.updatedAt }
        }

    suspend fun updateProgress(
        surahId: Int,
        reciterId: Int,
        positionMs: Long,
        durationMs: Long,
        lastAyahNumber: Int,
    ) {
        if (durationMs <= 0L) return
        context.surahProgressDataStore.edit { prefs ->
            val current = prefs[entriesKey]?.let { raw ->
                runCatching { adapter.fromJson(raw)?.entries.orEmpty() }.getOrDefault(emptyList())
            }.orEmpty()
            val updated = SurahProgressRules.applyUpdate(
                current = current,
                surahId = surahId,
                reciterId = reciterId,
                positionMs = positionMs,
                durationMs = durationMs,
                lastAyahNumber = lastAyahNumber,
            )
            prefs[entriesKey] = adapter.toJson(SurahProgressPayload(updated))
        }
    }

    suspend fun markCompleted(surahId: Int, reciterId: Int) {
        context.surahProgressDataStore.edit { prefs ->
            val current = prefs[entriesKey]?.let { raw ->
                runCatching { adapter.fromJson(raw)?.entries.orEmpty() }.getOrDefault(emptyList())
            }.orEmpty()
            val updated = SurahProgressRules.removeEntry(current, surahId, reciterId)
            prefs[entriesKey] = adapter.toJson(SurahProgressPayload(updated))
        }
    }
}
