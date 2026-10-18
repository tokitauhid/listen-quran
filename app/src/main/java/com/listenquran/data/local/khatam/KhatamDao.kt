package com.listenquran.data.local.khatam

import android.database.Cursor
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import com.listenquran.domain.model.khatam.AyahRangeSet
import kotlinx.coroutines.flow.Flow

@Dao
interface KhatamDao {

    // --- Khatam Journey Lifecycle ---

    @Query("SELECT * FROM khatams WHERE is_active = 1 LIMIT 1")
    fun getActiveKhatamFlow(): Flow<KhatamEntity?>

    @Query("SELECT * FROM khatams WHERE is_active = 1 LIMIT 1")
    suspend fun getActiveKhatam(): KhatamEntity?

    @Query("SELECT * FROM khatams WHERE id = :id LIMIT 1")
    suspend fun getKhatamById(id: String): KhatamEntity?

    @Query("SELECT * FROM khatams ORDER BY created_at DESC")
    fun getAllKhatamsFlow(): Flow<List<KhatamEntity>>

    @Query("SELECT * FROM khatams ORDER BY created_at DESC")
    suspend fun getAllKhatams(): List<KhatamEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKhatam(khatam: KhatamEntity)

    @Update
    suspend fun updateKhatam(khatam: KhatamEntity)

    // --- Snapshot Management ---

    @Query("SELECT s.* FROM khatam_snapshots s INNER JOIN khatams k ON s.khatam_id = k.id WHERE k.is_active = 1 LIMIT 1")
    fun getActiveSnapshotFlow(): Flow<KhatamSnapshotEntity?>

    @Query("SELECT * FROM khatam_snapshots WHERE khatam_id = :khatamId LIMIT 1")
    suspend fun getSnapshot(khatamId: String): KhatamSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSnapshot(snapshot: KhatamSnapshotEntity)

    // --- Event Log (Append-Only) ---

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(event: KhatamEventEntity): Long

    @Query("SELECT * FROM khatam_events WHERE khatam_id = :khatamId ORDER BY timestamp ASC")
    suspend fun getEventsForKhatam(khatamId: String): List<KhatamEventEntity>

    @Query("SELECT * FROM khatam_events ORDER BY timestamp ASC")
    suspend fun getAllEvents(): List<KhatamEventEntity>

    @Query("SELECT * FROM khatam_events WHERE timestamp >= :sinceTimestamp AND event_type = 'RANGE_ADDED' ORDER BY timestamp ASC")
    fun getAddedEventsSinceFlow(sinceTimestamp: Long): Flow<List<KhatamEventEntity>>

    @Query("SELECT * FROM khatam_events WHERE timestamp >= :sinceTimestamp AND event_type = 'RANGE_ADDED' ORDER BY timestamp ASC")
    suspend fun getAddedEventsSince(sinceTimestamp: Long): List<KhatamEventEntity>

    @Query("SELECT COUNT(*) FROM khatam_events WHERE event_id = :eventId")
    suspend fun hasEvent(eventId: String): Int

    @Query("SELECT COUNT(*) FROM khatam_events WHERE khatam_id = :khatamId")
    suspend fun getEventCountForKhatam(khatamId: String): Int

    // --- Durability & Integrity Operations ---

    /**
     * Atomically appends an event and updates the materialized snapshot.
     * Idempotent: If event already exists (insert returns -1), snapshot is not modified.
     */
    @Transaction
    suspend fun applyEventAndSnapshotTransaction(
        event: KhatamEventEntity,
        snapshot: KhatamSnapshotEntity
    ): Boolean {
        val rowId = insertEvent(event)
        return if (rowId != -1L) {
            insertOrUpdateSnapshot(snapshot)
            true
        } else {
            false
        }
    }

    /**
     * Rebuilds the snapshot from the ground truth event log.
     * Guarantees self-repair if snapshot becomes desynchronized or missing.
     */
    @Transaction
    suspend fun rebuildSnapshotFromEvents(khatamId: String): KhatamSnapshotEntity {
        val events = getEventsForKhatam(khatamId)
        var rangeSet = AyahRangeSet.EMPTY
        var lastId: String? = null
        var lastTimestamp = System.currentTimeMillis()

        for (e in events) {
            when (e.eventType) {
                "RANGE_ADDED" -> {
                    if (e.startAyah != null && e.endAyah != null) {
                        rangeSet = rangeSet.add(e.startAyah, e.endAyah)
                    }
                }
                "RANGE_REMOVED" -> {
                    if (e.startAyah != null && e.endAyah != null) {
                        rangeSet = rangeSet.remove(e.startAyah, e.endAyah)
                    }
                }
            }
            lastId = e.eventId
            lastTimestamp = e.timestamp
        }

        val snapshot = KhatamSnapshotEntity(
            khatamId = khatamId,
            rangesJson = rangeSet.toJson(),
            completedAyahsCount = rangeSet.completedCount,
            lastEventId = lastId,
            lastUpdatedAt = lastTimestamp
        )
        insertOrUpdateSnapshot(snapshot)
        return snapshot
    }

    /**
     * Executes arbitrary PRAGMA statements such as "PRAGMA quick_check".
     */
    @RawQuery
    fun rawQuery(query: SupportSQLiteQuery): Cursor
}
