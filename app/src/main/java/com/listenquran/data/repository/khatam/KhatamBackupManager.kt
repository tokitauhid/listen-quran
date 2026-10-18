package com.listenquran.data.repository.khatam

import android.content.Context
import com.listenquran.data.local.khatam.KhatamDao
import com.listenquran.data.local.khatam.KhatamEntity
import com.listenquran.data.local.khatam.KhatamEventEntity
import com.listenquran.data.local.khatam.KhatamSnapshotEntity
import com.listenquran.domain.model.khatam.AyahRangeSet
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

enum class ImportMode {
    MERGE,
    REPLACE
}

sealed class ImportResult {
    data class Success(val importedEventsCount: Int, val mode: ImportMode) : ImportResult()
    data class Failure(val reason: String) : ImportResult()
}

@Singleton
class KhatamBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val khatamDao: KhatamDao
) {
    private val snapshotDir: File by lazy {
        File(context.filesDir, "khatam_snapshots").apply {
            if (!exists()) mkdirs()
        }
    }

    private val emergencyFile: File by lazy {
        File(context.filesDir, "khatam_emergency_buffer.json")
    }

    /**
     * Rate-limited atomic snapshot creation in app-private storage.
     * Keeps the last 5 snapshots, using fsync and atomic rename.
     */
    suspend fun saveRollingSnapshot(): Boolean = withContext(Dispatchers.IO) {
        try {
            val khatams = khatamDao.getAllKhatams()
            if (khatams.isEmpty()) return@withContext false

            val allEvents = khatamDao.getAllEvents()
            val timestamp = System.currentTimeMillis()

            val root = JSONObject().apply {
                put("version", 1)
                put("timestamp", timestamp)
                put("type", "ROLLING_SNAPSHOT")

                val khatamsArray = JSONArray()
                for (k in khatams) {
                    val kObj = JSONObject().apply {
                        put("id", k.id)
                        put("title", k.title)
                        if (k.targetDays != null) put("target_days", k.targetDays)
                        if (k.targetDate != null) put("target_date", k.targetDate)
                        put("created_at", k.createdAt)
                        if (k.completedAt != null) put("completed_at", k.completedAt)
                        put("is_active", k.isActive)
                    }
                    khatamsArray.put(kObj)
                }
                put("khatams", khatamsArray)

                val eventsArray = JSONArray()
                for (e in allEvents) {
                    val eObj = JSONObject().apply {
                        put("event_id", e.eventId)
                        put("khatam_id", e.khatamId)
                        put("event_type", e.eventType)
                        if (e.startAyah != null) put("start_ayah", e.startAyah)
                        if (e.endAyah != null) put("end_ayah", e.endAyah)
                        put("timestamp", e.timestamp)
                        put("source", e.source)
                        if (e.metadataJson != null) put("metadata_json", e.metadataJson)
                    }
                    eventsArray.put(eObj)
                }
                put("events", eventsArray)
            }

            val content = root.toString(2)
            val checksum = computeSha256(content)
            root.put("checksum", checksum)

            val finalJson = root.toString(2)

            // Atomic write: write to temp file, fsync, then atomic rename
            val tempFile = File(snapshotDir, "snapshot_${timestamp}.json.tmp")
            val targetFile = File(snapshotDir, "snapshot_${timestamp}.json")

            FileOutputStream(tempFile).use { fos ->
                fos.write(finalJson.toByteArray(Charsets.UTF_8))
                fos.flush()
                fos.fd.sync() // Ensure physical disk persistence
            }

            if (tempFile.renameTo(targetFile)) {
                pruneOldSnapshots(maxKeep = 5)
                true
            } else {
                tempFile.delete()
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("KhatamBackupManager", "Failed to save rolling snapshot", e)
            false
        }
    }

    /**
     * Restores state from the most recent valid rolling snapshot.
     */
    suspend fun restoreFromLatestSnapshot(): Boolean = withContext(Dispatchers.IO) {
        val files = snapshotDir.listFiles { f -> f.name.startsWith("snapshot_") && f.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?: return@withContext false

        for (file in files) {
            try {
                val jsonString = file.readText(Charsets.UTF_8)
                val root = JSONObject(jsonString)
                val storedChecksum = root.optString("checksum")
                root.remove("checksum")
                val computedChecksum = computeSha256(root.toString(2))

                if (storedChecksum.isNotEmpty() && storedChecksum == computedChecksum) {
                    val success = applyParsedBackup(root, ImportMode.REPLACE)
                    if (success) {
                        return@withContext true
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("KhatamBackupManager", "Corrupt snapshot ignored: ${file.name}", e)
            }
        }
        false
    }

    /**
     * Appends an event to the emergency fallback buffer if the database disk is full.
     */
    suspend fun saveToEmergencyBuffer(event: KhatamEventEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val list = readEmergencyBuffer()
            val array = JSONArray()
            for (e in list) {
                array.put(eventToJson(e))
            }
            array.put(eventToJson(event))
            FileOutputStream(emergencyFile).use { fos ->
                fos.write(array.toString().toByteArray(Charsets.UTF_8))
                fos.flush()
                fos.fd.sync()
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("KhatamBackupManager", "Failed writing emergency buffer", e)
            false
        }
    }

    /**
     * Reads all pending emergency events from disk.
     */
    suspend fun readEmergencyBuffer(): List<KhatamEventEntity> = withContext(Dispatchers.IO) {
        if (!emergencyFile.exists()) return@withContext emptyList()
        try {
            val content = emergencyFile.readText(Charsets.UTF_8)
            val array = JSONArray(content)
            val list = mutableListOf<KhatamEventEntity>()
            for (i in 0 until array.length()) {
                list.add(jsonToEvent(array.getJSONObject(i)))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Clears the emergency buffer after successful database ingestion.
     */
    suspend fun clearEmergencyBuffer(): Unit = withContext(Dispatchers.IO) {
        if (emergencyFile.exists()) {
            emergencyFile.delete()
        }
    }

    /**
     * Exports full tracker data to an output stream (Storage Access Framework).
     */
    suspend fun exportToStream(outputStream: OutputStream): Boolean = withContext(Dispatchers.IO) {
        try {
            val khatams = khatamDao.getAllKhatams()
            val events = khatamDao.getAllEvents()
            val timestamp = System.currentTimeMillis()

            val root = JSONObject().apply {
                put("app", "ListenQuran")
                put("schema_version", 1)
                put("exported_at", timestamp)

                val khatamsArray = JSONArray()
                for (k in khatams) {
                    val kObj = JSONObject().apply {
                        put("id", k.id)
                        put("title", k.title)
                        if (k.targetDays != null) put("target_days", k.targetDays)
                        if (k.targetDate != null) put("target_date", k.targetDate)
                        put("created_at", k.createdAt)
                        if (k.completedAt != null) put("completed_at", k.completedAt)
                        put("is_active", k.isActive)
                    }
                    khatamsArray.put(kObj)
                }
                put("khatams", khatamsArray)

                val eventsArray = JSONArray()
                for (e in events) {
                    eventsArray.put(eventToJson(e))
                }
                put("events", eventsArray)
            }

            val content = root.toString(2)
            val checksum = computeSha256(content)
            root.put("checksum", checksum)

            outputStream.use { os ->
                os.write(root.toString(2).toByteArray(Charsets.UTF_8))
                os.flush()
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("KhatamBackupManager", "Export failed", e)
            false
        }
    }

    /**
     * Imports and validates tracker data from an input stream (Storage Access Framework).
     */
    suspend fun importFromStream(inputStream: InputStream, mode: ImportMode = ImportMode.MERGE): ImportResult = withContext(Dispatchers.IO) {
        try {
            val content = inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(content)

            if (!root.has("checksum")) {
                return@withContext ImportResult.Failure("Missing integrity checksum")
            }

            val expectedChecksum = root.getString("checksum")
            root.remove("checksum")
            val computedChecksum = computeSha256(root.toString(2))

            if (expectedChecksum != computedChecksum) {
                return@withContext ImportResult.Failure("Integrity check failed: file is corrupted or modified")
            }

            val eventsCount = root.optJSONArray("events")?.length() ?: 0
            val success = applyParsedBackup(root, mode)
            if (success) {
                ImportResult.Success(eventsCount, mode)
            } else {
                ImportResult.Failure("Failed to apply backup to database")
            }
        } catch (e: Exception) {
            ImportResult.Failure("Invalid backup JSON format: ${e.message}")
        }
    }

    private suspend fun applyParsedBackup(root: JSONObject, mode: ImportMode): Boolean {
        try {
            val khatamsArray = root.optJSONArray("khatams") ?: JSONArray()
            val eventsArray = root.optJSONArray("events") ?: JSONArray()

            // 1. Ingest Khatams
            for (i in 0 until khatamsArray.length()) {
                val obj = khatamsArray.getJSONObject(i)
                val id = obj.getString("id")
                val existing = khatamDao.getKhatamById(id)
                if (existing == null || mode == ImportMode.REPLACE) {
                    val entity = KhatamEntity(
                        id = id,
                        title = obj.getString("title"),
                        targetDays = if (obj.has("target_days")) obj.getInt("target_days") else null,
                        targetDate = if (obj.has("target_date")) obj.getLong("target_date") else null,
                        createdAt = obj.getLong("created_at"),
                        completedAt = if (obj.has("completed_at")) obj.getLong("completed_at") else null,
                        isActive = obj.optBoolean("is_active", false)
                    )
                    khatamDao.insertKhatam(entity)
                }
            }

            // 2. Ingest Events
            val khatamIdsToRebuild = mutableSetOf<String>()
            for (i in 0 until eventsArray.length()) {
                val eObj = eventsArray.getJSONObject(i)
                val event = jsonToEvent(eObj)
                khatamDao.insertEvent(event)
                khatamIdsToRebuild.add(event.khatamId)
            }

            // 3. Rebuild Snapshots for affected Khatams
            for (khatamId in khatamIdsToRebuild) {
                khatamDao.rebuildSnapshotFromEvents(khatamId)
            }

            return true
        } catch (e: Exception) {
            android.util.Log.e("KhatamBackupManager", "Error applying parsed backup", e)
            return false
        }
    }

    private fun pruneOldSnapshots(maxKeep: Int) {
        val files = snapshotDir.listFiles { f -> f.name.startsWith("snapshot_") && f.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?: return

        if (files.size > maxKeep) {
            for (i in maxKeep until files.size) {
                files[i].delete()
            }
        }
    }

    private fun computeSha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun eventToJson(e: KhatamEventEntity): JSONObject = JSONObject().apply {
        put("event_id", e.eventId)
        put("khatam_id", e.khatamId)
        put("event_type", e.eventType)
        if (e.startAyah != null) put("start_ayah", e.startAyah)
        if (e.endAyah != null) put("end_ayah", e.endAyah)
        put("timestamp", e.timestamp)
        put("source", e.source)
        if (e.metadataJson != null) put("metadata_json", e.metadataJson)
    }

    private fun jsonToEvent(obj: JSONObject): KhatamEventEntity = KhatamEventEntity(
        eventId = obj.getString("event_id"),
        khatamId = obj.getString("khatam_id"),
        eventType = obj.getString("event_type"),
        startAyah = if (obj.has("start_ayah")) obj.getInt("start_ayah") else null,
        endAyah = if (obj.has("end_ayah")) obj.getInt("end_ayah") else null,
        timestamp = obj.getLong("timestamp"),
        source = obj.getString("source"),
        metadataJson = if (obj.has("metadata_json")) obj.getString("metadata_json") else null
    )
}
