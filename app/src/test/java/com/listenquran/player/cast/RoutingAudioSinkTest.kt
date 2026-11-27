package com.listenquran.player.cast

import android.media.AudioDeviceInfo
import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.Clock
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.audio.AudioSink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class RoutingAudioSinkTest {

    private class FakeAudioSink : AudioSink {
        var assignedListener: AudioSink.Listener? = null
        var configureCount = 0
        var playCount = 0
        var pauseCount = 0
        var resetCount = 0
        var handleBufferCount = 0
        var lastConfiguredFormat: Format? = null
        var lastPlaybackParams: PlaybackParameters = PlaybackParameters.DEFAULT
        var lastVolume = 1.0f

        override fun setListener(listener: AudioSink.Listener) { this.assignedListener = listener }
        override fun supportsFormat(format: Format): Boolean = format.pcmEncoding == C.ENCODING_PCM_16BIT
        override fun getFormatSupport(format: Format): Int = AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
        override fun getCurrentPositionUs(sourceEnded: Boolean): Long = 123_456L
        override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
            configureCount++
            lastConfiguredFormat = inputFormat
        }
        override fun play() { playCount++ }
        override fun handleDiscontinuity() = Unit
        override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
            handleBufferCount++
            buffer.position(buffer.limit())
            return true
        }
        override fun playToEndOfStream() = Unit
        override fun isEnded(): Boolean = false
        override fun hasPendingData(): Boolean = false
        override fun setPlaybackParameters(playbackParameters: PlaybackParameters) { lastPlaybackParams = playbackParameters }
        override fun getPlaybackParameters(): PlaybackParameters = lastPlaybackParams
        override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) = Unit
        override fun getSkipSilenceEnabled(): Boolean = false
        override fun setAudioAttributes(audioAttributes: AudioAttributes) = Unit
        override fun getAudioAttributes(): AudioAttributes = AudioAttributes.DEFAULT
        override fun setAudioSessionId(audioSessionId: Int) = Unit
        override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) = Unit
        override fun enableTunnelingV21() = Unit
        override fun disableTunneling() = Unit
        override fun setVolume(volume: Float) { lastVolume = volume }
        override fun pause() { pauseCount++ }
        override fun flush() = Unit
        override fun reset() { resetCount++ }
        override fun setClock(clock: Clock) = Unit
        override fun setPlayerId(playerId: PlayerId?) = Unit
        override fun setPreferredDevice(audioDeviceInfo: AudioDeviceInfo?) = Unit
    }

    private fun testFormat(): Format = Format.Builder()
        .setSampleMimeType(MimeTypes.AUDIO_RAW)
        .setPcmEncoding(C.ENCODING_PCM_16BIT)
        .setSampleRate(48000)
        .setChannelCount(2)
        .build()

    @Test
    fun `hot-switching LOCAL to CAST resets localSink, reconfigures castSink, and notifies discontinuity`() {
        val local = FakeAudioSink()
        val cast = FakeAudioSink()
        val controller = CastAudioModeController()
        val routing = RoutingAudioSink(local, cast, controller)

        var discontinuityCount = 0
        routing.setListener(object : AudioSink.Listener {
            override fun onPositionDiscontinuity() { discontinuityCount++ }
            override fun onUnderrun(bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) = Unit
            override fun onSkipSilenceEnabledChanged(skipSilenceEnabled: Boolean) = Unit
        })

        val fmt = testFormat()
        val params = PlaybackParameters(1.25f)
        routing.configure(fmt, 0, null)
        routing.setPlaybackParameters(params)
        routing.play()
        assertTrue(routing.handleBuffer(ByteBuffer.allocate(64), 0L, 1))
        assertEquals(1, local.handleBufferCount)
        assertEquals(0, cast.handleBufferCount)

        // Flip to cast mode and sync -> local must pause + reset, cast must configure + preserve params + play
        controller.setCastMode(true)
        routing.syncModeIfNeeded()

        assertEquals(1, local.pauseCount)
        assertEquals(1, local.resetCount)
        assertEquals(1, cast.configureCount)
        assertEquals(fmt, cast.lastConfiguredFormat)
        assertEquals(params, cast.lastPlaybackParams)
        assertEquals(1, cast.playCount)
        assertEquals(1, discontinuityCount)

        assertTrue(routing.handleBuffer(ByteBuffer.allocate(64), 1000L, 1))
        assertEquals(1, local.handleBufferCount)
        assertEquals(1, cast.handleBufferCount)

        // Switch back to local -> cast pauses + resets, local reconfigures + restores volume 1f + plays
        local.lastVolume = 0.25f
        controller.setCastMode(false)
        routing.syncModeIfNeeded()

        assertEquals(1, cast.pauseCount)
        assertEquals(1, cast.resetCount)
        assertEquals(2, local.configureCount)
        assertEquals(1.0f, local.lastVolume, 0.001f)
        assertEquals(2, local.playCount)
        assertEquals(2, discontinuityCount)
    }
}
