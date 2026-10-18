package com.listenquran.khatam

import com.listenquran.data.local.khatam.KhatamEntity
import com.listenquran.data.local.khatam.KhatamEventEntity
import com.listenquran.data.local.khatam.KhatamSnapshotEntity
import com.listenquran.domain.model.khatam.AyahRangeSet
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest
import java.util.UUID

class KhatamDurabilityAndRebuildTest {

    @Test
    fun rebuildSnapshotFromEvents_recreatesExactState() {
        val khatamId = UUID.randomUUID().toString()
        val events = listOf(
            KhatamEventEntity(
                eventId = "e1",
                khatamId = khatamId,
                eventType = "RANGE_ADDED",
                startAyah = 1,
                endAyah = 100,
                timestamp = 1000L,
                source = "MANUAL"
            ),
            KhatamEventEntity(
                eventId = "e2",
                khatamId = khatamId,
                eventType = "RANGE_ADDED",
                startAyah = 200,
                endAyah = 300,
                timestamp = 2000L,
                source = "PLAYBACK"
            ),
            KhatamEventEntity(
                eventId = "e3",
                khatamId = khatamId,
                eventType = "RANGE_REMOVED",
                startAyah = 50,
                endAyah = 70,
                timestamp = 3000L,
                source = "MANUAL"
            )
        )

        // Simulate replay from log
        var rangeSet = AyahRangeSet.EMPTY
        var lastId: String? = null
        var lastTimestamp = 0L

        for (e in events) {
            when (e.eventType) {
                "RANGE_ADDED" -> rangeSet = rangeSet.add(e.startAyah!!, e.endAyah!!)
                "RANGE_REMOVED" -> rangeSet = rangeSet.remove(e.startAyah!!, e.endAyah!!)
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

        // Expected: [1..49], [71..100], [200..300]
        // Count: 49 + 30 + 101 = 180 ayahs
        assertEquals(180, snapshot.completedAyahsCount)
        assertEquals("e3", snapshot.lastEventId)

        val deserialized = AyahRangeSet.fromJson(snapshot.rangesJson)
        assertEquals(180, deserialized.completedCount)
        assertTrue(deserialized.contains(49))
        assertFalse(deserialized.contains(50))
        assertFalse(deserialized.contains(70))
        assertTrue(deserialized.contains(71))
    }

    @Test
    fun idempotency_duplicateEvent_doesNotModifyRangeSet() {
        var rangeSet = AyahRangeSet.EMPTY.add(1, 100)
        val initialCount = rangeSet.completedCount

        // Add the exact same range again
        val updatedSet = rangeSet.add(1, 100)
        assertEquals(initialCount, updatedSet.completedCount)
        assertEquals(rangeSet, updatedSet)
    }

    @Test
    fun backupChecksum_validationAndCorruptionDetection() {
        val root = JSONObject().apply {
            put("version", 1)
            put("khatam_id", "test-uuid")
            put("ranges", "[[1,100]]")
        }

        val content = root.toString(2)
        val digest = MessageDigest.getInstance("SHA-256")
        val correctHash = digest.digest(content.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

        root.put("checksum", correctHash)

        // 1. Verify valid checksum
        val storedChecksum = root.getString("checksum")
        root.remove("checksum")
        val computedHash = MessageDigest.getInstance("SHA-256").digest(root.toString(2).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        assertEquals(correctHash, computedHash)

        // 2. Corrupt content (tampering or bitflip)
        root.put("ranges", "[[1,500]]") // altered ranges
        val tamperedHash = MessageDigest.getInstance("SHA-256").digest(root.toString(2).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        assertNotEquals("Tampered content must fail checksum comparison", storedChecksum, tamperedHash)
    }

    @Test
    fun importMergeStrategy_unionsRangesWithoutLoss() {
        val deviceA = AyahRangeSet.EMPTY.add(1, 100) // Ayahs 1 to 100
        val deviceB = AyahRangeSet.EMPTY.add(80, 250) // Ayahs 80 to 250

        val merged = deviceA.union(deviceB)
        assertEquals(250, merged.completedCount)
        assertTrue(merged.containsRange(1, 250))
    }
}
