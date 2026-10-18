package com.listenquran.data.local.khatam

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a discrete Khatam journey (either currently active or completed and archived).
 */
@Entity(tableName = "khatams")
data class KhatamEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String, // UUID
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "target_days")
    val targetDays: Int? = null,
    @ColumnInfo(name = "target_date")
    val targetDate: Long? = null, // Epoch millis
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null, // Epoch millis when 100% finished
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true
)

/**
 * Immutable append-only event log entry representing a discrete mutation to Khatam progress.
 * Ground truth from which current state can always be reconstructed.
 */
@Entity(
    tableName = "khatam_events",
    foreignKeys = [
        ForeignKey(
            entity = KhatamEntity::class,
            parentColumns = ["id"],
            childColumns = ["khatam_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("khatam_id"),
        Index("timestamp")
    ]
)
data class KhatamEventEntity(
    @PrimaryKey
    @ColumnInfo(name = "event_id")
    val eventId: String, // UUID
    @ColumnInfo(name = "khatam_id")
    val khatamId: String,
    @ColumnInfo(name = "event_type")
    val eventType: String, // RANGE_ADDED, RANGE_REMOVED, GOAL_CHANGED, KHATAM_STARTED, KHATAM_COMPLETED
    @ColumnInfo(name = "start_ayah")
    val startAyah: Int? = null, // 1..6236
    @ColumnInfo(name = "end_ayah")
    val endAyah: Int? = null, // 1..6236
    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "source")
    val source: String, // PLAYBACK, MANUAL
    @ColumnInfo(name = "metadata_json")
    val metadataJson: String? = null
)

/**
 * Materialized snapshot caching the current state of a Khatam to accelerate UI queries.
 * Derived entirely from khatam_events; can be reconstructed at any time.
 */
@Entity(
    tableName = "khatam_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = KhatamEntity::class,
            parentColumns = ["id"],
            childColumns = ["khatam_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class KhatamSnapshotEntity(
    @PrimaryKey
    @ColumnInfo(name = "khatam_id")
    val khatamId: String,
    @ColumnInfo(name = "ranges_json")
    val rangesJson: String, // JSON string of [[start, end], ...]
    @ColumnInfo(name = "completed_ayahs_count")
    val completedAyahsCount: Int, // 0..6236
    @ColumnInfo(name = "last_event_id")
    val lastEventId: String?,
    @ColumnInfo(name = "last_updated_at")
    val lastUpdatedAt: Long = System.currentTimeMillis()
)
