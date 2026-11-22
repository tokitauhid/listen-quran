package com.listenquran

import com.listenquran.data.local.entity.BookmarkEntity
import com.listenquran.data.local.entity.RepetitionPresetEntity
import com.listenquran.domain.model.Bookmark
import com.listenquran.domain.model.RepetitionPreset
import com.listenquran.domain.model.Surah
import com.listenquran.domain.model.VerseTiming
import com.listenquran.data.local.dao.LikedDao
import com.listenquran.data.local.dao.PlaylistDao
import com.listenquran.data.local.entity.LikedSurahEntity
import com.listenquran.data.local.entity.PlaylistEntity
import com.listenquran.data.local.entity.PlaylistItemEntity
import com.listenquran.data.repository.LikedRepository
import com.listenquran.data.repository.PlaylistRepository
import com.listenquran.domain.model.LikedSurah
import com.listenquran.domain.model.RepetitionRangeValidator
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFeaturesTest {

    private val mockSurahAlBaqarah = Surah(
        id = 2,
        nameSimple = "Al-Baqarah",
        nameComplex = "Al-Baqarah",
        nameArabic = "البقرة",
        revelationPlace = "madinah",
        revelationOrder = 87,
        versesCount = 286,
        pages = listOf(2, 49),
        translatedName = "The Cow"
    )

    private val mockSurahAlFatihah = Surah(
        id = 1,
        nameSimple = "Al-Fatihah",
        nameComplex = "Al-Fatihah",
        nameArabic = "الفاتحة",
        revelationPlace = "makkah",
        revelationOrder = 5,
        versesCount = 7,
        pages = listOf(1, 1),
        translatedName = "The Opener"
    )

    private val mockVerseTimingsSurah2 = listOf(
        VerseTiming(verseKey = "2:1", timestampFrom = 0L, timestampTo = 4500L, duration = 4500L),
        VerseTiming(verseKey = "2:2", timestampFrom = 4500L, timestampTo = 11200L, duration = 6700L),
        VerseTiming(verseKey = "2:255", timestampFrom = 820000L, timestampTo = 875000L, duration = 55000L),
        VerseTiming(verseKey = "2:256", timestampFrom = 875000L, timestampTo = 920000L, duration = 45000L),
    )

    // ==========================================
    // 1. Bookmark Uniqueness & Toggle Logic
    // ==========================================

    @Test
    fun testBookmarkUniqueCompositeKeyLogic() {
        val existingBookmarks = mutableListOf(
            Bookmark(id = 1L, surahNumber = 2, surahName = "Al-Baqarah", ayahNumber = 255, verseKey = "2:255", textUthmani = "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ", translation = "Allah! There is no deity except Him"),
            Bookmark(id = 2L, surahNumber = 1, surahName = "Al-Fatihah", ayahNumber = 1, verseKey = "1:1", textUthmani = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ", translation = "In the name of Allah"),
        )

        // Verifying duplicate check on (surahNumber, ayahNumber)
        val duplicateCandidate = Bookmark(id = 3L, surahNumber = 2, surahName = "Al-Baqarah", ayahNumber = 255, verseKey = "2:255", textUthmani = "Duplicate", translation = "")
        val isDuplicate = existingBookmarks.any { it.surahNumber == duplicateCandidate.surahNumber && it.ayahNumber == duplicateCandidate.ayahNumber }
        assertTrue("Duplicate surah and ayah must be detected", isDuplicate)

        // Adding non-duplicate
        val newAyah = Bookmark(id = 4L, surahNumber = 2, surahName = "Al-Baqarah", ayahNumber = 256, verseKey = "2:256", textUthmani = "لَا إِكْرَاهَ فِي الدِّينِ", translation = "Let there be no compulsion in religion")
        val isNewDuplicate = existingBookmarks.any { it.surahNumber == newAyah.surahNumber && it.ayahNumber == newAyah.ayahNumber }
        assertFalse("New ayah must not be detected as duplicate", isNewDuplicate)
    }

    @Test
    fun testBookmarkToggleAction() {
        var currentBookmarks = listOf(
            Bookmark(id = 1L, surahNumber = 2, surahName = "Al-Baqarah", ayahNumber = 255, verseKey = "2:255", textUthmani = "آية الكرسي", translation = "The Throne Verse"),
        )

        // Toggle existing bookmark -> removes it
        val targetSurah = 2
        val targetAyah = 255
        val exists = currentBookmarks.any { it.surahNumber == targetSurah && it.ayahNumber == targetAyah }
        assertTrue("Bookmark initially exists", exists)

        currentBookmarks = if (exists) {
            currentBookmarks.filterNot { it.surahNumber == targetSurah && it.ayahNumber == targetAyah }
        } else {
            currentBookmarks + Bookmark(id = 2L, surahNumber = targetSurah, surahName = "Al-Baqarah", ayahNumber = targetAyah, verseKey = "$targetSurah:$targetAyah", textUthmani = "text", translation = "")
        }
        assertEquals(0, currentBookmarks.size)

        // Toggle again -> adds it back
        val existsAfterRemove = currentBookmarks.any { it.surahNumber == targetSurah && it.ayahNumber == targetAyah }
        assertFalse("Bookmark should no longer exist", existsAfterRemove)

        currentBookmarks = if (existsAfterRemove) {
            currentBookmarks.filterNot { it.surahNumber == targetSurah && it.ayahNumber == targetAyah }
        } else {
            currentBookmarks + Bookmark(id = 3L, surahNumber = targetSurah, surahName = "Al-Baqarah", ayahNumber = targetAyah, verseKey = "$targetSurah:$targetAyah", textUthmani = "آية الكرسي", translation = "The Throne Verse")
        }
        assertEquals(1, currentBookmarks.size)
        assertEquals(255, currentBookmarks.first().ayahNumber)
    }

    // ==========================================
    // 2. Repetition Range & Stepper Validation
    // ==========================================

    private fun validateRepetitionRange(
        startAyah: Int,
        endAyah: Int,
        totalSurahVerses: Int,
        repeatCount: Int,
        delaySeconds: Int,
    ): Boolean {
        if (startAyah < 1 || startAyah > totalSurahVerses) return false
        if (endAyah < 1 || endAyah > totalSurahVerses) return false
        if (startAyah > endAyah) return false
        if (repeatCount !in 0..100) return false // 0 represents infinite (∞)
        if (delaySeconds < 0 || delaySeconds > 60) return false
        return true
    }

    @Test
    fun testValidRepetitionRanges() {
        // Valid single ayah
        assertTrue(validateRepetitionRange(startAyah = 255, endAyah = 255, totalSurahVerses = 286, repeatCount = 10, delaySeconds = 3))

        // Valid multi-ayah range
        assertTrue(validateRepetitionRange(startAyah = 1, endAyah = 5, totalSurahVerses = 7, repeatCount = 5, delaySeconds = 0))

        // Valid boundary: whole surah
        assertTrue(validateRepetitionRange(startAyah = 1, endAyah = 7, totalSurahVerses = 7, repeatCount = 1, delaySeconds = 5))

        // Infinite count (0)
        assertTrue(validateRepetitionRange(startAyah = 1, endAyah = 3, totalSurahVerses = 7, repeatCount = 0, delaySeconds = 0))
    }

    @Test
    fun testInvalidRepetitionRanges() {
        // Start greater than end
        assertFalse(validateRepetitionRange(startAyah = 10, endAyah = 5, totalSurahVerses = 286, repeatCount = 3, delaySeconds = 0))

        // Start < 1
        assertFalse(validateRepetitionRange(startAyah = 0, endAyah = 5, totalSurahVerses = 286, repeatCount = 3, delaySeconds = 0))

        // End greater than total verses count in surah
        assertFalse(validateRepetitionRange(startAyah = 1, endAyah = 8, totalSurahVerses = 7, repeatCount = 3, delaySeconds = 0))

        // Negative repeat count
        assertFalse(validateRepetitionRange(startAyah = 1, endAyah = 5, totalSurahVerses = 286, repeatCount = -1, delaySeconds = 0))

        // Count exceeding 100
        assertFalse(validateRepetitionRange(startAyah = 1, endAyah = 5, totalSurahVerses = 286, repeatCount = 101, delaySeconds = 0))

        // Negative delay
        assertFalse(validateRepetitionRange(startAyah = 1, endAyah = 5, totalSurahVerses = 286, repeatCount = 5, delaySeconds = -1))
    }

    // ==========================================
    // 3. Preset Persistence & Model Mapping
    // ==========================================

    @Test
    fun testRepetitionPresetEntityMapping() {
        val entity = RepetitionPresetEntity(
            id = 42L,
            surahNumber = 67,
            startAyah = 1,
            endAyah = 5,
            repeatCount = 10,
            delaySeconds = 3,
            label = "Al-Mulk 1–5 ×10",
            createdAt = 1729440000000L
        )

        // Mapping to domain model
        val domainModel = RepetitionPreset(
            id = entity.id,
            surahNumber = entity.surahNumber,
            startAyah = entity.startAyah,
            endAyah = entity.endAyah,
            repeatCount = entity.repeatCount,
            delaySeconds = entity.delaySeconds,
            label = entity.label,
            createdAt = entity.createdAt
        )

        assertEquals(42L, domainModel.id)
        assertEquals(67, domainModel.surahNumber)
        assertEquals(1, domainModel.startAyah)
        assertEquals(5, domainModel.endAyah)
        assertEquals(10, domainModel.repeatCount)
        assertEquals(3, domainModel.delaySeconds)
        assertEquals("Al-Mulk 1–5 ×10", domainModel.label)
        assertEquals(1729440000000L, domainModel.createdAt)

        // Mapping back to Entity
        val remappedEntity = RepetitionPresetEntity(
            id = domainModel.id,
            surahNumber = domainModel.surahNumber,
            startAyah = domainModel.startAyah,
            endAyah = domainModel.endAyah,
            repeatCount = domainModel.repeatCount,
            delaySeconds = domainModel.delaySeconds,
            label = domainModel.label,
            createdAt = domainModel.createdAt
        )
        assertEquals(entity, remappedEntity)
    }

    @Test
    fun testDefaultPresetLabelGeneration() {
        fun generateLabel(surahName: String, start: Int, end: Int, count: Int): String {
            val rangeStr = if (start == end) "$start" else "$start–$end"
            val countStr = if (count == 0) "∞" else "×$count"
            return "$surahName $rangeStr $countStr"
        }

        val singleAyahLabel = generateLabel("Al-Baqarah", 255, 255, 10)
        assertEquals("Al-Baqarah 255 ×10", singleAyahLabel)

        val rangeLabel = generateLabel("Al-Mulk", 1, 5, 10)
        assertEquals("Al-Mulk 1–5 ×10", rangeLabel)

        val infiniteLabel = generateLabel("Yasin", 1, 10, 0)
        assertEquals("Yasin 1–10 ∞", infiniteLabel)
    }

    // ==========================================
    // 4. Mapping Bookmark to Playback Position
    // ==========================================

    @Test
    fun testMapBookmarkToPlaybackPosition() {
        val bookmark = Bookmark(
            id = 10L,
            surahNumber = 2,
            surahName = "Al-Baqarah",
            ayahNumber = 255,
            verseKey = "2:255",
            textUthmani = "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ",
            translation = "Allah! There is no deity except Him"
        )

        // Resolve timing from verseTimings
        val verseKey = "${bookmark.surahNumber}:${bookmark.ayahNumber}"
        val timing = mockVerseTimingsSurah2.find { it.verseKey == verseKey }

        assertNotNull("Ayatul Kursi timing must be found", timing)
        assertEquals(820000L, timing?.timestampFrom)
        assertEquals(875000L, timing?.timestampTo)

        // Position seeking test
        val seekTargetMs = timing?.timestampFrom ?: 0L
        assertEquals(820000L, seekTargetMs)
    }

    @Test
    fun testMapBookmarkWhenTimingsUnavailable() {
        val bookmark = Bookmark(
            id = 11L,
            surahNumber = 1,
            surahName = "Al-Fatihah",
            ayahNumber = 3,
            verseKey = "1:3",
            textUthmani = "الرَّحْمَٰنِ الرَّحِيمِ",
            translation = "The Entirely Merciful, the Especially Merciful"
        )

        // Empty timings (reciter without verse sync timings)
        val emptyTimings = emptyList<VerseTiming>()
        val verseKey = "${bookmark.surahNumber}:${bookmark.ayahNumber}"
        val timing = emptyTimings.find { it.verseKey == verseKey }

        assertNull("Timing is null for untimed reciter", timing)
        // Fallback policy: cannot seek to exact ayah offset, plays from start of surah (0L)
        val seekFallbackMs = timing?.timestampFrom ?: 0L
        assertEquals(0L, seekFallbackMs)
    }

    @Test
    fun testFindSurahForBookmark() {
        val allSurahs = listOf(mockSurahAlFatihah, mockSurahAlBaqarah)
        val bookmark = Bookmark(
            id = 12L,
            surahNumber = 2,
            surahName = "Al-Baqarah",
            ayahNumber = 255,
            verseKey = "2:255",
            textUthmani = "",
            translation = ""
        )

        val foundSurah = allSurahs.find { it.id == bookmark.surahNumber }
        assertNotNull(foundSurah)
        assertEquals("Al-Baqarah", foundSurah?.nameSimple)
        assertEquals(286, foundSurah?.versesCount)
    }

    // ==========================================
    // 5. Repetition Range Pure Validator Tests
    // ==========================================

    @Test
    fun testValidatorEmptyInputs() {
        val res = RepetitionRangeValidator.validate(
            startText = "",
            endText = "",
            repeatCountText = "",
            maxAyahs = 7,
            isInfiniteRepeat = false
        )
        assertFalse(res.isValid)
        assertEquals("Enter a start ayah", res.startError)
        assertEquals("Enter repeat count", res.repeatCountError)
        assertNull(res.parsedStartAyah)
        assertNull(res.parsedEndAyah)
    }

    @Test
    fun testValidatorBlankEndMeansSingleAyah() {
        val res = RepetitionRangeValidator.validate(
            startText = "5",
            endText = "",
            repeatCountText = "10",
            maxAyahs = 7,
            isInfiniteRepeat = false
        )
        assertTrue("Blank end should resolve to start ayah as single ayah", res.isValid)
        assertEquals(5, res.parsedStartAyah)
        assertEquals(5, res.parsedEndAyah)
        assertEquals(10, res.parsedRepeatCount)
        assertNull(res.startError)
        assertNull(res.endError)
    }

    @Test
    fun testValidatorStartGreaterThanEnd() {
        val res = RepetitionRangeValidator.validate(
            startText = "5",
            endText = "2",
            repeatCountText = "10",
            maxAyahs = 7,
            isInfiniteRepeat = false
        )
        assertFalse(res.isValid)
        assertEquals("End must be ≥ start (5)", res.endError)
    }

    @Test
    fun testValidatorOutOfRange() {
        // Start < 1
        val resStartZero = RepetitionRangeValidator.validate("0", "5", "10", 7)
        assertFalse(resStartZero.isValid)
        assertEquals("Must be at least 1", resStartZero.startError)

        // Start > maxAyahs
        val resStartExceed = RepetitionRangeValidator.validate("8", "8", "10", 7)
        assertFalse(resStartExceed.isValid)
        assertEquals("Exceeds surah limit (7)", resStartExceed.startError)

        // End > maxAyahs
        val resEndExceed = RepetitionRangeValidator.validate("1", "8", "10", 7)
        assertFalse(resEndExceed.isValid)
        assertEquals("Exceeds surah limit (7)", resEndExceed.endError)
    }

    @Test
    fun testValidatorPartialTyping() {
        // While typing "25", user has typed "2" so far
        val resPartial = RepetitionRangeValidator.validate("2", "", "10", 286)
        assertTrue(resPartial.isValid)
        assertEquals(2, resPartial.parsedStartAyah)
        assertEquals(2, resPartial.parsedEndAyah)

        // Partial typing non-number / empty does not throw
        val resEmpty = RepetitionRangeValidator.validate("", "10", "5", 286)
        assertFalse(resEmpty.isValid)
        assertEquals("Enter a start ayah", resEmpty.startError)
    }

    @Test
    fun testValidatorInfiniteRepeat() {
        val res = RepetitionRangeValidator.validate(
            startText = "1",
            endText = "7",
            repeatCountText = "",
            maxAyahs = 7,
            isInfiniteRepeat = true
        )
        assertTrue(res.isValid)
        assertEquals(0, res.parsedRepeatCount)
        assertNull(res.repeatCountError)
    }

    @Test
    fun testValidatorRepeatCountRange() {
        val resZero = RepetitionRangeValidator.validate("1", "7", "0", 7, isInfiniteRepeat = false)
        assertFalse(resZero.isValid)
        assertEquals("Must be at least 1", resZero.repeatCountError)

        val resTooHigh = RepetitionRangeValidator.validate("1", "7", "101", 7, isInfiniteRepeat = false)
        assertFalse(resTooHigh.isValid)
        assertEquals("Maximum is 100", resTooHigh.repeatCountError)

        val resValidMax = RepetitionRangeValidator.validate("1", "7", "100", 7, isInfiniteRepeat = false)
        assertTrue(resValidMax.isValid)
        assertEquals(100, resValidMax.parsedRepeatCount)
    }

    @Test
    fun testValidatorSurahChange() {
        // For Surah Al-Fatihah (7 ayahs), 255 is invalid
        val resFatihah = RepetitionRangeValidator.validate("255", "255", "10", 7)
        assertFalse(resFatihah.isValid)
        assertEquals("Exceeds surah limit (7)", resFatihah.startError)

        // Switch to Surah Al-Baqarah (286 ayahs), 255 becomes valid
        val resBaqarah = RepetitionRangeValidator.validate("255", "255", "10", 286)
        assertTrue(resBaqarah.isValid)
        assertEquals(255, resBaqarah.parsedStartAyah)
        assertEquals(255, resBaqarah.parsedEndAyah)
    }

    // ==========================================
    // 6. Liked Repository & Flow Propagation
    // ==========================================

    private class FakeLikedDao : LikedDao {
        private val itemsFlow = MutableStateFlow<List<LikedSurahEntity>>(emptyList())

        override fun getAllLiked(): Flow<List<LikedSurahEntity>> = itemsFlow

        override fun isLiked(surahNumber: Int, reciterId: Int): Flow<Boolean> {
            return itemsFlow.map { list -> list.any { it.surahNumber == surahNumber && it.reciterId == reciterId } }
        }

        override suspend fun isLikedDirect(surahNumber: Int, reciterId: Int): Boolean {
            return itemsFlow.value.any { it.surahNumber == surahNumber && it.reciterId == reciterId }
        }

        override suspend fun insertLiked(liked: LikedSurahEntity): Long {
            val current = itemsFlow.value.toMutableList()
            val existingIndex = current.indexOfFirst { it.surahNumber == liked.surahNumber && it.reciterId == liked.reciterId }
            val id = if (existingIndex >= 0) {
                current[existingIndex] = liked.copy(id = current[existingIndex].id)
                current[existingIndex].id
            } else {
                val newId = (current.maxOfOrNull { it.id } ?: 0L) + 1L
                current.add(0, liked.copy(id = newId))
                newId
            }
            itemsFlow.value = current
            return id
        }

        override suspend fun removeLiked(surahNumber: Int, reciterId: Int) {
            itemsFlow.value = itemsFlow.value.filterNot { it.surahNumber == surahNumber && it.reciterId == reciterId }
        }

        override suspend fun deleteById(id: Long) {
            itemsFlow.value = itemsFlow.value.filterNot { it.id == id }
        }

        override suspend fun getCount(): Int = itemsFlow.value.size
    }

    @Test
    fun testLikedRepositoryLikeAndUnlikePropagation() = runTest {
        val fakeDao = FakeLikedDao()
        val repository = LikedRepository(fakeDao)

        // Initial state: empty
        assertEquals(0, repository.getAllLiked().first().size)
        assertFalse(repository.isLiked(1, 7).first())

        // Like Surah 1 with Reciter 7
        val added = repository.toggleLike(
            surahNumber = 1,
            surahName = "Al-Fatihah",
            surahArabic = "الفاتحة",
            reciterId = 7,
            reciterName = "Mishari",
        )
        assertTrue("toggleLike should return true on addition", added)

        // Flow emits new list
        val likedList = repository.getAllLiked().first()
        assertEquals(1, likedList.size)
        assertEquals(1, likedList.first().surahNumber)
        assertEquals(7, likedList.first().reciterId)
        assertTrue(repository.isLiked(1, 7).first())
        assertTrue(repository.isLikedDirect(1, 7))

        // Unlike Surah 1 with Reciter 7
        val removed = repository.toggleLike(
            surahNumber = 1,
            surahName = "Al-Fatihah",
            surahArabic = "الفاتحة",
            reciterId = 7,
            reciterName = "Mishari",
        )
        assertFalse("toggleLike should return false on removal", removed)

        // Flow emits empty list
        assertEquals(0, repository.getAllLiked().first().size)
        assertFalse(repository.isLiked(1, 7).first())
        assertFalse(repository.isLikedDirect(1, 7))
    }

    @Test
    fun testLikedRepositoryDuplicateLikesIdempotency() = runTest {
        val fakeDao = FakeLikedDao()
        val repository = LikedRepository(fakeDao)

        // Add like twice
        repository.addLike(2, "Al-Baqarah", "البقرة", 7, "Mishari")
        repository.addLike(2, "Al-Baqarah", "البقرة", 7, "Mishari")

        // Must still have exactly 1 item (unique composite key, idempotent)
        val list = repository.getAllLiked().first()
        assertEquals(1, list.size)
        assertEquals(2, list.first().surahNumber)
        assertEquals(7, list.first().reciterId)
    }

    @Test
    fun testLikedRepositoryMultipleItemsOrder() = runTest {
        val fakeDao = FakeLikedDao()
        val repository = LikedRepository(fakeDao)

        repository.addLike(1, "Al-Fatihah", "الفاتحة", 7, "Mishari")
        repository.addLike(2, "Al-Baqarah", "البقرة", 7, "Mishari")
        repository.addLike(36, "Yasin", "يس", 7, "Mishari")

        val list = repository.getAllLiked().first()
        assertEquals(3, list.size)
        // Most recently added first
        assertEquals(36, list[0].surahNumber)
        assertEquals(2, list[1].surahNumber)
        assertEquals(1, list[2].surahNumber)
    }

    // ==========================================
    // 7. Playlist Repository Track Count Propagation (Issue #1)
    // ==========================================

    private class FakePlaylistDao : PlaylistDao {
        private val playlistsFlow = MutableStateFlow<List<PlaylistEntity>>(emptyList())
        private val itemsFlow = MutableStateFlow<List<PlaylistItemEntity>>(emptyList())
        private var nextPlaylistId = 1L
        private var nextItemId = 1L

        override fun getAllPlaylists(): Flow<List<PlaylistEntity>> =
            playlistsFlow.map { list ->
                list.sortedWith(compareByDescending<PlaylistEntity> { it.isLikedCollection }.thenByDescending { it.createdAt })
            }

        override fun getLikedCollection(): Flow<PlaylistEntity?> =
            playlistsFlow.map { list -> list.firstOrNull { it.isLikedCollection } }

        override suspend fun getPlaylistById(playlistId: Long): PlaylistEntity? =
            playlistsFlow.value.firstOrNull { it.id == playlistId }

        override suspend fun insertPlaylist(playlist: PlaylistEntity): Long {
            val id = if (playlist.id != 0L) playlist.id else nextPlaylistId++
            val stored = playlist.copy(id = id)
            playlistsFlow.value = playlistsFlow.value.filterNot { it.id == id } + stored
            return id
        }

        override suspend fun deletePlaylist(playlist: PlaylistEntity) {
            playlistsFlow.value = playlistsFlow.value.filterNot { it.id == playlist.id }
            itemsFlow.value = itemsFlow.value.filterNot { it.playlistId == playlist.id }
        }

        override suspend fun updatePlaylistName(playlistId: Long, name: String) {
            playlistsFlow.value = playlistsFlow.value.map {
                if (it.id == playlistId) it.copy(name = name) else it
            }
        }

        override fun getPlaylistItems(playlistId: Long): Flow<List<PlaylistItemEntity>> =
            itemsFlow.map { list ->
                list.filter { it.playlistId == playlistId }.sortedWith(compareBy({ it.orderIndex }, { it.id }))
            }

        override fun getAllPlaylistItems(): Flow<List<PlaylistItemEntity>> =
            itemsFlow.map { list ->
                list.sortedWith(compareBy({ it.playlistId }, { it.orderIndex }, { it.id }))
            }

        override suspend fun getPlaylistItemsDirect(playlistId: Long): List<PlaylistItemEntity> =
            itemsFlow.value.filter { it.playlistId == playlistId }.sortedWith(compareBy({ it.orderIndex }, { it.id }))

        override suspend fun insertPlaylistItem(item: PlaylistItemEntity): Long {
            val id = if (item.id != 0L) item.id else nextItemId++
            val stored = item.copy(id = id)
            itemsFlow.value = itemsFlow.value.filterNot { it.id == id } + stored
            return id
        }

        override suspend fun insertPlaylistItems(items: List<PlaylistItemEntity>) {
            for (item in items) {
                insertPlaylistItem(item)
            }
        }

        override suspend fun deletePlaylistItem(itemId: Long) {
            itemsFlow.value = itemsFlow.value.filterNot { it.id == itemId }
        }

        override suspend fun updatePlaylistItemOrder(itemId: Long, orderIndex: Int) {
            itemsFlow.value = itemsFlow.value.map {
                if (it.id == itemId) it.copy(orderIndex = orderIndex) else it
            }
        }

        override suspend fun clearPlaylistItems(playlistId: Long) {
            itemsFlow.value = itemsFlow.value.filterNot { it.playlistId == playlistId }
        }

        override fun isLiked(surahNumber: Int, reciterId: Int): Flow<Boolean> =
            itemsFlow.map { list ->
                val likedIds = playlistsFlow.value.filter { it.isLikedCollection }.map { it.id }.toSet()
                list.any { it.playlistId in likedIds && it.surahNumber == surahNumber && it.reciterId == reciterId }
            }

        override suspend fun isLikedDirect(surahNumber: Int, reciterId: Int): Boolean {
            val likedIds = playlistsFlow.value.filter { it.isLikedCollection }.map { it.id }.toSet()
            return itemsFlow.value.any { it.playlistId in likedIds && it.surahNumber == surahNumber && it.reciterId == reciterId }
        }

        override suspend fun isItemInPlaylist(playlistId: Long, surahNumber: Int, reciterId: Int): Boolean =
            itemsFlow.value.any { it.playlistId == playlistId && it.surahNumber == surahNumber && it.reciterId == reciterId }

        override suspend fun removeDuplicatePlaylistItems() = Unit

        override suspend fun getLikedCollectionDirect(): PlaylistEntity? =
            playlistsFlow.value.firstOrNull { it.isLikedCollection }

        override suspend fun removeLiked(surahNumber: Int, reciterId: Int) {
            val likedIds = playlistsFlow.value.filter { it.isLikedCollection }.map { it.id }.toSet()
            itemsFlow.value = itemsFlow.value.filterNot {
                it.playlistId in likedIds && it.surahNumber == surahNumber && it.reciterId == reciterId
            }
        }
    }

    @Test
    fun testPlaylistRepositoryTrackCountsUpdateOnAddAndRemove() = runTest {
        val fakePlaylistDao = FakePlaylistDao()
        val fakeLikedDao = FakeLikedDao()
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val repository = PlaylistRepository(fakePlaylistDao, fakeLikedDao, moshi)

        val playlistId = repository.createPlaylist("Friday Favorites", "Weekly selection")

        // Initially 0 tracks
        val initialPlaylist = repository.getAllPlaylists().first().first { it.id == playlistId }
        assertEquals(0, initialPlaylist.items.size)

        // Add 2 tracks
        val added1 = repository.addToPlaylist(playlistId, 18, "Al-Kahf", "الكهف", 7, "Mishari")
        val added2 = repository.addToPlaylist(playlistId, 36, "Yasin", "يس", 7, "Mishari")
        assertTrue(added1)
        assertTrue(added2)

        val afterAdd = repository.getAllPlaylists().first().first { it.id == playlistId }
        assertEquals(2, afterAdd.items.size)
        assertEquals(18, afterAdd.items[0].surahNumber)
        assertEquals(0, afterAdd.items[0].orderIndex)
        assertEquals(36, afterAdd.items[1].surahNumber)
        assertEquals(1, afterAdd.items[1].orderIndex)

        // Remove 1 track
        repository.deletePlaylistItem(afterAdd.items[0].id)

        val afterRemove = repository.getAllPlaylists().first().first { it.id == playlistId }
        assertEquals(1, afterRemove.items.size)
        assertEquals(36, afterRemove.items[0].surahNumber)

        val directFetch = repository.getPlaylistById(playlistId)
        assertNotNull(directFetch)
        assertEquals(1, directFetch?.items?.size)
    }

    @Test
    fun testLikedPlaylistTrackCountsReflectLikedDao() = runTest {
        val fakePlaylistDao = FakePlaylistDao()
        val fakeLikedDao = FakeLikedDao()
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val playlistRepository = PlaylistRepository(fakePlaylistDao, fakeLikedDao, moshi)
        val likedRepository = LikedRepository(fakeLikedDao)

        val likedId = playlistRepository.ensureLikedPlaylistCreated()
        assertEquals(0, playlistRepository.getAllPlaylists().first().first { it.id == likedId }.items.size)

        likedRepository.addLike(1, "Al-Fatihah", "الفاتحة", 7, "Mishari")
        likedRepository.addLike(67, "Al-Mulk", "الملك", 7, "Mishari")

        val playlists = playlistRepository.getAllPlaylists().first()
        val likedPlaylist = playlists.first { it.isLikedCollection }
        assertEquals(2, likedPlaylist.items.size)

        likedRepository.removeLike(1, 7)
        val updatedLikedPlaylist = playlistRepository.getAllPlaylists().first().first { it.isLikedCollection }
        assertEquals(1, updatedLikedPlaylist.items.size)
        assertEquals(67, updatedLikedPlaylist.items.first().surahNumber)
    }

    @Test
    fun testLegacyLikedItemsInPlaylistItemsMigrateToLikedSurahsOnStartup() = runTest {
        val fakePlaylistDao = FakePlaylistDao()
        val fakeLikedDao = FakeLikedDao()
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

        // Seed a legacy Liked collection in playlists + 3 items in playlist_items while liked_surahs is empty
        val likedPlId = fakePlaylistDao.insertPlaylist(
            PlaylistEntity(name = "Liked Surahs", description = "Favorites", isLikedCollection = true)
        )
        fakePlaylistDao.insertPlaylistItem(
            PlaylistItemEntity(playlistId = likedPlId, surahNumber = 1, surahName = "Al-Fatihah", surahArabic = "الفاتحة", reciterId = 7, reciterName = "Mishari")
        )
        fakePlaylistDao.insertPlaylistItem(
            PlaylistItemEntity(playlistId = likedPlId, surahNumber = 18, surahName = "Al-Kahf", surahArabic = "الكهف", reciterId = 7, reciterName = "Mishari")
        )
        fakePlaylistDao.insertPlaylistItem(
            PlaylistItemEntity(playlistId = likedPlId, surahNumber = 36, surahName = "Ya-Sin", surahArabic = "يس", reciterId = 7, reciterName = "Mishari")
        )

        val playlistRepository = PlaylistRepository(fakePlaylistDao, fakeLikedDao, moshi)
        val likedRepository = LikedRepository(fakeLikedDao)

        // Before startup reconciliation, liked_surahs is empty, yet getAllPlaylists() merges legacy rows
        assertEquals(0, likedRepository.getAllLiked().first().size)
        assertEquals(3, playlistRepository.getAllPlaylists().first().first { it.isLikedCollection }.items.size)

        // Startup reconciliation moves all 3 legacy liked items into liked_surahs and clears playlist_items
        val reconciledId = playlistRepository.ensureLikedPlaylistCreated()
        assertEquals(likedPlId, reconciledId)
        assertEquals(3, likedRepository.getAllLiked().first().size)
        assertEquals(0, fakePlaylistDao.getPlaylistItemsDirect(likedPlId).size)
        assertEquals(3, playlistRepository.getAllPlaylists().first().first { it.isLikedCollection }.items.size)
    }
}

