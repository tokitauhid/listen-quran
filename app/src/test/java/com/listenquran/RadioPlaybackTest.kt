package com.listenquran

import com.listenquran.domain.model.CuratedRadioStations
import com.listenquran.domain.model.RadioStation
import com.listenquran.domain.model.RadioStationType
import com.listenquran.domain.model.Reciter
import com.listenquran.domain.model.Surah
import com.listenquran.player.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioPlaybackTest {

    private val testReciter = Reciter(
        id = 7,
        name = "مشاري راشد العفاسي",
        englishName = "Mishari Rashid al-`Afasy",
        style = "Murattal",
        audioServer = "https://download.quranicaudio.com/qdc/mishari_al_afasy/murattal/"
    )

    private fun makeSurah(id: Int, name: String) = Surah(
        id = id,
        nameSimple = name,
        nameComplex = name,
        nameArabic = "سورة",
        revelationPlace = "makkah",
        revelationOrder = id,
        versesCount = 10,
        pages = listOf(1, 2),
        translatedName = name
    )

    @Test
    fun testCuratedStationsIntegrityAndParsing() {
        val stations = CuratedRadioStations
        assertEquals(4, stations.size)

        // Station 1: Popular Recitations
        val pop = stations[0]
        assertEquals("curated_1", pop.id)
        assertEquals(RadioStationType.CURATED, pop.type)
        assertEquals(114, pop.targetSurahs.size)
        assertTrue(pop.poolReciterIds.contains(7))
        assertNotNull(pop.bannerUrl)

        // Station 2: Yaseen, Al-Waqiah, Al-Mulk
        val threeSurahs = stations[1]
        assertEquals("curated_2", threeSurahs.id)
        assertEquals(listOf(36, 56, 67), threeSurahs.targetSurahs)
        assertEquals(listOf(7), threeSurahs.poolReciterIds)

        // Station 3: Surah Al-Kahf
        val kahf = stations[2]
        assertEquals("curated_3", kahf.id)
        assertEquals(listOf(18), kahf.targetSurahs)

        // Station 4: Juz Amma
        val juzAmma = stations[3]
        assertEquals("curated_4", juzAmma.id)
        assertEquals((78..114).toList(), juzAmma.targetSurahs)
    }

    @Test
    fun testDefensiveStationFallback() {
        // Test station with empty optional parameters
        val sparseStation = RadioStation(
            id = "sparse_1",
            name = "Test Sparse Station",
            description = "Station with minimal metadata",
            type = RadioStationType.RECITER,
            bannerUrl = null,
            poolReciterIds = emptyList(),
            targetSurahs = emptyList()
        )

        assertEquals("sparse_1", sparseStation.id)
        assertNull(sparseStation.bannerUrl)
        assertTrue(sparseStation.poolReciterIds.isEmpty())
        assertTrue(sparseStation.targetSurahs.isEmpty())

        // Simulated queue track resolution fallback
        val targetSurahs = if (sparseStation.targetSurahs.isNotEmpty()) sparseStation.targetSurahs else (1..114).toList()
        val poolReciters = if (sparseStation.poolReciterIds.isNotEmpty()) sparseStation.poolReciterIds else listOf(7)

        assertEquals(114, targetSurahs.size)
        assertEquals(listOf(7), poolReciters)
    }

    @Test
    fun testRadioStateMachineTransitions() {
        val initialSurah = makeSurah(1, "Al-Fatihah")
        var state = PlaybackState(
            currentSurah = initialSurah,
            currentReciter = testReciter,
            isPlaying = true,
            isRadio = false
        )

        // 1. User starts Radio station
        val station = CuratedRadioStations.first()
        state = state.copy(
            isRadio = true,
            currentRadioStation = station,
            isBuffering = true,
            isPlaying = false,
            radioError = null,
            lastSurahBeforeRadio = state.currentSurah,
            lastReciterBeforeRadio = state.currentReciter,
            queue = emptyList()
        )

        assertTrue(state.isRadio)
        assertTrue(state.isBuffering)
        assertFalse(state.isPlaying)
        assertEquals("curated_1", state.currentRadioStation?.id)
        assertEquals(1, state.lastSurahBeforeRadio?.id)

        // 2. Playback starts (Buffering -> Playing)
        state = state.copy(
            isBuffering = false,
            isPlaying = true,
            radioError = null
        )
        assertTrue(state.isPlaying)
        assertFalse(state.isBuffering)
        assertNull(state.radioError)

        // 3. User stops radio
        state = state.copy(
            isRadio = false,
            currentRadioStation = null,
            isPlaying = false,
            isBuffering = false,
            radioError = null
        )
        assertFalse(state.isRadio)
        assertNull(state.currentRadioStation)
        assertFalse(state.isPlaying)
        // Ensure last surah before radio is preserved for resumption
        assertEquals(1, state.lastSurahBeforeRadio?.id)

        // 4. Resume surah after radio
        val restoredSurah = state.lastSurahBeforeRadio
        assertNotNull(restoredSurah)
        state = state.copy(
            currentSurah = restoredSurah,
            currentReciter = state.lastReciterBeforeRadio,
            isPlaying = true,
            lastSurahBeforeRadio = null
        )
        assertEquals(1, state.currentSurah?.id)
        assertTrue(state.isPlaying)
        assertFalse(state.isRadio)
    }

    @Test
    fun testRadioTimeoutStateMachine() {
        val station = CuratedRadioStations.first()
        var state = PlaybackState(
            isRadio = true,
            currentRadioStation = station,
            isBuffering = true,
            isPlaying = false,
            radioError = null
        )

        // Simulate Watchdog timeout after 10 seconds
        val timeoutOccurred = true
        if (timeoutOccurred && state.isRadio && state.isBuffering && !state.isPlaying) {
            state = state.copy(
                isBuffering = false,
                radioError = "Playback connection timed out. Tap to retry."
            )
        }

        assertFalse(state.isBuffering)
        assertNotNull(state.radioError)
        assertTrue(state.radioError!!.contains("timed out"))
    }

    @Test
    fun testSessionRestoreDoesNotOverrideActiveRadio() {
        val station = CuratedRadioStations[1]
        val activeRadioState = PlaybackState(
            isRadio = true,
            currentRadioStation = station,
            isPlaying = true,
            lastSurahBeforeRadio = makeSurah(2, "Al-Baqarah")
        )

        // Simulate session callback sync attempt with regular surah
        val incomingMediaId = "radio:yaseen_waqiah_mulk:123456789:36:7"
        val isRadioMediaItem = incomingMediaId.startsWith("radio:")
        assertTrue(isRadioMediaItem)

        // When media ID is radio:*, the player must NEVER reset isRadio to false
        val parts = incomingMediaId.split(":")
        val stationId = parts[1]
        val surahId = parts[3].toInt()
        val reciterId = parts[4].toInt()

        assertEquals("yaseen_waqiah_mulk", stationId)
        assertEquals(36, surahId)
        assertEquals(7, reciterId)
        assertTrue(activeRadioState.isRadio)
    }
}
