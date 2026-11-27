package com.listenquran.player.cast

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.audio.AudioSink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CastPcmAudioSinkTest {

    private fun pcmFormat(sampleRate: Int = 44100, channels: Int = 2): Format =
        Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelCount(channels)
            .build()

    private fun pcmBuffer(numFrames: Int, sampleRate: Int = 44100, channels: Int = 2, fillValue: Short = 1000): ByteBuffer {
        val bytes = ByteBuffer.allocate(numFrames * channels * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until (numFrames * channels)) {
            bytes.putShort(fillValue)
        }
        bytes.flip()
        return bytes
    }

    @Test
    fun `applies backpressure at high-water mark without dropping bytes and resumes once drained`() {
        val hub = PcmStreamHub(initialPrebufferMs = 0L, defaultIdleSilenceThresholdMs = 500L)
        val token = hub.openSession()
        val clientId = hub.beginClientStream(token)

        // 200ms high-water mark = 8820 frames * 4 = 35,280 bytes
        val sink = CastPcmAudioSink(
            fadeGainAudioProcessor = FadeGainAudioProcessor(),
            pcmStreamHub = hub,
            source = PcmSource.MAIN,
            highWaterMs = 200L,
        )
        sink.configure(pcmFormat(44100, 2), 0, null)
        sink.play()

        // First 150ms chunk (6615 frames = 26,460 bytes) is accepted completely
        val chunk1 = pcmBuffer(6615)
        assertTrue(sink.handleBuffer(chunk1, presentationTimeUs = 0L, encodedAccessUnitCount = 1))
        assertEquals(0, chunk1.remaining())
        assertEquals(6615 * 4, hub.bufferedBytes(PcmSource.MAIN))

        // Second 150ms chunk reaches 200ms limit after partial consumption -> returns false (backpressure)
        val chunk2 = pcmBuffer(6615)
        val acceptedAll = sink.handleBuffer(chunk2, presentationTimeUs = 150_000L, encodedAccessUnitCount = 1)
        assertFalse("Sink should backpressure once hub hits 200ms watermark", acceptedAll)
        assertTrue(hub.bufferedBytes(PcmSource.MAIN) <= 200 * 44100 / 1000 * 4)

        // Trying again while still full returns false immediately without losing remaining input bytes
        val remBefore = chunk2.remaining()
        assertFalse(sink.handleBuffer(chunk2, presentationTimeUs = 150_000L, encodedAccessUnitCount = 1))
        assertEquals(remBefore, chunk2.remaining())

        // Drain 250ms from PcmStreamHub -> sink drains remaining bytes and accepts chunk2 completely
        val out = ByteArray(11025 * 4)
        val drained = hub.read(token, out, clientId = clientId)
        assertTrue(drained > 0)

        assertTrue(sink.handleBuffer(chunk2, presentationTimeUs = 150_000L, encodedAccessUnitCount = 1))
        assertEquals(0, chunk2.remaining())

        hub.closeSession()
    }

    @Test
    fun `tracks current position monotonically across consumption and 1_5x playback speed`() {
        val hub = PcmStreamHub(initialPrebufferMs = 0L, defaultIdleSilenceThresholdMs = 500L)
        val token = hub.openSession()
        val clientId = hub.beginClientStream(token)

        val sink = CastPcmAudioSink(
            fadeGainAudioProcessor = FadeGainAudioProcessor(),
            pcmStreamHub = hub,
            source = PcmSource.MAIN,
            highWaterMs = 1000L,
        )
        sink.configure(pcmFormat(44100, 2), 0, null)
        sink.play()

        // Before first buffer -> CURRENT_POSITION_NOT_SET
        assertEquals(AudioSink.CURRENT_POSITION_NOT_SET, sink.getCurrentPositionUs(false))

        // Submit 500ms (22,050 frames) starting at 10,000,000 us
        val startUs = 10_000_000L
        assertTrue(sink.handleBuffer(pcmBuffer(22050), presentationTimeUs = startUs, encodedAccessUnitCount = 1))
        // While all 500ms is still buffered in hub, position stays anchored at 10.0s
        assertEquals(startUs, sink.getCurrentPositionUs(false))

        // Drain 250ms (11,025 frames * 4 = 44,100 bytes)
        val readBuf = ByteArray(11025 * 4)
        assertEquals(readBuf.size, hub.read(token, readBuf, maxLen = readBuf.size, clientId = clientId))
        val posAfterQuarterSec = sink.getCurrentPositionUs(false)
        assertTrue("Expected ~10.25s, got $posAfterQuarterSec", posAfterQuarterSec in 10_245_000L..10_255_000L)

        // Drain the rest and switch to 1.5x speed
        assertEquals(readBuf.size, hub.read(token, readBuf, maxLen = readBuf.size, clientId = clientId))
        val posAfterHalfSec = sink.getCurrentPositionUs(false)
        assertTrue("Expected ~10.50s, got $posAfterHalfSec", posAfterHalfSec in 10_495_000L..10_505_000L)

        sink.setPlaybackParameters(PlaybackParameters(1.5f))
        // Feed 1500ms media -> Sonic shrinks to ~1000ms output PCM (or feed smaller 300ms media = ~200ms output)
        assertTrue(sink.handleBuffer(pcmBuffer(13230), presentationTimeUs = startUs + 500_000L, encodedAccessUnitCount = 1))
        // Drain remaining buffered bytes
        while (hub.bufferedBytes(PcmSource.MAIN) > 0) {
            val avail = hub.bufferedBytes(PcmSource.MAIN)
            hub.read(token, ByteArray(avail), maxLen = avail, clientId = clientId)
        }
        val posAfterSpeedChunk = sink.getCurrentPositionUs(false)
        assertTrue("Expected >= 10.75s at 1.5x, got $posAfterSpeedChunk", posAfterSpeedChunk >= 10_750_000L)

        hub.closeSession()
    }

    @Test
    fun `isEnded waits until hub drains all buffered frames for that source`() {
        val hub = PcmStreamHub(initialPrebufferMs = 0L, defaultIdleSilenceThresholdMs = 500L)
        val token = hub.openSession()
        val clientId = hub.beginClientStream(token)

        val sink = CastPcmAudioSink(
            fadeGainAudioProcessor = FadeGainAudioProcessor(),
            pcmStreamHub = hub,
            source = PcmSource.CUE,
            highWaterMs = 800L,
        )
        hub.activeSource = PcmSource.CUE
        sink.configure(pcmFormat(44100, 2), 0, null)
        sink.play()

        assertTrue(sink.handleBuffer(pcmBuffer(4410), presentationTimeUs = 0L, encodedAccessUnitCount = 1))
        sink.playToEndOfStream()

        // Hub still holds 100ms of CUE audio -> isEnded must be false so cue isn't clipped early
        assertFalse(sink.isEnded())
        assertTrue(sink.hasPendingData())

        val out = ByteArray(4410 * 4)
        assertEquals(out.size, hub.read(token, out, clientId = clientId))

        assertTrue(sink.isEnded())
        assertFalse(sink.hasPendingData())
        hub.closeSession()
    }

    @Test
    fun `upmixes mono input frames to stereo pcm16`() {
        val hub = PcmStreamHub(initialPrebufferMs = 0L, defaultIdleSilenceThresholdMs = 500L)
        val token = hub.openSession()
        val clientId = hub.beginClientStream(token)

        val sink = CastPcmAudioSink(
            fadeGainAudioProcessor = FadeGainAudioProcessor(),
            pcmStreamHub = hub,
            source = PcmSource.MAIN,
            highWaterMs = 800L,
        )
        sink.configure(pcmFormat(44100, channels = 1), 0, null)
        sink.play()

        // 1000 mono frames = 2000 bytes -> 1000 stereo frames = 4000 bytes
        val monoChunk = pcmBuffer(numFrames = 1000, channels = 1, fillValue = 1234)
        assertTrue(sink.handleBuffer(monoChunk, presentationTimeUs = 0L, encodedAccessUnitCount = 1))
        assertEquals(1000 * 4, hub.bufferedBytes(PcmSource.MAIN))

        val out = ByteArray(1000 * 4)
        assertEquals(out.size, hub.read(token, out, clientId = clientId))
        val outShorts = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        assertEquals(1234.toShort(), outShorts.get(0))
        assertEquals(1234.toShort(), outShorts.get(1))
        hub.closeSession()
    }
}
