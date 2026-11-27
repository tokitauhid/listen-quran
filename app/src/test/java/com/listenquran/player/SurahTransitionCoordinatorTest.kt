package com.listenquran.player

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import com.listenquran.domain.model.VerseTiming
import com.listenquran.player.cast.FadeGainAudioProcessor
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class SurahTransitionCoordinatorTest {

    private fun newProcessor(): FadeGainAudioProcessor {
        return FadeGainAudioProcessor().apply {
            configure(AudioProcessor.AudioFormat(1000, 2, C.ENCODING_PCM_16BIT))
            flush()
        }
    }

    @Test
    fun `runAutoTransition never applies fade-in and plays main at full gain`() {
        val processor = newProcessor()
        // Simulate prior surah having faded out to 0
        processor.setGainImmediate(0f)

        var bismillahPlayed = false
        val coordinator = SurahTransitionCoordinator(
            mainFadeGainProcessor = processor,
            playBismillahCue = { _, _, _ ->
                bismillahPlayed = true
                true
            },
            setCueMuted = {},
            stopCue = {},
            dispatcher = Dispatchers.Unconfined,
        )

        var gainWhenPlayMainCalled = -1f
        val finishedLatch = CountDownLatch(1)
        val timings = listOf(VerseTiming(verseKey = "2:1", timestampFrom = 0L, timestampTo = 3000L, duration = 3000L))

        coordinator.runAutoTransition(
            targetSurahId = 2,
            targetReciterId = 7,
            resolveTimings = { timings },
            playbackSpeed = 1.0f,
            fadeSeconds = 3,
            gapMs = 0,
            bismillahEnabled = true,
            isCastingProvider = { false },
            pauseMain = {},
            seekMainToZero = {},
            playMain = {
                gainWhenPlayMainCalled = processor.currentGain
            },
            onFinished = { finishedLatch.countDown() },
        )

        assertTrue(finishedLatch.await(2, TimeUnit.SECONDS))
        assertTrue(bismillahPlayed)
        assertEquals(1.0f, gainWhenPlayMainCalled, 0.0001f)
        assertEquals(1.0f, processor.currentGain, 0.0001f)

        // Verify audio buffer immediately passes at full amplitude (zero fade-in attenuation)
        val input = ByteBuffer.allocateDirect(8).order(ByteOrder.LITTLE_ENDIAN)
        input.putShort(10000).putShort(10000).putShort(10000).putShort(10000)
        input.flip()
        processor.queueInput(input)
        val out = processor.getOutput().order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(10000, out.getShort(0).toInt())
    }

    @Test
    fun `onProgressTick triggers fade-out only when gapMs and fadeSeconds are positive`() {
        val processor = newProcessor()
        val coordinator = SurahTransitionCoordinator(
            mainFadeGainProcessor = processor,
            playBismillahCue = { _, _, _ -> true },
            setCueMuted = {},
            stopCue = {},
            dispatcher = Dispatchers.Unconfined,
        )

        // With gapMs = 0 (Pause Between Surahs = Off), fade-out must NOT trigger
        coordinator.onProgressTick(
            mediaId = "surah:1:reciter:7",
            rawPosMs = 28_500L,
            durationMs = 30_000L,
            isHifz = false,
            repeatMode = Player.REPEAT_MODE_OFF,
            isRadio = false,
            fadeSeconds = 3,
            gapMs = 0,
        )
        val bufNoFade = ByteBuffer.allocateDirect(4).order(ByteOrder.LITTLE_ENDIAN)
        bufNoFade.putShort(10000).putShort(10000).flip()
        processor.queueInput(bufNoFade)
        assertEquals(10000, processor.getOutput().order(ByteOrder.LITTLE_ENDIAN).getShort(0).toInt())

        // With gapMs = 600 and fadeSeconds = 2, fade-out ramps down at end of surah
        coordinator.onProgressTick(
            mediaId = "surah:1:reciter:7",
            rawPosMs = 28_500L,
            durationMs = 30_000L,
            isHifz = false,
            repeatMode = Player.REPEAT_MODE_OFF,
            isRadio = false,
            fadeSeconds = 2,
            gapMs = 600,
        )
        val frames = 2000
        val bufFade = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        repeat(frames) { bufFade.putShort(10000).putShort(10000) }
        bufFade.flip()
        processor.queueInput(bufFade)
        val fadedOut = processor.getOutput().order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0, fadedOut.getShort((frames - 1) * 4).toInt())
        assertEquals(0f, processor.currentGain, 0.001f)
    }

    @Test
    fun `runAutoTransition with gapMs 0 and no Bismillah does not pause or seek main player`() {
        val processor = newProcessor()
        processor.setGainImmediate(0.5f)
        val coordinator = SurahTransitionCoordinator(
            mainFadeGainProcessor = processor,
            playBismillahCue = { _, _, _ -> false },
            setCueMuted = {},
            stopCue = {},
            dispatcher = Dispatchers.Unconfined,
        )

        val pauseCount = AtomicInteger(0)
        val seekCount = AtomicInteger(0)
        val playCalled = AtomicBoolean(false)
        val finishedLatch = CountDownLatch(1)

        coordinator.runAutoTransition(
            targetSurahId = 9, // At-Tawbah never plays Bismillah
            targetReciterId = 7,
            resolveTimings = { emptyList() },
            playbackSpeed = 1.0f,
            fadeSeconds = 2,
            gapMs = 0,
            bismillahEnabled = true,
            isCastingProvider = { false },
            pauseMain = { pauseCount.incrementAndGet() },
            seekMainToZero = { seekCount.incrementAndGet() },
            playMain = { playCalled.set(true) },
            onFinished = { finishedLatch.countDown() },
        )

        assertTrue(finishedLatch.await(2, TimeUnit.SECONDS))
        assertEquals(0, pauseCount.get())
        assertEquals(0, seekCount.get())
        assertFalse(playCalled.get())
        assertEquals(1.0f, processor.currentGain, 0.0001f)
    }

    @Test
    fun `runAutoTransition pauses main before resolving timings when Bismillah may play`() {
        val processor = newProcessor()
        val events = mutableListOf<String>()
        val coordinator = SurahTransitionCoordinator(
            mainFadeGainProcessor = processor,
            playBismillahCue = { _, _, _ ->
                events.add("bismillah")
                true
            },
            setCueMuted = {},
            stopCue = {},
            dispatcher = Dispatchers.Unconfined,
        )

        val finishedLatch = CountDownLatch(1)
        coordinator.runAutoTransition(
            targetSurahId = 2,
            targetReciterId = 7,
            resolveTimings = {
                events.add("resolve")
                listOf(VerseTiming(verseKey = "2:1", timestampFrom = 0L, timestampTo = 3000L, duration = 3000L))
            },
            playbackSpeed = 1.0f,
            fadeSeconds = 0,
            gapMs = 0,
            bismillahEnabled = true,
            isCastingProvider = { false },
            pauseMain = { events.add("pause") },
            seekMainToZero = { events.add("seek0") },
            playMain = { events.add("play") },
            onFinished = { finishedLatch.countDown() },
        )

        assertTrue(finishedLatch.await(2, TimeUnit.SECONDS))
        assertEquals("pause must execute before async resolveTimings", "pause", events.firstOrNull())
        assertTrue(events.indexOf("pause") < events.indexOf("resolve"))
        assertTrue(events.indexOf("bismillah") < events.indexOf("play"))
    }

    @Test
    fun `runAutoTransition resumes main when paused at boundary with gapMs 0 and embedded Bismillah`() {
        val processor = newProcessor()
        val coordinator = SurahTransitionCoordinator(
            mainFadeGainProcessor = processor,
            playBismillahCue = { _, _, _ -> false },
            setCueMuted = {},
            stopCue = {},
            dispatcher = Dispatchers.Unconfined,
        )

        val playCount = AtomicInteger(0)
        val finishedLatch = CountDownLatch(1)
        coordinator.runAutoTransition(
            targetSurahId = 2,
            targetReciterId = 7,
            resolveTimings = {
                listOf(VerseTiming(verseKey = "2:1", timestampFrom = 4500L, timestampTo = 8000L, duration = 8000L))
            },
            playbackSpeed = 1.0f,
            fadeSeconds = 0,
            gapMs = 0,
            bismillahEnabled = true,
            isCastingProvider = { false },
            pauseMain = {},
            seekMainToZero = {},
            playMain = { playCount.incrementAndGet() },
            wasPausedAtBoundary = true,
            onFinished = { finishedLatch.countDown() },
        )

        assertTrue(finishedLatch.await(2, TimeUnit.SECONDS))
        assertEquals(1, playCount.get())
    }
}
