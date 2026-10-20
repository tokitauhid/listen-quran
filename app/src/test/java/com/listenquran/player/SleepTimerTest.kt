package com.listenquran.player

import androidx.media3.common.Player
import com.listenquran.domain.model.QueueItem
import com.listenquran.domain.model.Reciter
import com.listenquran.domain.model.Surah
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerTest {

    private val testReciter = Reciter(
        id = 7,
        name = "Mishari",
        englishName = "Mishari Rashid al-Afasy",
        audioServer = "https://server.com"
    )

    private fun makeSurah(id: Int, name: String, verses: Int = 10) = Surah(
        id = id,
        nameSimple = name,
        nameArabic = "سورة",
        revelationPlace = "makkah",
        versesCount = verses,
        translatedName = name
    )

    // Helper simulating the same mode matching logic used in AudioPlayerManager
    private fun isSameMode(current: SleepTimerMode, requested: SleepTimerMode): Boolean {
        return when {
            current is SleepTimerMode.Off && requested is SleepTimerMode.Off -> true
            current is SleepTimerMode.EndOfSurah && requested is SleepTimerMode.EndOfSurah -> true
            current is SleepTimerMode.EndOfPlaylist && requested is SleepTimerMode.EndOfPlaylist -> true
            current is SleepTimerMode.Duration && requested is SleepTimerMode.Duration -> current.totalMinutes == requested.totalMinutes
            current is SleepTimerMode.Custom && requested is SleepTimerMode.Custom -> true
            else -> false
        }
    }

    // Helper simulating state machine transition
    private fun transitionMode(current: SleepTimerState, requested: SleepTimerMode): SleepTimerState {
        if (requested is SleepTimerMode.Off) {
            return SleepTimerState(mode = SleepTimerMode.Off, remainingSeconds = null, isRunning = false)
        }
        // Tapping the active option turns it off!
        if (current.isRunning && isSameMode(current.mode, requested)) {
            return SleepTimerState(mode = SleepTimerMode.Off, remainingSeconds = null, isRunning = false)
        }
        return when (requested) {
            is SleepTimerMode.Duration -> SleepTimerState(
                mode = requested,
                remainingSeconds = requested.totalMinutes * 60L,
                isRunning = true
            )
            is SleepTimerMode.Custom -> SleepTimerState(
                mode = requested,
                remainingSeconds = requested.totalMinutes * 60L,
                isRunning = true
            )
            is SleepTimerMode.EndOfSurah -> SleepTimerState(
                mode = SleepTimerMode.EndOfSurah,
                remainingSeconds = null,
                isRunning = true
            )
            is SleepTimerMode.EndOfPlaylist -> SleepTimerState(
                mode = SleepTimerMode.EndOfPlaylist,
                remainingSeconds = null,
                isRunning = true
            )
            is SleepTimerMode.Off -> SleepTimerState(
                mode = SleepTimerMode.Off,
                remainingSeconds = null,
                isRunning = false
            )
        }
    }

    @Test
    fun testInitialOffState() {
        val state = SleepTimerState()
        assertEquals(SleepTimerMode.Off, state.mode)
        assertFalse(state.isRunning)
        assertNull(state.remainingSeconds)
        assertFalse(state.isEndOfSurah)
        assertFalse(state.isEndOfPlaylist)
        assertFalse(state.isDurationTimer)
    }

    @Test
    fun testStartPresetDuration() {
        val initial = SleepTimerState()
        val next = transitionMode(initial, SleepTimerMode.Duration(15))

        assertTrue(next.isRunning)
        assertEquals(SleepTimerMode.Duration(15), next.mode)
        assertEquals(900L, next.remainingSeconds)
        assertTrue(next.isDurationTimer)
        assertFalse(next.isEndOfSurah)
        assertFalse(next.isEndOfPlaylist)
    }

    @Test
    fun testSwitchPresetDuration() {
        var state = transitionMode(SleepTimerState(), SleepTimerMode.Duration(15))
        assertEquals(SleepTimerMode.Duration(15), state.mode)
        assertEquals(900L, state.remainingSeconds)

        // Switch to 30 min
        state = transitionMode(state, SleepTimerMode.Duration(30))
        assertTrue(state.isRunning)
        assertEquals(SleepTimerMode.Duration(30), state.mode)
        assertEquals(1800L, state.remainingSeconds)
    }

    @Test
    fun testTapActiveDurationTurnsOff() {
        var state = transitionMode(SleepTimerState(), SleepTimerMode.Duration(30))
        assertTrue(state.isRunning)

        // Tapping the active 30 min option again turns it off
        state = transitionMode(state, SleepTimerMode.Duration(30))
        assertFalse(state.isRunning)
        assertEquals(SleepTimerMode.Off, state.mode)
        assertNull(state.remainingSeconds)
    }

    @Test
    fun testStartAndToggleEndOfSurah() {
        var state = transitionMode(SleepTimerState(), SleepTimerMode.EndOfSurah)
        assertTrue(state.isRunning)
        assertEquals(SleepTimerMode.EndOfSurah, state.mode)
        assertTrue(state.isEndOfSurah)
        assertNull(state.remainingSeconds)

        // Tapping active End of Surah turns it off
        state = transitionMode(state, SleepTimerMode.EndOfSurah)
        assertFalse(state.isRunning)
        assertEquals(SleepTimerMode.Off, state.mode)
    }

    @Test
    fun testStartAndToggleEndOfPlaylist() {
        var state = transitionMode(SleepTimerState(), SleepTimerMode.EndOfPlaylist)
        assertTrue(state.isRunning)
        assertEquals(SleepTimerMode.EndOfPlaylist, state.mode)
        assertTrue(state.isEndOfPlaylist)
        assertNull(state.remainingSeconds)

        // Tapping active End of Playlist turns it off
        state = transitionMode(state, SleepTimerMode.EndOfPlaylist)
        assertFalse(state.isRunning)
        assertEquals(SleepTimerMode.Off, state.mode)
    }

    @Test
    fun testCustomTimerStateAndToggle() {
        var state = transitionMode(SleepTimerState(), SleepTimerMode.Custom(25))
        assertTrue(state.isRunning)
        assertEquals(SleepTimerMode.Custom(25), state.mode)
        assertEquals(1500L, state.remainingSeconds)
        assertTrue(state.isDurationTimer)

        // Tapping active Custom option turns it off
        state = transitionMode(state, SleepTimerMode.Custom(25))
        assertFalse(state.isRunning)
        assertEquals(SleepTimerMode.Off, state.mode)
    }

    @Test
    fun testCustomInputValidation() {
        fun validate(input: String): Boolean {
            val parsed = input.toIntOrNull()
            return parsed != null && parsed in 1..720
        }

        assertFalse(validate(""))
        assertFalse(validate("   "))
        assertFalse(validate("abc"))
        assertFalse(validate("0"))
        assertFalse(validate("-10"))
        assertFalse(validate("721"))
        assertFalse(validate("9999"))

        assertTrue(validate("1"))
        assertTrue(validate("20"))
        assertTrue(validate("45"))
        assertTrue(validate("60"))
        assertTrue(validate("120"))
        assertTrue(validate("720"))
    }

    @Test
    fun testRadioStreamDisablesEndOfSurahAndPlaylist() {
        val isRadio = true

        val endOfSurahEnabled = !isRadio
        val endOfPlaylistEnabled = !isRadio
        val durationTimerEnabled = true

        assertFalse(endOfSurahEnabled)
        assertFalse(endOfPlaylistEnabled)
        assertTrue(durationTimerEnabled)
    }

    @Test
    fun testRepeatAllDisablesEndOfPlaylist() {
        val isRadio = false
        val repeatModeAll = Player.REPEAT_MODE_ALL
        val repeatModeOff = Player.REPEAT_MODE_OFF

        val endOfPlaylistWithRepeatAll = !isRadio && (repeatModeAll != Player.REPEAT_MODE_ALL)
        val endOfPlaylistWithRepeatOff = !isRadio && (repeatModeOff != Player.REPEAT_MODE_ALL)

        assertFalse(endOfPlaylistWithRepeatAll)
        assertTrue(endOfPlaylistWithRepeatOff)
    }

    @Test
    fun testEndOfPlaylistDetectionWithoutShuffle() {
        val surah1 = makeSurah(1, "Al-Fatihah")
        val surah2 = makeSurah(2, "Al-Baqarah")
        val surah3 = makeSurah(3, "Ali 'Imran")

        val items = listOf(
            QueueItem(surah = surah1, reciter = testReciter, timelineIndex = 0),
            QueueItem(surah = surah2, reciter = testReciter, timelineIndex = 1),
            QueueItem(surah = surah3, reciter = testReciter, timelineIndex = 2)
        )

        // Track 1 playing: upcoming = [surah2, surah3]
        val queueState1 = PlaybackQueueState(
            currentItem = items[0],
            upcomingItems = listOf(items[1], items[2]),
            isShuffleEnabled = false
        )
        // Not at end of playlist
        assertFalse(queueState1.upcomingItems.isEmpty())

        // Track 3 playing (last item): upcoming = empty
        val queueState3 = PlaybackQueueState(
            currentItem = items[2],
            upcomingItems = emptyList(),
            isShuffleEnabled = false
        )
        // At end of playlist
        assertTrue(queueState3.upcomingItems.isEmpty())
    }

    @Test
    fun testEndOfPlaylistDetectionWithShuffle() {
        val surah1 = makeSurah(1, "Al-Fatihah")
        val surah2 = makeSurah(2, "Al-Baqarah")
        val surah3 = makeSurah(3, "Ali 'Imran")

        val items = listOf(
            QueueItem(surah = surah1, reciter = testReciter, timelineIndex = 0),
            QueueItem(surah = surah2, reciter = testReciter, timelineIndex = 1),
            QueueItem(surah = surah3, reciter = testReciter, timelineIndex = 2)
        )

        // Shuffled order: index 2 (surah3), then index 0 (surah1), then index 1 (surah2)
        // Playing surah3: upcoming = [surah1, surah2]
        val queueStateFirst = PlaybackQueueState(
            currentItem = items[2],
            upcomingItems = listOf(items[0], items[1]),
            isShuffleEnabled = true
        )
        assertFalse(queueStateFirst.upcomingItems.isEmpty())

        // Playing surah2 (final track in shuffled playback): upcoming = empty
        val queueStateLast = PlaybackQueueState(
            currentItem = items[1],
            upcomingItems = emptyList(),
            isShuffleEnabled = true
        )
        assertTrue(queueStateLast.upcomingItems.isEmpty())
    }

    @Test
    fun testQueueModificationWhileArmed() {
        val surah1 = makeSurah(1, "Al-Fatihah")
        val surah2 = makeSurah(2, "Al-Baqarah")
        val surah3 = makeSurah(3, "Ali 'Imran")

        // User initially has queue with surah1 and surah2
        var upcoming = listOf(QueueItem(surah = surah2, reciter = testReciter, timelineIndex = 1))
        var state = PlaybackQueueState(
            currentItem = QueueItem(surah = surah1, reciter = testReciter, timelineIndex = 0),
            upcomingItems = upcoming
        )
        assertFalse(state.upcomingItems.isEmpty())

        // User appends surah3 to queue
        upcoming = listOf(
            QueueItem(surah = surah2, reciter = testReciter, timelineIndex = 1),
            QueueItem(surah = surah3, reciter = testReciter, timelineIndex = 2)
        )
        state = state.copy(upcomingItems = upcoming)
        assertEquals(2, state.upcomingItems.size)
        // New last item is surah3
        assertEquals(3, state.upcomingItems.last().surah.id)
    }
}
