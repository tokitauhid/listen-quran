package com.listenquran.data.repository.khatam

import android.database.sqlite.SQLiteFullException
import androidx.sqlite.db.SimpleSQLiteQuery
import com.listenquran.data.local.khatam.KhatamDao
import com.listenquran.data.local.khatam.KhatamEntity
import com.listenquran.data.local.khatam.KhatamEventEntity
import com.listenquran.data.local.khatam.KhatamSnapshotEntity
import com.listenquran.domain.model.khatam.AyahRangeSet
import com.listenquran.domain.model.khatam.QuranAyahMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.max

@Singleton
class KhatamTrackerRepository @Inject constructor(
    private val khatamDao: KhatamDao,
    private val backupManager: KhatamBackupManager
) {
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _emergencyWarning = MutableStateFlow<String?>(null)
    val emergencyWarning: StateFlow<String?> = _emergencyWarning.asStateFlow()

    private val _isRecovering = MutableStateFlow(false)
    val isRecovering: StateFlow<Boolean> = _isRecovering.asStateFlow()

    private val lastSnapshotSaveTime = AtomicLong(0L)

    val activeKhatam: StateFlow<KhatamEntity?> = khatamDao.getActiveKhatamFlow()
        .stateIn(repositoryScope, SharingStarted.Eagerly, null)

    val activeSnapshot: StateFlow<KhatamSnapshotEntity?> = khatamDao.getActiveSnapshotFlow()
        .stateIn(repositoryScope, SharingStarted.Eagerly, null)

    val activeRangeSet: StateFlow<AyahRangeSet> = activeSnapshot
        .map { snapshot ->
            if (snapshot != null) AyahRangeSet.fromJson(snapshot.rangesJson) else AyahRangeSet.EMPTY
        }
        .stateIn(repositoryScope, SharingStarted.Eagerly, AyahRangeSet.EMPTY)

    val allKhatams: StateFlow<List<KhatamEntity>> = khatamDao.getAllKhatamsFlow()
        .stateIn(repositoryScope, SharingStarted.Eagerly, emptyList())

    // Today's progress: ayahs added since midnight
    private val todayMidnightMillis: Long
        get() {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return cal.timeInMillis
        }

    val todayEvents: StateFlow<List<KhatamEventEntity>> = khatamDao.getAddedEventsSinceFlow(todayMidnightMillis)
        .stateIn(repositoryScope, SharingStarted.Eagerly, emptyList())

    val todayAyahsRead: StateFlow<Int> = todayEvents
        .map { events ->
            var count = 0
            for (e in events) {
                if (e.startAyah != null && e.endAyah != null) {
                    count += (e.endAyah - e.startAyah + 1)
                }
            }
            count
        }
        .stateIn(repositoryScope, SharingStarted.Eagerly, 0)

    val goalStatus: StateFlow<KhatamGoalStatus> = combine(activeKhatam, activeRangeSet) { khatam, rangeSet ->
        computeGoalStatus(khatam, rangeSet)
    }.stateIn(repositoryScope, SharingStarted.Eagerly, KhatamGoalStatus())

    init {
        repositoryScope.launch {
            initialize()
        }
    }

    /**
     * Startup verification: runs PRAGMA quick_check, reconciles snapshot against event log,
     * restores from rolling snapshot if corrupt, and flushes any pending emergency events.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            // 1. Database quick_check
            val isHealthy = runQuickCheck()
            if (!isHealthy) {
                _isRecovering.value = true
                android.util.Log.w("KhatamTrackerRepository", "Integrity check failed! Attempting restore from snapshot...")
                val restored = backupManager.restoreFromLatestSnapshot()
                if (restored) {
                    _emergencyWarning.value = "Database was repaired from a local snapshot."
                } else {
                    _emergencyWarning.value = "Database integrity check warned. Rebuilding from event log."
                }
                _isRecovering.value = false
            }

            // 2. Ensure an active Khatam exists
            var current = khatamDao.getActiveKhatam()
            if (current == null) {
                val newId = UUID.randomUUID().toString()
                current = KhatamEntity(
                    id = newId,
                    title = "My Khatam",
                    targetDays = 30,
                    createdAt = System.currentTimeMillis(),
                    isActive = true
                )
                khatamDao.insertKhatam(current)
                val initialEvent = KhatamEventEntity(
                    eventId = UUID.randomUUID().toString(),
                    khatamId = newId,
                    eventType = "KHATAM_STARTED",
                    timestamp = System.currentTimeMillis(),
                    source = "MANUAL"
                )
                khatamDao.insertEvent(initialEvent)
                khatamDao.insertOrUpdateSnapshot(
                    KhatamSnapshotEntity(
                        khatamId = newId,
                        rangesJson = "[]",
                        completedAyahsCount = 0,
                        lastEventId = initialEvent.eventId,
                        lastUpdatedAt = System.currentTimeMillis()
                    )
                )
            }

            // 3. Reconcile snapshot against event log
            val currentSnapshot = khatamDao.getSnapshot(current.id)
            val events = khatamDao.getEventsForKhatam(current.id)
            var expectedCount = 0
            var testRangeSet = AyahRangeSet.EMPTY
            for (e in events) {
                if (e.eventType == "RANGE_ADDED" && e.startAyah != null && e.endAyah != null) {
                    testRangeSet = testRangeSet.add(e.startAyah, e.endAyah)
                } else if (e.eventType == "RANGE_REMOVED" && e.startAyah != null && e.endAyah != null) {
                    testRangeSet = testRangeSet.remove(e.startAyah, e.endAyah)
                }
            }
            expectedCount = testRangeSet.completedCount

            if (currentSnapshot == null || currentSnapshot.completedAyahsCount != expectedCount) {
                android.util.Log.i("KhatamTrackerRepository", "Rebuilding snapshot from event log (snapshot=${currentSnapshot?.completedAyahsCount}, expected=$expectedCount)")
                khatamDao.rebuildSnapshotFromEvents(current.id)
            }

            // 4. Ingest emergency buffer if any exists
            flushEmergencyBuffer()

            // 5. Initial snapshot save
            maybeTriggerRollingSnapshot(force = false)
        } catch (e: Exception) {
            android.util.Log.e("KhatamTrackerRepository", "Initialization error", e)
        }
    }

    private fun runQuickCheck(): Boolean {
        return try {
            val cursor = khatamDao.rawQuery(SimpleSQLiteQuery("PRAGMA quick_check;"))
            var ok = false
            if (cursor.moveToFirst()) {
                val result = cursor.getString(0)
                ok = result.equals("ok", ignoreCase = true)
            }
            cursor.close()
            ok
        } catch (e: Exception) {
            android.util.Log.e("KhatamTrackerRepository", "PRAGMA quick_check threw", e)
            false
        }
    }

    /**
     * Records completion of an ayah from real playback in AudioPlayerManager.
     */
    suspend fun recordPlaybackAyah(surahNumber: Int, ayahNumber: Int) {
        val globalAyah = QuranAyahMetadata.toGlobalAyah(surahNumber, ayahNumber)
        markRange(globalAyah, globalAyah, source = "PLAYBACK")
    }

    /**
     * Marks a range [startAyah, endAyah] as completed.
     */
    suspend fun markRange(startAyah: Int, endAyah: Int, source: String = "MANUAL") = withContext(Dispatchers.IO) {
        val khatam = khatamDao.getActiveKhatam() ?: return@withContext
        val currentRangeSet = activeRangeSet.value

        // If range is already completely covered, this is a no-op
        if (currentRangeSet.containsRange(startAyah, endAyah)) {
            return@withContext
        }

        val updatedRangeSet = currentRangeSet.add(startAyah, endAyah)
        val eventId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val event = KhatamEventEntity(
            eventId = eventId,
            khatamId = khatam.id,
            eventType = "RANGE_ADDED",
            startAyah = startAyah,
            endAyah = endAyah,
            timestamp = now,
            source = source
        )

        val snapshot = KhatamSnapshotEntity(
            khatamId = khatam.id,
            rangesJson = updatedRangeSet.toJson(),
            completedAyahsCount = updatedRangeSet.completedCount,
            lastEventId = eventId,
            lastUpdatedAt = now
        )

        try {
            khatamDao.applyEventAndSnapshotTransaction(event, snapshot)
            _emergencyWarning.value = null

            // Check for Khatam completion
            if (!currentRangeSet.isComplete && updatedRangeSet.isComplete) {
                recordKhatamCompleted(khatam.id)
            }

            maybeTriggerRollingSnapshot(force = false)
        } catch (fullEx: SQLiteFullException) {
            android.util.Log.e("KhatamTrackerRepository", "Storage full during markRange!", fullEx)
            backupManager.saveToEmergencyBuffer(event)
            _emergencyWarning.value = "Storage is full. Progress saved to emergency cache."
        } catch (e: Exception) {
            android.util.Log.e("KhatamTrackerRepository", "Failed to mark range", e)
        }
    }

    /**
     * Unmarks a range [startAyah, endAyah] (undo or correction).
     */
    suspend fun unmarkRange(startAyah: Int, endAyah: Int) = withContext(Dispatchers.IO) {
        val khatam = khatamDao.getActiveKhatam() ?: return@withContext
        val currentRangeSet = activeRangeSet.value

        if (currentRangeSet.overlapCount(startAyah, endAyah) == 0) {
            return@withContext
        }

        val updatedRangeSet = currentRangeSet.remove(startAyah, endAyah)
        val eventId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val event = KhatamEventEntity(
            eventId = eventId,
            khatamId = khatam.id,
            eventType = "RANGE_REMOVED",
            startAyah = startAyah,
            endAyah = endAyah,
            timestamp = now,
            source = "MANUAL"
        )

        val snapshot = KhatamSnapshotEntity(
            khatamId = khatam.id,
            rangesJson = updatedRangeSet.toJson(),
            completedAyahsCount = updatedRangeSet.completedCount,
            lastEventId = eventId,
            lastUpdatedAt = now
        )

        try {
            khatamDao.applyEventAndSnapshotTransaction(event, snapshot)
            maybeTriggerRollingSnapshot(force = false)
        } catch (fullEx: SQLiteFullException) {
            backupManager.saveToEmergencyBuffer(event)
            _emergencyWarning.value = "Storage is full. Correction saved to emergency cache."
        } catch (e: Exception) {
            android.util.Log.e("KhatamTrackerRepository", "Failed to unmark range", e)
        }
    }

    /**
     * Marks a complete Surah as finished.
     */
    suspend fun markSurah(surahNumber: Int) {
        val startAyah = QuranAyahMetadata.toGlobalAyah(surahNumber, 1)
        val count = QuranAyahMetadata.surahAyahCounts.getOrElse(surahNumber - 1) { 1 }
        val endAyah = startAyah + count - 1
        markRange(startAyah, endAyah, source = "MANUAL")
    }

    /**
     * Marks a complete Juz as finished.
     */
    suspend fun markJuz(juzNumber: Int) {
        val range = QuranAyahMetadata.juzRange(juzNumber)
        markRange(range.first, range.last, source = "MANUAL")
    }

    /**
     * Archives current active Khatam and starts a new one.
     */
    suspend fun startNewKhatam(
        title: String = "My Khatam",
        targetDays: Int? = 30,
        targetDate: Long? = null
    ) = withContext(Dispatchers.IO) {
        val active = khatamDao.getActiveKhatam()
        if (active != null) {
            khatamDao.updateKhatam(active.copy(isActive = false))
        }

        val newId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val newKhatam = KhatamEntity(
            id = newId,
            title = title,
            targetDays = targetDays,
            targetDate = targetDate,
            createdAt = now,
            isActive = true
        )
        khatamDao.insertKhatam(newKhatam)

        val startEvent = KhatamEventEntity(
            eventId = UUID.randomUUID().toString(),
            khatamId = newId,
            eventType = "KHATAM_STARTED",
            timestamp = now,
            source = "MANUAL"
        )
        khatamDao.insertEvent(startEvent)

        khatamDao.insertOrUpdateSnapshot(
            KhatamSnapshotEntity(
                khatamId = newId,
                rangesJson = "[]",
                completedAyahsCount = 0,
                lastEventId = startEvent.eventId,
                lastUpdatedAt = now
            )
        )

        maybeTriggerRollingSnapshot(force = true)
    }

    /**
     * Updates goal settings for the active Khatam.
     */
    suspend fun updateGoal(targetDays: Int?, targetDate: Long?) = withContext(Dispatchers.IO) {
        val active = khatamDao.getActiveKhatam() ?: return@withContext
        khatamDao.updateKhatam(active.copy(targetDays = targetDays, targetDate = targetDate))

        val event = KhatamEventEntity(
            eventId = UUID.randomUUID().toString(),
            khatamId = active.id,
            eventType = "GOAL_CHANGED",
            timestamp = System.currentTimeMillis(),
            source = "MANUAL"
        )
        khatamDao.insertEvent(event)
    }

    private suspend fun recordKhatamCompleted(khatamId: String) {
        val khatam = khatamDao.getKhatamById(khatamId) ?: return
        val now = System.currentTimeMillis()
        khatamDao.updateKhatam(khatam.copy(completedAt = now))

        val completeEvent = KhatamEventEntity(
            eventId = UUID.randomUUID().toString(),
            khatamId = khatamId,
            eventType = "KHATAM_COMPLETED",
            timestamp = now,
            source = "MANUAL"
        )
        khatamDao.insertEvent(completeEvent)
        maybeTriggerRollingSnapshot(force = true)
    }

    private suspend fun flushEmergencyBuffer() {
        val pending = backupManager.readEmergencyBuffer()
        if (pending.isEmpty()) return

        for (event in pending) {
            val rowId = khatamDao.insertEvent(event)
            if (rowId != -1L) {
                khatamDao.rebuildSnapshotFromEvents(event.khatamId)
            }
        }
        backupManager.clearEmergencyBuffer()
        _emergencyWarning.value = null
    }

    private suspend fun maybeTriggerRollingSnapshot(force: Boolean) {
        val now = System.currentTimeMillis()
        val last = lastSnapshotSaveTime.get()
        // Rate-limit snapshots to once per 10 seconds unless forced
        if (force || now - last > 10_000L) {
            if (lastSnapshotSaveTime.compareAndSet(last, now)) {
                backupManager.saveRollingSnapshot()
            }
        }
    }

    suspend fun exportBackup(outputStream: OutputStream): Boolean {
        return backupManager.exportToStream(outputStream)
    }

    suspend fun importBackup(inputStream: InputStream, mode: ImportMode = ImportMode.MERGE): ImportResult {
        val result = backupManager.importFromStream(inputStream, mode)
        if (result is ImportResult.Success) {
            // Re-read active khatam and snapshots
            val active = khatamDao.getActiveKhatam()
            if (active != null) {
                khatamDao.rebuildSnapshotFromEvents(active.id)
            }
        }
        return result
    }

    private fun computeGoalStatus(khatam: KhatamEntity?, rangeSet: AyahRangeSet): KhatamGoalStatus {
        if (khatam == null) return KhatamGoalStatus()
        val targetDays = khatam.targetDays ?: return KhatamGoalStatus(hasGoal = false)

        val now = System.currentTimeMillis()
        val elapsedMillis = max(0L, now - khatam.createdAt)
        val daysElapsed = (elapsedMillis / (1000L * 60 * 60 * 24)).toInt()
        val daysRemaining = max(1, targetDays - daysElapsed)

        val totalAyahs = QuranAyahMetadata.TOTAL_AYAHS
        val completed = rangeSet.completedCount
        val remainingAyahs = max(0, totalAyahs - completed)

        val dailyTarget = ceil(remainingAyahs.toDouble() / daysRemaining).toInt()
        val expectedPace = ((daysElapsed.toFloat() / targetDays.toFloat()) * totalAyahs).toInt()

        val diff = completed - expectedPace
        val (statusText, onTrack) = when {
            rangeSet.isComplete -> "Khatam Completed! 🎉" to true
            diff >= 0 -> "On track (${dailyTarget} ayahs/day)" to true
            else -> "${dailyTarget} ayahs/day to finish in $daysRemaining days" to false
        }

        return KhatamGoalStatus(
            hasGoal = true,
            targetDays = targetDays,
            daysElapsed = daysElapsed,
            daysRemaining = daysRemaining,
            dailyTargetAyahs = dailyTarget,
            statusText = statusText,
            isOnTrack = onTrack
        )
    }
}
