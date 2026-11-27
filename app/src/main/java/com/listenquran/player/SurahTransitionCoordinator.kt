package com.listenquran.player

import androidx.media3.common.Player
import com.listenquran.domain.model.VerseTiming
import com.listenquran.player.cast.FadeGainAudioProcessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates smooth surah end fade-out, inter-surah silence gap, optional Bismillah cue,
 * fade-in, and soft local pause/play fades.
 */
@Singleton
class SurahTransitionCoordinator @Inject constructor(
    private val mainFadeGainProcessor: FadeGainAudioProcessor,
    private val cuePlayer: CuePlayer,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    var isInTransition: Boolean = false
        private set

    private var transitionJob: Job? = null
    private var fadeOutTriggeredForMediaId: String? = null

    fun onMediaItemChanged(mediaId: String?) {
        fadeOutTriggeredForMediaId = null
    }

    fun onProgressTick(
        mediaId: String?,
        rawPosMs: Long,
        durationMs: Long,
        isHifz: Boolean,
        repeatMode: Int,
        isRadio: Boolean,
        fadeSeconds: Int,
    ) {
        if (fadeSeconds <= 0 || isHifz || isRadio || repeatMode == Player.REPEAT_MODE_ONE) return
        if (durationMs <= 0L || mediaId == null || fadeOutTriggeredForMediaId == mediaId) return

        val fadeMs = fadeSeconds * 1000L
        val remainingMs = durationMs - rawPosMs
        // Include ~250ms sink lead so the fade finishes cleanly right before ExoPlayer transitions.
        if (remainingMs in 1L..(fadeMs + 250L)) {
            fadeOutTriggeredForMediaId = mediaId
            mainFadeGainProcessor.fadeTo(0f, fadeMs)
        }
    }

    fun runAutoTransition(
        targetSurahId: Int,
        targetReciterId: Int,
        resolveTimings: suspend () -> List<VerseTiming>,
        playbackSpeed: Float,
        fadeSeconds: Int,
        gapMs: Int,
        bismillahEnabled: Boolean,
        isCastingProvider: () -> Boolean,
        pauseMain: () -> Unit,
        seekMainToZero: () -> Unit,
        playMain: () -> Unit,
        onFinished: () -> Unit = {},
    ) {
        transitionJob?.cancel()
        transitionJob = scope.launch {
            isInTransition = true
            try {
                pauseMain()
                seekMainToZero()
                if (gapMs > 0) {
                    delay(gapMs.toLong())
                }
                if (bismillahEnabled) {
                    val timings = resolveTimings()
                    if (BismillahRule.shouldPlayBismillah(targetSurahId, timings)) {
                        cuePlayer.playBismillah(
                            reciterId = targetReciterId,
                            playbackSpeed = playbackSpeed,
                            isCastMuted = isCastingProvider(),
                        )
                        delay(300L)
                    }
                }
                if (fadeSeconds > 0) {
                    mainFadeGainProcessor.setGainImmediate(0f)
                } else {
                    mainFadeGainProcessor.setGainImmediate(1f)
                }
                playMain()
                if (fadeSeconds > 0) {
                    mainFadeGainProcessor.fadeTo(1f, fadeSeconds * 1000L)
                }
            } catch (ce: CancellationException) {
                mainFadeGainProcessor.setGainImmediate(1f)
                throw ce
            } finally {
                isInTransition = false
                onFinished()
            }
        }
    }

    fun runStartBismillah(
        targetSurahId: Int,
        targetReciterId: Int,
        resolveTimings: suspend () -> List<VerseTiming>,
        playbackSpeed: Float,
        isCastingProvider: () -> Boolean,
        pauseMain: () -> Unit,
        playMain: () -> Unit,
        onFinished: () -> Unit = {},
    ) {
        transitionJob?.cancel()
        transitionJob = scope.launch {
            isInTransition = true
            try {
                val timings = resolveTimings()
                if (BismillahRule.shouldPlayBismillah(targetSurahId, timings)) {
                    pauseMain()
                    cuePlayer.playBismillah(
                        reciterId = targetReciterId,
                        playbackSpeed = playbackSpeed,
                        isCastMuted = isCastingProvider(),
                    )
                    delay(250L)
                }
                mainFadeGainProcessor.setGainImmediate(1f)
                playMain()
            } catch (ce: CancellationException) {
                mainFadeGainProcessor.setGainImmediate(1f)
                throw ce
            } finally {
                isInTransition = false
                onFinished()
            }
        }
    }

    fun fadeOutAndPause(durationMs: Long = 300L, onPause: () -> Unit) {
        transitionJob?.cancel()
        transitionJob = scope.launch {
            try {
                mainFadeGainProcessor.fadeTo(0f, durationMs)
                delay(durationMs + 120L)
                onPause()
            } finally {
                mainFadeGainProcessor.setGainImmediate(1f)
            }
        }
    }

    fun fadeInAndPlay(durationMs: Long = 300L, onPlay: () -> Unit) {
        transitionJob?.cancel()
        mainFadeGainProcessor.setGainImmediate(0f)
        onPlay()
        mainFadeGainProcessor.fadeTo(1f, durationMs)
    }

    fun onCastMuteChanged(isCastMuted: Boolean) {
        scope.launch {
            cuePlayer.setMuted(isCastMuted)
        }
    }

    fun cancel() {
        transitionJob?.cancel()
        transitionJob = null
        isInTransition = false
        mainFadeGainProcessor.setGainImmediate(1f)
        scope.launch {
            cuePlayer.stop()
        }
    }
}
