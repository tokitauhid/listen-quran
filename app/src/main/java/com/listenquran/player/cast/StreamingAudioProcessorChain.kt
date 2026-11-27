package com.listenquran.player.cast

import androidx.annotation.OptIn
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessorChain
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer

/**
 * Custom Media3 [AudioProcessorChain] routing decoded PCM through:
 *
 * 1. [FadeGainAudioProcessor] — smooth gain ramps independent of device mute
 * 2. [SilenceSkippingAudioProcessor] — optional silence skipping
 * 3. [SonicAudioProcessor] — time-stretching (speed/pitch) + resampling to 44.1 kHz
 * 4. [ChannelMixingAudioProcessor] — normalizes mono/multi-channel to stereo
 * 5. [TeeAudioProcessor] — taps 44.1 kHz stereo PCM into [PcmStreamHub] *after* speed is applied
 */
@OptIn(UnstableApi::class)
class StreamingAudioProcessorChain(
    val fadeGainAudioProcessor: FadeGainAudioProcessor,
    audioBufferSink: TeeAudioProcessor.AudioBufferSink,
) : AudioProcessorChain {

    private val silenceSkippingAudioProcessor = SilenceSkippingAudioProcessor()
    private val sonicAudioProcessor = SonicAudioProcessor().apply {
        setOutputSampleRateHz(PcmStreamHub.SAMPLE_RATE_HZ)
    }

    // SonicAudioProcessor.reset() clears pendingOutputSampleRate back to SAMPLE_RATE_NO_CHANGE (-1).
    // Wrapping it guarantees 44.1 kHz output sample rate is preserved across stop/reset cycles.
    private val fixedRateSonicProcessor = object : AudioProcessor {
        override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
            sonicAudioProcessor.setOutputSampleRateHz(PcmStreamHub.SAMPLE_RATE_HZ)
            return sonicAudioProcessor.configure(inputAudioFormat)
        }

        override fun isActive(): Boolean = sonicAudioProcessor.isActive

        override fun queueInput(inputBuffer: ByteBuffer) {
            sonicAudioProcessor.queueInput(inputBuffer)
        }

        override fun queueEndOfStream() {
            sonicAudioProcessor.queueEndOfStream()
        }

        override fun getOutput(): ByteBuffer = sonicAudioProcessor.output

        override fun isEnded(): Boolean = sonicAudioProcessor.isEnded

        override fun flush() {
            sonicAudioProcessor.flush()
        }

        override fun reset() {
            sonicAudioProcessor.reset()
            sonicAudioProcessor.setOutputSampleRateHz(PcmStreamHub.SAMPLE_RATE_HZ)
        }
    }

    private val channelMixingAudioProcessor = ChannelMixingAudioProcessor().apply {
        putChannelMixingMatrix(ChannelMixingMatrix.create(1, 2))
        putChannelMixingMatrix(ChannelMixingMatrix.create(2, 2))
        runCatching { putChannelMixingMatrix(ChannelMixingMatrix.create(6, 2)) }
    }
    private val teeAudioProcessor = TeeAudioProcessor(audioBufferSink)

    private val processors: Array<AudioProcessor> = arrayOf(
        fadeGainAudioProcessor,
        silenceSkippingAudioProcessor,
        fixedRateSonicProcessor,
        channelMixingAudioProcessor,
        teeAudioProcessor,
    )

    override fun getAudioProcessors(): Array<AudioProcessor> = processors

    override fun applyPlaybackParameters(playbackParameters: PlaybackParameters): PlaybackParameters {
        sonicAudioProcessor.setOutputSampleRateHz(PcmStreamHub.SAMPLE_RATE_HZ)
        sonicAudioProcessor.setSpeed(playbackParameters.speed)
        sonicAudioProcessor.setPitch(playbackParameters.pitch)
        return playbackParameters
    }

    override fun applySkipSilenceEnabled(skipSilenceEnabled: Boolean): Boolean {
        silenceSkippingAudioProcessor.setEnabled(skipSilenceEnabled)
        return skipSilenceEnabled
    }

    override fun getMediaDuration(playoutDuration: Long): Long {
        return sonicAudioProcessor.getMediaDuration(playoutDuration)
    }

    override fun getSkippedOutputFrameCount(): Long {
        return silenceSkippingAudioProcessor.skippedFrames
    }
}
