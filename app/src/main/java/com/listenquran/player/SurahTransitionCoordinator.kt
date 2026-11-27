package com.listenquran.player

import androidx.media3.common.Player
import com.listenquran.domain.model.VerseTiming
import com.listenquran.player.cast.FadeGainAudioProcessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
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
 * and soft local pause fade-out. Never applies fade-in so the first word after Bismillah
 * or at the start of a surah is always heard clearly at full gain.
 */
@Singleton
class SurahTransitionCoordinator internal constructor(
    private val mainFadeGainProcessor: FadeGainAudioProcessor,
    private val playBismillahCue: suspend (reciterId: Int, playbackSpeed: Float, isCastMuted: Boolean) -> Boolean,
    private val setCueMuted: suspend (Boolean) -> Unit,
    private val stopCue: suspend () -> Unit,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) {
    @Inject
    constructor(
        mainFadeGainProcessor: FadeGainAudioProcessor,
        cuePlayer: CuePlayer,
    ) : this(
        mainFadeGainProcessor = mainFadeGainProcessor,
        playBismillahCue = { reciterId, speed, isCastMuted ->
            cuePlayer.playBismillah(reciterId, speed, isCastMuted)
        },
        setCueMuted = { cuePlayer.setMuted(it) },
        stopCue = { cuePlayer.stop() },
        dispatcher = Dispatchers.Main.immediate,
    )

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    @Volatile
    var isInTransition: Boolean = false
        private set

    private var transitionJob: Job? = null
    private var fadeOutTriggeredForMediaId: String? = null

    fun prepareBoundaryTransition() {
        isInTransition = true
    }

    fun clearBoundaryTransition() {
        if (transitionJob?.isActive != true) {
            isInTransition = false
        }
    }

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
        gapMs: Int = 600,
    ) {
        if (fadeSeconds <= 0 || gapMs <= 0 || isHifz || isRadio || repeatMode == Player.REPEAT_MODE_ONE) return
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
        wasPausedAtBoundary: Boolean = false,
        emitCastSilenceMs: (suspend (Long) -> Unit)? = null,
        beforePlayMain: (() -> Unit)? = null,
        onFinished: () -> Unit = {},
    ) {
        transitionJob?.cancel()
        isInTransition = true
        transitionJob = scope.launch {
            try {
                val mightHandleTransition = gapMs > 0 ||
                    (bismillahEnabled && targetSurahId != 1 && targetSurahId != 9)
                var pausedBeforeLookup = false
                if (mightHandleTransition) {
                    pauseMain()
                    pausedBeforeLookup = true
                }

                val shouldPlayBismillah = if (bismillahEnabled) {
                    val timings = resolveTimings()
                    BismillahRule.shouldPlayBismillah(targetSurahId, timings)
                } else {
                    false
                }

                // True gapless: when gapMs is 0 and no Bismillah cue is needed, do not pause or seek.
                if (gapMs <= 0 && !shouldPlayBismillah) {
                    mainFadeGainProcessor.setGainImmediate(1f)
                    if (wasPausedAtBoundary || pausedBeforeLookup) {
                        beforePlayMain?.invoke()
                        playMain()
                    }
                    return@launch
                }

                if (!pausedBeforeLookup) {
                    pauseMain()
                }
                seekMainToZero()
                if (gapMs > 0) {
                    if (isCastingProvider() && emitCastSilenceMs != null) {
                        emitCastSilenceMs(gapMs.toLong())
                    } else {
                        delay(gapMs.toLong())
                    }
                }
                if (shouldPlayBismillah) {
                    playBismillahCue(targetReciterId, playbackSpeed, isCastingProvider())
                    val breathMs = if (gapMs > 0) 150L else 100L
                    if (isCastingProvider() && emitCastSilenceMs != null) {
                        emitCastSilenceMs(breathMs)
                    } else {
                        delay(breathMs)
                    }
                }
                beforePlayMain?.invoke()
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

    fun runStartBismillah(
        targetSurahId: Int,
        targetReciterId: Int,
        resolveTimings: suspend () -> List<VerseTiming>,
        playbackSpeed: Float,
        isCastingProvider: () -> Boolean,
        pauseMain: () -> Unit,
        playMain: () -> Unit,
        emitCastSilenceMs: (suspend (Long) -> Unit)? = null,
        awaitMainPrepared: (suspend () -> Unit)? = null,
        beforePlayMain: (() -> Unit)? = null,
        onFinished: () -> Unit = {},
    ) {
        transitionJob?.cancel()
        isInTransition = true
        transitionJob = scope.launch {
            try {
                pauseMain()
                val timings = resolveTimings()
                if (BismillahRule.shouldPlayBismillah(targetSurahId, timings)) {
                    playBismillahCue(targetReciterId, playbackSpeed, isCastingProvider())
                    if (isCastingProvider() && emitCastSilenceMs != null) {
                        emitCastSilenceMs(150L)
                    } else {
                        delay(150L)
                    }
                }
                awaitMainPrepared?.invoke()
                beforePlayMain?.invoke()
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

    fun fadeOutAndPause(durationMs: Long = 250L, onPause: () -> Unit) {
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

    fun fadeInAndPlay(durationMs: Long = 0L, onPlay: () -> Unit) {
        transitionJob?.cancel()
        mainFadeGainProcessor.setGainImmediate(1f)
        onPlay()
    }

    fun onCastMuteChanged(isCastMuted: Boolean) {
        scope.launch {
            setCueMuted(isCastMuted)
        }
    }

    fun cancel() {
        transitionJob?.cancel()
        transitionJob = null
        isInTransition = false
        mainFadeGainProcessor.setGainImmediate(1f)
        scope.launch {
            stopCue()
        }
    }
}
