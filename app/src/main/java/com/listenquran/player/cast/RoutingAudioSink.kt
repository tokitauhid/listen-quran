package com.listenquran.player.cast

import android.media.AudioDeviceInfo
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.Format
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.AudioSink
import java.nio.ByteBuffer

/**
 * Dual-mode [AudioSink] routing decoded audio to either [localSink] (Android hardware `AudioTrack`)
 * or [castSink] (headless [CastPcmAudioSink] feeding [PcmStreamHub]) based on [CastAudioModeController].
 *
 * Hot-switching `LOCAL -> CAST` pauses and resets [localSink] immediately, closing the Android
 * `AudioTrack` in `AudioFlinger` so backgrounded/screen-off casting cannot be paused by OS
 * mute-track watchdogs (e.g. HyperOS `PowerKeeper.Audio`).
 */
@OptIn(UnstableApi::class)
class RoutingAudioSink(
    private val localSink: AudioSink,
    private val castSink: AudioSink,
    private val castModeController: CastAudioModeController,
) : AudioSink {

    private val lock = Any()

    private var activeCastMode: Boolean = castModeController.isCastMode
    private var listener: AudioSink.Listener? = null
    private var playerId: PlayerId? = null
    private var clock: Clock = Clock.DEFAULT
    private var audioAttributes: AudioAttributes = AudioAttributes.DEFAULT
    private var audioSessionId: Int = 0
    private var auxEffectInfo: AuxEffectInfo = AuxEffectInfo(AuxEffectInfo.NO_AUX_EFFECT_ID, 0f)
    private var preferredDevice: AudioDeviceInfo? = null
    private var playbackParameters: PlaybackParameters = PlaybackParameters.DEFAULT
    private var skipSilenceEnabled: Boolean = false
    private var volume: Float = 1.0f
    private var outputStreamOffsetUs: Long = 0L

    private var configuredFormat: Format? = null
    private var configuredBufferSize: Int = 0
    private var configuredOutputChannels: IntArray? = null
    private var isPlaying: Boolean = false

    private fun currentSinkLocked(): AudioSink = if (activeCastMode) castSink else localSink

    /**
     * Switches between [localSink] and [castSink] if [CastAudioModeController.isCastMode] has changed.
     * Safe to invoke from the player's playback thread (either inline during callbacks or via `PlayerMessage`).
     */
    fun syncModeIfNeeded() {
        synchronized(lock) {
            val desired = castModeController.isCastMode
            if (activeCastMode == desired) return

            val oldSink = currentSinkLocked()
            runCatching { oldSink.pause() }
            runCatching { oldSink.reset() }

            activeCastMode = desired
            val newSink = currentSinkLocked()

            listener?.let { newSink.setListener(it) }
            playerId?.let { newSink.setPlayerId(it) }
            newSink.setClock(clock)
            newSink.setAudioAttributes(audioAttributes)
            if (!desired && audioSessionId != 0) {
                newSink.setAudioSessionId(audioSessionId)
            }
            newSink.setAuxEffectInfo(auxEffectInfo)
            newSink.setPreferredDevice(preferredDevice)
            newSink.setPlaybackParameters(playbackParameters)
            newSink.setSkipSilenceEnabled(skipSilenceEnabled)
            newSink.setVolume(if (desired) 1.0f else volume)
            newSink.setOutputStreamOffsetUs(outputStreamOffsetUs)

            val fmt = configuredFormat
            if (fmt != null) {
                newSink.configure(fmt, configuredBufferSize, configuredOutputChannels)
            }
            if (isPlaying) {
                newSink.play()
            }
            listener?.onPositionDiscontinuity()
        }
    }

    override fun setListener(listener: AudioSink.Listener) {
        synchronized(lock) {
            this.listener = listener
            localSink.setListener(listener)
            castSink.setListener(listener)
        }
    }

    override fun setPlayerId(playerId: PlayerId?) {
        synchronized(lock) {
            this.playerId = playerId
            localSink.setPlayerId(playerId)
            castSink.setPlayerId(playerId)
        }
    }

    override fun setClock(clock: Clock) {
        synchronized(lock) {
            this.clock = clock
            localSink.setClock(clock)
            castSink.setClock(clock)
        }
    }

    override fun supportsFormat(format: Format): Boolean {
        return synchronized(lock) {
            syncModeIfNeeded()
            currentSinkLocked().supportsFormat(format)
        }
    }

    override fun getFormatSupport(format: Format): Int {
        return synchronized(lock) {
            syncModeIfNeeded()
            currentSinkLocked().getFormatSupport(format)
        }
    }

    override fun getFormatOffloadSupport(format: Format): AudioOffloadSupport {
        return synchronized(lock) {
            syncModeIfNeeded()
            if (activeCastMode) AudioOffloadSupport.DEFAULT_UNSUPPORTED else localSink.getFormatOffloadSupport(format)
        }
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        return synchronized(lock) {
            syncModeIfNeeded()
            currentSinkLocked().getCurrentPositionUs(sourceEnded)
        }
    }

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        synchronized(lock) {
            syncModeIfNeeded()
            configuredFormat = inputFormat
            configuredBufferSize = specifiedBufferSize
            configuredOutputChannels = outputChannels?.clone()
            currentSinkLocked().configure(inputFormat, specifiedBufferSize, outputChannels)
        }
    }

    override fun play() {
        synchronized(lock) {
            syncModeIfNeeded()
            isPlaying = true
            currentSinkLocked().play()
        }
    }

    override fun handleDiscontinuity() {
        synchronized(lock) {
            syncModeIfNeeded()
            currentSinkLocked().handleDiscontinuity()
        }
    }

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int,
    ): Boolean {
        return synchronized(lock) {
            syncModeIfNeeded()
            currentSinkLocked().handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        }
    }

    override fun playToEndOfStream() {
        synchronized(lock) {
            syncModeIfNeeded()
            currentSinkLocked().playToEndOfStream()
        }
    }

    override fun isEnded(): Boolean {
        return synchronized(lock) {
            syncModeIfNeeded()
            currentSinkLocked().isEnded()
        }
    }

    override fun hasPendingData(): Boolean {
        return synchronized(lock) {
            syncModeIfNeeded()
            currentSinkLocked().hasPendingData()
        }
    }

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        synchronized(lock) {
            this.playbackParameters = playbackParameters
            syncModeIfNeeded()
            currentSinkLocked().setPlaybackParameters(playbackParameters)
        }
    }

    override fun getPlaybackParameters(): PlaybackParameters {
        return synchronized(lock) {
            syncModeIfNeeded()
            currentSinkLocked().getPlaybackParameters()
        }
    }

    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) {
        synchronized(lock) {
            this.skipSilenceEnabled = skipSilenceEnabled
            syncModeIfNeeded()
            currentSinkLocked().setSkipSilenceEnabled(skipSilenceEnabled)
        }
    }

    override fun getSkipSilenceEnabled(): Boolean {
        return synchronized(lock) { skipSilenceEnabled }
    }

    override fun setAudioAttributes(audioAttributes: AudioAttributes) {
        synchronized(lock) {
            this.audioAttributes = audioAttributes
            syncModeIfNeeded()
            currentSinkLocked().setAudioAttributes(audioAttributes)
        }
    }

    override fun getAudioAttributes(): AudioAttributes {
        return synchronized(lock) { audioAttributes }
    }

    override fun setAudioSessionId(audioSessionId: Int) {
        synchronized(lock) {
            this.audioSessionId = audioSessionId
            localSink.setAudioSessionId(audioSessionId)
        }
    }

    override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) {
        synchronized(lock) {
            this.auxEffectInfo = auxEffectInfo
            localSink.setAuxEffectInfo(auxEffectInfo)
        }
    }

    override fun setPreferredDevice(audioDeviceInfo: AudioDeviceInfo?) {
        synchronized(lock) {
            this.preferredDevice = audioDeviceInfo
            localSink.setPreferredDevice(audioDeviceInfo)
        }
    }

    override fun setOutputStreamOffsetUs(outputStreamOffsetUs: Long) {
        synchronized(lock) {
            this.outputStreamOffsetUs = outputStreamOffsetUs
            currentSinkLocked().setOutputStreamOffsetUs(outputStreamOffsetUs)
        }
    }

    override fun enableTunnelingV21() {
        synchronized(lock) {
            if (!activeCastMode) localSink.enableTunnelingV21()
        }
    }

    override fun disableTunneling() {
        synchronized(lock) {
            localSink.disableTunneling()
        }
    }

    override fun setOffloadMode(offloadMode: Int) {
        synchronized(lock) {
            if (!activeCastMode) localSink.setOffloadMode(offloadMode)
        }
    }

    override fun setOffloadDelayPadding(delayInFrames: Int, paddingInFrames: Int) {
        synchronized(lock) {
            if (!activeCastMode) localSink.setOffloadDelayPadding(delayInFrames, paddingInFrames)
        }
    }

    override fun setVolume(volume: Float) {
        synchronized(lock) {
            this.volume = volume
            if (!activeCastMode) {
                localSink.setVolume(volume)
            }
        }
    }

    override fun pause() {
        synchronized(lock) {
            isPlaying = false
            syncModeIfNeeded()
            currentSinkLocked().pause()
        }
    }

    override fun flush() {
        synchronized(lock) {
            syncModeIfNeeded()
            currentSinkLocked().flush()
        }
    }

    override fun reset() {
        synchronized(lock) {
            configuredFormat = null
            isPlaying = false
            localSink.reset()
            castSink.reset()
        }
    }

    override fun release() {
        synchronized(lock) {
            localSink.release()
            castSink.release()
        }
    }
}
