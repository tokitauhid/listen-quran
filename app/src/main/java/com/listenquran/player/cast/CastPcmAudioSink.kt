package com.listenquran.player.cast

import android.media.AudioDeviceInfo
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessingPipeline
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import com.google.common.collect.ImmutableList
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Headless, pull-driven [AudioSink] used exclusively while casting to a Wi-Fi / DLNA speaker.
 *
 * Processes decoded PCM-16 audio through [FadeGainAudioProcessor] -> [SilenceSkippingAudioProcessor]
 * -> [SonicAudioProcessor] (locked to 44.1 kHz output + speed/pitch) -> [StereoDownmixAudioProcessor]
 * (stereo) and feeds [PcmStreamHub] up to [highWaterBytes] (`800 ms` default).
 *
 * When [PcmStreamHub] is at or above [highWaterBytes], [handleBuffer] returns `false` without
 * dropping frames. Media3's `MediaCodecAudioRenderer.getDurationToProgressUs()` then puts the
 * decoder thread to sleep naturally until the speaker drains bytes over HTTP — locking ExoPlayer
 * to the speaker's hardware DAC clock with zero Android `AudioTrack` in `AudioFlinger`.
 */
@OptIn(UnstableApi::class)
class CastPcmAudioSink(
    private val fadeGainAudioProcessor: FadeGainAudioProcessor,
    private val pcmStreamHub: PcmStreamHub,
    private val source: PcmSource,
    private val highWaterMs: Long = DEFAULT_HIGH_WATER_MS,
) : AudioSink {

    companion object {
        const val DEFAULT_HIGH_WATER_MS = 800L
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocate(0).order(ByteOrder.nativeOrder())
    }

    private val highWaterBytes: Int =
        ((((highWaterMs.coerceAtLeast(40L) * PcmStreamHub.SAMPLE_RATE_HZ) / 1000L).toInt()) *
            PcmStreamHub.FRAME_SIZE_BYTES)
            .coerceAtMost(PcmStreamHub.DEFAULT_CAPACITY_BYTES / 2)

    private val silenceSkippingAudioProcessor = SilenceSkippingAudioProcessor()
    private val sonicAudioProcessor = SonicAudioProcessor().apply {
        setOutputSampleRateHz(PcmStreamHub.SAMPLE_RATE_HZ)
    }
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
    private val stereoDownmixAudioProcessor = StereoDownmixAudioProcessor()

    private val pipeline = AudioProcessingPipeline(
        ImmutableList.of(
            fadeGainAudioProcessor,
            silenceSkippingAudioProcessor,
            fixedRateSonicProcessor,
            stereoDownmixAudioProcessor,
        )
    )

    private var listener: AudioSink.Listener? = null
    private var clock: Clock = Clock.DEFAULT
    private var audioAttributes: AudioAttributes = AudioAttributes.DEFAULT
    private var playbackParameters: PlaybackParameters = PlaybackParameters.DEFAULT
    private var skipSilenceEnabled: Boolean = false
    private var configured: Boolean = false
    private var configuredInputFormat: Format? = null
    private var inputBaseFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var playing: Boolean = false
    private var endOfStreamQueued: Boolean = false
    private var pendingOutput: ByteBuffer = EMPTY_BUFFER

    private var startMediaTimeUs: Long = C.TIME_UNSET
    private var startMediaTimeUsNeedsSync: Boolean = false
    private var writtenOutputFrames: Long = 0L
    private var checkpointOutputFrames: Long = 0L
    private var checkpointMediaDurationUs: Long = 0L
    private var checkpointSpeed: Float = 1.0f
    private var previousSpeed: Float = 1.0f
    private var lastReportedPositionUs: Long = 0L
    private var notifiedAdvancing: Boolean = false

    override fun setListener(listener: AudioSink.Listener) {
        this.listener = listener
    }

    override fun setPlayerId(playerId: PlayerId?) = Unit

    override fun setClock(clock: Clock) {
        this.clock = clock
    }

    override fun supportsFormat(format: Format): Boolean {
        return getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED
    }

    override fun getFormatSupport(format: Format): Int {
        if (MimeTypes.AUDIO_RAW != format.sampleMimeType) {
            return AudioSink.SINK_FORMAT_UNSUPPORTED
        }
        return if (format.pcmEncoding == C.ENCODING_PCM_16BIT) {
            AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
        } else {
            AudioSink.SINK_FORMAT_UNSUPPORTED
        }
    }

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        if (MimeTypes.AUDIO_RAW != inputFormat.sampleMimeType ||
            inputFormat.pcmEncoding != C.ENCODING_PCM_16BIT ||
            inputFormat.sampleRate <= 0 ||
            inputFormat.channelCount <= 0
        ) {
            throw AudioSink.ConfigurationException("Unsupported cast PCM format: $inputFormat", inputFormat)
        }

        val base = AudioProcessor.AudioFormat(
            inputFormat.sampleRate,
            inputFormat.channelCount,
            inputFormat.pcmEncoding,
        )
        try {
            silenceSkippingAudioProcessor.setEnabled(skipSilenceEnabled)
            sonicAudioProcessor.setOutputSampleRateHz(PcmStreamHub.SAMPLE_RATE_HZ)
            sonicAudioProcessor.setSpeed(playbackParameters.speed)
            sonicAudioProcessor.setPitch(playbackParameters.pitch)
            pipeline.configure(base)
            pipeline.flush()
        } catch (e: AudioProcessor.UnhandledAudioFormatException) {
            throw AudioSink.ConfigurationException(e, inputFormat)
        }

        configured = true
        configuredInputFormat = inputFormat
        inputBaseFormat = base
        pendingOutput = EMPTY_BUFFER
        endOfStreamQueued = false
        resetTimingState()
    }

    override fun play() {
        playing = true
    }

    override fun handleDiscontinuity() {
        startMediaTimeUsNeedsSync = true
    }

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int,
    ): Boolean {
        if (!configured) return false

        if (pendingOutput.hasRemaining()) {
            if (!drainPendingOutputToHub()) {
                return false
            }
        }

        if (isBackpressuredInHub()) {
            return false
        }

        anchorOrSyncMediaClock(presentationTimeUs)

        if (!pipeline.isOperational) {
            if (buffer.hasRemaining()) {
                writeBufferDirectlyToHub(buffer)
            }
            return !buffer.hasRemaining()
        }

        if (buffer.hasRemaining()) {
            pipeline.queueInput(buffer)
        }
        drainPipelineToHub()

        return !buffer.hasRemaining() && !pendingOutput.hasRemaining()
    }

    override fun playToEndOfStream() {
        if (!configured) return
        if (!endOfStreamQueued) {
            endOfStreamQueued = true
            if (pipeline.isOperational) {
                pipeline.queueEndOfStream()
            }
        }
        drainPendingOutputToHub()
        if (pipeline.isOperational && !pendingOutput.hasRemaining()) {
            drainPipelineToHub()
        }
    }

    override fun isEnded(): Boolean {
        if (!configured) return true
        if (endOfStreamQueued) {
            drainPendingOutputToHub()
            if (pipeline.isOperational && !pendingOutput.hasRemaining()) {
                drainPipelineToHub()
            }
        }
        val pipelineDone = !pipeline.isOperational || pipeline.isEnded
        val hubDrained = !pcmStreamHub.isSessionOpen ||
            pcmStreamHub.activeSource != source ||
            pcmStreamHub.bufferedBytes(source) == 0
        return endOfStreamQueued && !pendingOutput.hasRemaining() && pipelineDone && hubDrained
    }

    override fun hasPendingData(): Boolean {
        if (pendingOutput.hasRemaining()) return true
        return pcmStreamHub.isSessionOpen &&
            pcmStreamHub.activeSource == source &&
            pcmStreamHub.bufferedBytes(source) > 0
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        if (!configured || startMediaTimeUs == C.TIME_UNSET) {
            return AudioSink.CURRENT_POSITION_NOT_SET
        }

        val hubBufferedFrames = if (pcmStreamHub.isSessionOpen && pcmStreamHub.activeSource == source) {
            (pcmStreamHub.bufferedBytes(source) / PcmStreamHub.FRAME_SIZE_BYTES).toLong()
        } else {
            0L
        }
        val playedFrames = (writtenOutputFrames - hubBufferedFrames).coerceAtLeast(0L)
        val mediaElapsedUs = if (playedFrames >= checkpointOutputFrames) {
            checkpointMediaDurationUs + framesToMediaUs(playedFrames - checkpointOutputFrames, checkpointSpeed)
        } else {
            (checkpointMediaDurationUs - framesToMediaUs(checkpointOutputFrames - playedFrames, previousSpeed))
                .coerceAtLeast(0L)
        }

        val pos = maxOf(lastReportedPositionUs, startMediaTimeUs + mediaElapsedUs)
        lastReportedPositionUs = pos
        return pos
    }

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        val sanitized = PlaybackParameters(
            playbackParameters.speed.coerceAtLeast(0.1f),
            playbackParameters.pitch.coerceAtLeast(0.1f),
        )
        if (this.playbackParameters == sanitized) return

        val wasActive = configured && fixedRateSonicProcessor.isActive()
        if (abs(sanitized.speed - checkpointSpeed) >= 0.001f) {
            val delta = (writtenOutputFrames - checkpointOutputFrames).coerceAtLeast(0L)
            checkpointMediaDurationUs += framesToMediaUs(delta, checkpointSpeed)
            checkpointOutputFrames = writtenOutputFrames
            previousSpeed = checkpointSpeed
            checkpointSpeed = sanitized.speed
        }

        this.playbackParameters = sanitized
        sonicAudioProcessor.setOutputSampleRateHz(PcmStreamHub.SAMPLE_RATE_HZ)
        sonicAudioProcessor.setSpeed(sanitized.speed)
        sonicAudioProcessor.setPitch(sanitized.pitch)

        val shouldBeActive = configured && (
            abs(sanitized.speed - 1.0f) >= 0.01f ||
                abs(sanitized.pitch - 1.0f) >= 0.01f ||
                (inputBaseFormat != AudioProcessor.AudioFormat.NOT_SET &&
                    inputBaseFormat.sampleRate != PcmStreamHub.SAMPLE_RATE_HZ)
            )
        if (configured && wasActive != shouldBeActive) {
            if (pipeline.isOperational && !pendingOutput.hasRemaining()) {
                drainPipelineToHub()
            }
            pipeline.flush()
        }
    }

    override fun getPlaybackParameters(): PlaybackParameters = playbackParameters

    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) {
        if (this.skipSilenceEnabled == skipSilenceEnabled) return
        this.skipSilenceEnabled = skipSilenceEnabled
        silenceSkippingAudioProcessor.setEnabled(skipSilenceEnabled)
        if (configured) {
            if (pipeline.isOperational && !pendingOutput.hasRemaining()) {
                drainPipelineToHub()
            }
            pipeline.flush()
        }
    }

    override fun getSkipSilenceEnabled(): Boolean = skipSilenceEnabled

    override fun setAudioAttributes(audioAttributes: AudioAttributes) {
        this.audioAttributes = audioAttributes
    }

    override fun getAudioAttributes(): AudioAttributes = audioAttributes

    override fun setAudioSessionId(audioSessionId: Int) = Unit

    override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) = Unit

    override fun setPreferredDevice(audioDeviceInfo: AudioDeviceInfo?) = Unit

    override fun setOutputStreamOffsetUs(outputStreamOffsetUs: Long) = Unit

    override fun enableTunnelingV21() = Unit

    override fun disableTunneling() = Unit

    override fun setOffloadMode(offloadMode: Int) = Unit

    override fun setOffloadDelayPadding(delayInFrames: Int, paddingInFrames: Int) = Unit

    override fun setVolume(volume: Float) = Unit

    override fun pause() {
        playing = false
    }

    override fun flush() {
        if (configured) {
            pipeline.flush()
        }
        pendingOutput = EMPTY_BUFFER
        endOfStreamQueued = false
        resetTimingState()
        pcmStreamHub.clearSourceBuffer(source)
    }

    override fun reset() {
        flush()
        pipeline.reset()
        sonicAudioProcessor.setOutputSampleRateHz(PcmStreamHub.SAMPLE_RATE_HZ)
        configured = false
        configuredInputFormat = null
        inputBaseFormat = AudioProcessor.AudioFormat.NOT_SET
        playing = false
    }

    private fun resetTimingState() {
        startMediaTimeUs = C.TIME_UNSET
        startMediaTimeUsNeedsSync = false
        writtenOutputFrames = 0L
        checkpointOutputFrames = 0L
        checkpointMediaDurationUs = 0L
        checkpointSpeed = playbackParameters.speed
        previousSpeed = checkpointSpeed
        lastReportedPositionUs = 0L
        notifiedAdvancing = false
    }

    private fun isBackpressuredInHub(): Boolean {
        return pcmStreamHub.isSessionOpen &&
            pcmStreamHub.activeSource == source &&
            pcmStreamHub.bufferedBytes(source) >= highWaterBytes
    }

    private fun anchorOrSyncMediaClock(presentationTimeUs: Long) {
        if (startMediaTimeUs == C.TIME_UNSET) {
            startMediaTimeUs = presentationTimeUs
            writtenOutputFrames = 0L
            checkpointOutputFrames = 0L
            checkpointMediaDurationUs = 0L
            checkpointSpeed = playbackParameters.speed
            previousSpeed = checkpointSpeed
            lastReportedPositionUs = presentationTimeUs
            startMediaTimeUsNeedsSync = false
        } else if (startMediaTimeUsNeedsSync) {
            val totalWrittenMediaUs = checkpointMediaDurationUs +
                framesToMediaUs((writtenOutputFrames - checkpointOutputFrames).coerceAtLeast(0L), checkpointSpeed)
            startMediaTimeUs = presentationTimeUs - totalWrittenMediaUs
            startMediaTimeUsNeedsSync = false
            listener?.onPositionDiscontinuity()
        }
    }

    private fun drainPendingOutputToHub(): Boolean {
        if (!pendingOutput.hasRemaining()) return true
        val written = pcmStreamHub.writeFromByteBufferWithLimit(source, pendingOutput, highWaterBytes)
        when {
            written > 0 -> recordWrittenBytes(written)
            written < 0 -> pendingOutput.position(pendingOutput.limit())
        }
        val drained = !pendingOutput.hasRemaining()
        if (drained) {
            pendingOutput = EMPTY_BUFFER
        }
        return drained
    }

    private fun drainPipelineToHub() {
        while (true) {
            val out = pipeline.getOutput()
            if (!out.hasRemaining()) break
            val written = pcmStreamHub.writeFromByteBufferWithLimit(source, out, highWaterBytes)
            when {
                written > 0 -> recordWrittenBytes(written)
                written < 0 -> out.position(out.limit())
            }
            if (out.hasRemaining()) {
                val copy = ByteArray(out.remaining())
                out.get(copy)
                pendingOutput = ByteBuffer.wrap(copy).order(out.order())
                break
            }
        }
    }

    private fun writeBufferDirectlyToHub(buffer: ByteBuffer) {
        val written = pcmStreamHub.writeFromByteBufferWithLimit(source, buffer, highWaterBytes)
        when {
            written > 0 -> recordWrittenBytes(written)
            written < 0 -> buffer.position(buffer.limit())
        }
    }

    private fun recordWrittenBytes(bytes: Int) {
        writtenOutputFrames += (bytes / PcmStreamHub.FRAME_SIZE_BYTES).toLong()
        if (!notifiedAdvancing) {
            notifiedAdvancing = true
            listener?.onPositionAdvancing(clock.currentTimeMillis())
        }
    }

    private fun framesToMediaUs(frames: Long, speed: Float): Long {
        return ((frames * 1_000_000.0 / PcmStreamHub.SAMPLE_RATE_HZ.toDouble()) * speed.toDouble()).toLong()
    }

    /**
     * Pure-JVM/Android stereo normalizer replacing Media3's `ChannelMixingAudioProcessor`
     * (which stores matrices in `android.util.SparseArray` and fails unmocked JVM unit tests).
     */
    private class StereoDownmixAudioProcessor : BaseAudioProcessor() {

        override fun onConfigure(
            inputAudioFormat: AudioProcessor.AudioFormat
        ): AudioProcessor.AudioFormat {
            if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount <= 0) {
                throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
            }
            if (inputAudioFormat.channelCount == 2) {
                return AudioProcessor.AudioFormat.NOT_SET
            }
            return AudioProcessor.AudioFormat(
                inputAudioFormat.sampleRate,
                2,
                C.ENCODING_PCM_16BIT,
            )
        }

        override fun queueInput(inputBuffer: ByteBuffer) {
            val remaining = inputBuffer.remaining()
            if (remaining <= 0) return

            val inputChannels = inputAudioFormat.channelCount
            val inputFrameBytes = inputChannels * 2
            val frameCount = remaining / inputFrameBytes
            if (frameCount <= 0) return

            val out = replaceOutputBuffer(frameCount * PcmStreamHub.FRAME_SIZE_BYTES)
            if (inputChannels == 1) {
                for (i in 0 until frameCount) {
                    val sample = inputBuffer.getShort()
                    out.putShort(sample)
                    out.putShort(sample)
                }
            } else {
                val extraBytes = (inputChannels - 2) * 2
                for (i in 0 until frameCount) {
                    val left = inputBuffer.getShort()
                    val right = inputBuffer.getShort()
                    out.putShort(left)
                    out.putShort(right)
                    inputBuffer.position(inputBuffer.position() + extraBytes)
                }
            }
            out.flip()
        }
    }
}
