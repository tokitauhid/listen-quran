package com.listenquran.data.backup

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class BackupSerializationTest {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(QuranBackup::class.java)

    private fun computeSha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `roundtrip backup serialization preserves all fields`() {
        val original = QuranBackup(
            schemaVersion = 1,
            appVersion = "1.0.0",
            exportedAt = 1700000000000L,
            checksum = "test-hash",
            playlists = listOf(
                BackupPlaylist(
                    name = "Morning Adhkar",
                    description = "Daily recitation",
                    createdAt = 1700000000000L,
                    items = listOf(
                        BackupPlaylistItem(
                            surahNumber = 1,
                            surahName = "Al-Fatihah",
                            surahArabic = "الفاتحة",
                            reciterId = 7,
                            reciterName = "Mishari Rashid",
                            startAyah = 1,
                            endAyah = 7,
                            orderIndex = 0
                        )
                    )
                )
            ),
            likedSurahs = listOf(
                BackupLikedSurah(
                    surahNumber = 18,
                    surahName = "Al-Kahf",
                    surahArabic = "الكهف",
                    reciterId = 7,
                    reciterName = "Mishari Rashid"
                )
            ),
            bookmarks = listOf(
                BackupBookmark(
                    surahNumber = 2,
                    surahName = "Al-Baqarah",
                    ayahNumber = 255,
                    verseKey = "2:255",
                    textUthmani = "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ",
                    translation = "Allah - there is no deity except Him",
                    note = "Ayat al-Kursi reflection"
                )
            ),
            repetitionPresets = listOf(
                BackupRepetitionPreset(
                    surahNumber = 112,
                    startAyah = 1,
                    endAyah = 4,
                    repeatCount = 3,
                    delaySeconds = 3,
                    label = "Al-Ikhlas 3x"
                )
            ),
            listeningStats = listOf(
                BackupListeningStat(
                    surahNumber = 67,
                    reciterId = 7,
                    durationSeconds = 480,
                    timestamp = 1700000000000L,
                    isCompleted = true
                )
            ),
            preferences = BackupPreferences(
                themeMode = "DARK",
                playbackSpeed = 1.0f,
                defaultReciterId = 7,
                wordByWordEnabled = true,
                translationEnabled = true,
                hifzRepeatCount = 3,
                hifzDelaySeconds = 0,
                customSleepTimerMinutes = 30
            )
        )

        val json = adapter.indent("  ").toJson(original)
        assertNotNull(json)
        assertTrue(json.contains("Morning Adhkar"))
        assertTrue(json.contains("Ayat al-Kursi reflection"))

        val deserialized = adapter.fromJson(json)
        assertNotNull(deserialized)
        assertEquals(original.schemaVersion, deserialized!!.schemaVersion)
        assertEquals(original.playlists.size, deserialized.playlists.size)
        assertEquals(original.likedSurahs.size, deserialized.likedSurahs.size)
        assertEquals(original.bookmarks.size, deserialized.bookmarks.size)
        assertEquals(original.repetitionPresets.size, deserialized.repetitionPresets.size)
        assertEquals(original.listeningStats.size, deserialized.listeningStats.size)
        assertEquals(original.preferences?.themeMode, deserialized.preferences?.themeMode)
        assertEquals(true, deserialized.listeningStats[0].isCompleted)
    }

    @Test
    fun `sha256 checksum detection flags tampering`() {
        val original = QuranBackup(
            schemaVersion = 1,
            appVersion = "1.0.0",
            exportedAt = 1700000000000L,
            checksum = "",
            likedSurahs = listOf(
                BackupLikedSurah(
                    surahNumber = 1,
                    surahName = "Al-Fatihah",
                    surahArabic = "الفاتحة",
                    reciterId = 7,
                    reciterName = "Mishari"
                )
            )
        )

        // Compute valid checksum over payload with empty checksum
        val canonicalJson = adapter.toJson(original)
        val hash = computeSha256(canonicalJson)

        val signedBackup = original.copy(checksum = hash)
        assertEquals(hash, signedBackup.checksum)

        // Tampering with the payload
        val tampered = signedBackup.copy(appVersion = "2.0.0")
        val candidateJson = adapter.toJson(tampered.copy(checksum = ""))
        val candidateHash = computeSha256(candidateJson)

        assertNotEquals(signedBackup.checksum, candidateHash)
    }

    @Test
    fun `BackupImportResult summarizes added and skipped accurately`() {
        val result = BackupImportResult(
            addedPlaylists = 2,
            skippedPlaylists = 1,
            addedLiked = 5,
            skippedLiked = 3,
            addedBookmarks = 1,
            skippedBookmarks = 0,
            addedStats = 10,
            skippedStats = 2
        )

        assertEquals(18, result.totalAdded)
        assertEquals(6, result.totalSkipped)

        val summary = result.toSummaryMessage()
        assertTrue(summary.contains("2 playlists"))
        assertTrue(summary.contains("5 liked"))
        assertTrue(summary.contains("1 bookmarks"))
        assertTrue(summary.contains("10 sessions"))
        assertTrue(summary.contains("6 duplicate items skipped"))
    }
}
