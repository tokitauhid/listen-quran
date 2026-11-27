package com.listenquran.player.cast

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * PCM-16 audio processor applying smooth linear gain ramps (fade-in / fade-out)
 * directly to the decoded audio stream before speed/tee processing.
 *
 * Kept separate from [androidx.media3.common.Player.setVolume] so device mute
 * during DLNA casting never interferes with transition fades.
 */
@OptIn(UnstableApi::class)
class FadeGainAudioProcessor : BaseAudioProcessor() {

    private val lock = Any()

    @Volatile
    private var _currentGain: Float = 1.0f

    val currentGain: Float
        get() = _currentGain

    private var startGain: Float = 1.0f
    private var targetGain: Float = 1.0f
    private var rampFramesTotal: Long = 0L
    private var rampFramesElapsed: Long = 0L

    private var sampleRateHz: Int = 44100
    private var channelCount: Int = 2

    fun fadeTo(target: Float, durationMs: Long) {
        val clampedTarget = target.coerceIn(0f, 1f)
        synchronized(lock) {
            if (durationMs <= 0L) {
                startGain = clampedTarget
                targetGain = clampedTarget
                _currentGain = clampedTarget
                rampFramesTotal = 0L
                rampFramesElapsed = 0L
                return
            }
            val frames = ((sampleRateHz.toLong() * durationMs) / 1000L).coerceAtLeast(1L)
            startGain = _currentGain
            targetGain = clampedTarget
            rampFramesTotal = frames
            rampFramesElapsed = 0L
        }
    }

    fun setGainImmediate(gain: Float) {
        val clamped = gain.coerceIn(0f, 1f)
        synchronized(lock) {
            startGain = clamped
            targetGain = clamped
            _currentGain = clamped
            rampFramesTotal = 0L
            rampFramesElapsed = 0L
        }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        synchronized(lock) {
            sampleRateHz = inputAudioFormat.sampleRate.takeIf { it > 0 } ?: 44100
            channelCount = inputAudioFormat.channelCount.takeIf { it > 0 } ?: 2
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val channels: Int
        val localStartGain: Float
        val localTargetGain: Float
        val localTotalFrames: Long
        var localElapsedFrames: Long

        synchronized(lock) {
            channels = channelCount.coerceAtLeast(1)
            localStartGain = startGain
            localTargetGain = targetGain
            localTotalFrames = rampFramesTotal
            localElapsedFrames = rampFramesElapsed
        }

        val frameBytes = channels * 2
        val totalFramesInInput = remaining / frameBytes
        if (totalFramesInInput <= 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }

        val bytesToProcess = totalFramesInInput * frameBytes
        val out = replaceOutputBuffer(bytesToProcess)
        out.order(ByteOrder.LITTLE_ENDIAN)

        // Fast path: steady unity gain -> zero-math copy
        if (localTotalFrames == 0L && localTargetGain >= 0.9999f) {
            val dup = inputBuffer.slice()
            dup.limit(bytesToProcess)
            out.put(dup)
            inputBuffer.position(inputBuffer.position() + bytesToProcess)
            out.flip()
            return
        }

        // Fast path: steady mute -> zero-fill
        if (localTotalFrames == 0L && localTargetGain <= 0.0001f) {
            repeat(bytesToProcess) { out.put(0) }
            inputBuffer.position(inputBuffer.position() + bytesToProcess)
            out.flip()
            return
        }

        val inOrder = inputBuffer.order()
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)

        var lastGain = _currentGain
        for (frame in 0 until totalFramesInInput) {
            val gain = if (localTotalFrames > 0L && localElapsedFrames < localTotalFrames) {
                localElapsedFrames++
                val t = localElapsedFrames.toFloat() / localTotalFrames.toFloat()
                (localStartGain + (localTargetGain - localStartGain) * t).coerceIn(0f, 1f)
            } else {
                localTargetGain
            }
            lastGain = gain
            for (ch in 0 until channels) {
                val sample = inputBuffer.short.toInt()
                val scaled = (sample * gain).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                out.putShort(scaled.toShort())
            }
        }
        inputBuffer.order(inOrder)

        synchronized(lock) {
            _currentGain = lastGain
            // Only advance ramp if fadeTo/setGainImmediate wasn't called mid-buffer
            if (rampFramesTotal == localTotalFrames && targetGain == localTargetGain && startGain == localStartGain) {
                if (localTotalFrames > 0L && localElapsedFrames >= localTotalFrames) {
                    rampFramesTotal = 0L
                    rampFramesElapsed = 0L
                    startGain = localTargetGain
                    _currentGain = localTargetGain
                } else {
                    rampFramesElapsed = localElapsedFrames
                }
            }
        }

        out.flip()
    }

    override fun onReset() {
        synchronized(lock) {
            sampleRateHz = 44100
            channelCount = 2
        }
    }
}
