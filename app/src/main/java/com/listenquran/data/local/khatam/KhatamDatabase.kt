package com.listenquran.data.local.khatam

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        KhatamEntity::class,
        KhatamEventEntity::class,
        KhatamSnapshotEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class KhatamDatabase : RoomDatabase() {
    abstract fun khatamDao(): KhatamDao
}
