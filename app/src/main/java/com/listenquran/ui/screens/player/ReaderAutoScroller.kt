package com.listenquran.ui.screens.player

import android.util.Log
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**
 * Triggers indicating why a scroll was initiated.
 */
enum class ScrollReason {
    PLAYBACK, // Normal forward playback
    SEEK,     // User scrubbed media slider or tapped a verse/word
    TAP,      // User tapped an ayah card directly
    JUMP      // Queue transition or "Follow recitation" chip tap
}

/**
 * Target verse and word to track.
 */
data class ScrollTarget(
    val verseIndex: Int,
    val wordIndex: Int = 1,
    val reason: ScrollReason = ScrollReason.PLAYBACK
)

/**
 * Output of the deterministic decision logic.
 */
sealed interface ScrollDecision {
    data object NoOp : ScrollDecision
    data class Animate(val delta: Float) : ScrollDecision
    data class InstantJump(val itemIndex: Int, val scrollOffset: Int = 0) : ScrollDecision
}

/**
 * Single, deterministic auto-scroll controller for the reader screen.
 *
 * Enforces the following core invariants:
 * 1. Single Owner: One coroutine owns the LazyListState scroll session at a time.
 * 2. Conflation: Latest target wins; intermediate targets are dropped without queuing.
 * 3. Direction Rule: During forward PLAYBACK, negative deltas (upward scrolls) are strictly clamped to 0.
 * 4. Comfort Zone: Only scrolls when the active target is outside the safe band (15% to 65% of visible height).
 * 5. Smooth Continuation: When a new target arrives mid-scroll, it re-evaluates remaining delta dynamically without cancelling.
 * 6. User Control: Pauses on user touch drag; resumes on next verse change, 3s idle, or SEEK/TAP.
 */
class ReaderAutoScroller(
    val listState: LazyListState,
    private val coroutineScope: CoroutineScope
) {
    companion object {
        private const val TAG = "ReaderAutoScroller"
        const val DEFAULT_ANIMATION_DURATION_MS = 200L
    }

    data class ViewportGeometry(
        val visibleTop: Float = 0f,
        val visibleHeight: Float,
        val safeZoneTopRatio: Float = 0.15f,
        val safeZoneBottomRatio: Float = 0.65f,
        val targetTopRatio: Float = 0.35f
    ) {
        val safeTop: Float
            get() = visibleTop + (visibleHeight * safeZoneTopRatio)

        val safeBottom: Float
            get() = visibleTop + (visibleHeight * safeZoneBottomRatio)

        val targetTop: Float
            get() = visibleTop + (visibleHeight * targetTopRatio)
    }

    // State flows
    private val _isAutoFollowPaused = MutableStateFlow(false)
    val isAutoFollowPaused: StateFlow<Boolean> = _isAutoFollowPaused.asStateFlow()

    // Conflated trigger channel
    private val triggerSignal = Channel<Unit>(Channel.CONFLATED)

    // Current active target
    @Volatile
    var currentTarget: ScrollTarget? = null
        private set

    // Cached active word bounds measured relative to LazyColumn list coordinates
    @Volatile
    var activeWordBoundsInList: Rect? = null
        private set

    @Volatile
    var activeWordVerseIndex: Int = -1
        private set

    @Volatile
    var activeWordPosition: Int = -1
        private set

    // Live viewport dimensions
    var lazyListCoordinates: LayoutCoordinates? = null
    var viewportHeight: Float = 0f
    var bottomPaddingPx: Float = 0f

    // User drag / idle job
    private var idleResumeJob: Job? = null
    private var workerJob: Job? = null

    init {
        startWorker()
    }

    private fun startWorker() {
        workerJob?.cancel()
        workerJob = coroutineScope.launch {
            for (signal in triggerSignal) {
                while (isActive) {
                    val target = currentTarget ?: break
                    if (_isAutoFollowPaused.value) break

                    // Step 1: Ensure target item is present in layoutInfo
                    val isLaidOut = listState.layoutInfo.visibleItemsInfo.any { it.index == target.verseIndex }
                    if (!isLaidOut) {
                        if (target.reason == ScrollReason.SEEK || target.reason == ScrollReason.JUMP) {
                            listState.scrollToItem(target.verseIndex)
                        } else {
                            listState.animateScrollToItem(target.verseIndex)
                        }
                        // Settle layout
                        withFrameNanos { }
                    }

                    // Step 2: Perform comfort-zone scroll
                    val finished = executeComfortZoneScroll(target)
                    if (finished) break
                }
            }
        }
    }

    /**
     * Submits a new active target. Conflates intermediate targets.
     */
    fun submitTarget(target: ScrollTarget) {
        val previousTarget = currentTarget
        currentTarget = target

        // If active verse changed, resume auto-follow if it was paused
        if (previousTarget != null && previousTarget.verseIndex != target.verseIndex) {
            _isAutoFollowPaused.value = false
            idleResumeJob?.cancel()
        }

        // Seeking or tapping immediately clears user pause
        if (target.reason == ScrollReason.SEEK || target.reason == ScrollReason.TAP) {
            _isAutoFollowPaused.value = false
            idleResumeJob?.cancel()
        }

        triggerSignal.trySend(Unit)
    }

    /**
     * Reports active word layout positioning.
     * Only called for the single active word in the active verse.
     */
    fun onActiveWordPositioned(
        verseIndex: Int,
        wordPosition: Int,
        wordCoordinates: LayoutCoordinates
    ) {
        val listCoords = lazyListCoordinates
        if (listCoords != null && listCoords.isAttached && wordCoordinates.isAttached) {
            val bounds = listCoords.localBoundingBoxOf(wordCoordinates)
            // Ensure bounds have settled (non-zero height)
            if (bounds.height > 0f) {
                activeWordBoundsInList = bounds
                activeWordVerseIndex = verseIndex
                activeWordPosition = wordPosition
                triggerSignal.trySend(Unit)
            }
        }
    }

    /**
     * Handles user touch dragging. Pauses auto-follow on drag, resumes after 3s idle.
     */
    fun onUserDrag(isDragging: Boolean) {
        if (isDragging) {
            _isAutoFollowPaused.value = true
            idleResumeJob?.cancel()
        } else if (_isAutoFollowPaused.value) {
            idleResumeJob?.cancel()
            idleResumeJob = coroutineScope.launch {
                delay(3000L)
                _isAutoFollowPaused.value = false
                triggerSignal.trySend(Unit)
            }
        }
    }

    /**
     * Resumes auto-follow explicitly (e.g. Follow recitation chip tap).
     */
    fun resumeFollow(reason: ScrollReason = ScrollReason.JUMP) {
        _isAutoFollowPaused.value = false
        idleResumeJob?.cancel()
        val target = currentTarget
        if (target != null) {
            currentTarget = target.copy(reason = reason)
            triggerSignal.trySend(Unit)
        }
    }

    /**
     * Releases active coroutine jobs and channels when the reader is disposed.
     */
    fun dispose() {
        workerJob?.cancel()
        idleResumeJob?.cancel()
        triggerSignal.close()
    }

    /**
     * Executes the comfort zone scroll loop.
     * Returns true if settled/finished, false if needs continuation.
     */
    private suspend fun executeComfortZoneScroll(initialTarget: ScrollTarget): Boolean {
        val listCoords = lazyListCoordinates ?: return true
        if (!listCoords.isAttached) return true

        val vHeight = (viewportHeight - bottomPaddingPx).coerceAtLeast(100f)
        val geometry = ViewportGeometry(visibleTop = 0f, visibleHeight = vHeight)

        val target = currentTarget ?: initialTarget
        val bounds = getLiveTargetBounds(target) ?: return true

        val decision = computeDecision(
            wordTop = bounds.top,
            wordBottom = bounds.bottom,
            geometry = geometry,
            reason = target.reason,
            isPaused = _isAutoFollowPaused.value,
            verseIndex = target.verseIndex
        )

        return when (decision) {
            is ScrollDecision.NoOp -> true
            is ScrollDecision.InstantJump -> {
                listState.scrollToItem(decision.itemIndex, decision.scrollOffset)
                true
            }
            is ScrollDecision.Animate -> {
                val totalDelta = decision.delta
                if (abs(totalDelta) < 3f) return true

                val durationMs = DEFAULT_ANIMATION_DURATION_MS
                val startTime = System.currentTimeMillis()
                var previousProgress = 0f

                try {
                    listState.scroll(MutatePriority.Default) {
                        while (true) {
                            if (_isAutoFollowPaused.value) break

                            val liveTarget = currentTarget ?: break
                            val liveBounds = getLiveTargetBounds(liveTarget)
                            val remainingDelta = if (liveBounds != null) {
                                val liveDecision = computeDecision(
                                    wordTop = liveBounds.top,
                                    wordBottom = liveBounds.bottom,
                                    geometry = geometry,
                                    reason = liveTarget.reason,
                                    isPaused = false,
                                    verseIndex = liveTarget.verseIndex
                                )
                                when (liveDecision) {
                                    is ScrollDecision.Animate -> liveDecision.delta
                                    is ScrollDecision.NoOp -> 0f
                                    is ScrollDecision.InstantJump -> break
                                }
                            } else {
                                totalDelta * (1f - previousProgress)
                            }

                            if (abs(remainingDelta) < 3f) {
                                break
                            }

                            val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(0L)
                            val rawProgress = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
                            // Quadratic decelerate curve: 1 - (1 - progress)^2
                            val easedProgress = 1f - (1f - rawProgress) * (1f - rawProgress)
                            val progressDelta = (easedProgress - previousProgress).coerceAtLeast(0f)
                            previousProgress = easedProgress

                            val step = remainingDelta * (if (progressDelta > 0f) progressDelta / (1f - (previousProgress - progressDelta)).coerceAtLeast(0.01f) else 0f)
                            if (abs(step) > 0.05f) {
                                scrollBy(step)
                            }

                            if (rawProgress >= 1f) break
                            withFrameNanos { }
                        }
                    }
                } catch (e: Exception) {
                    if (e !is CancellationException) {
                        Log.e(TAG, "Error during smooth scroll", e)
                    }
                }
                true
            }
        }
    }

    /**
     * Retrieves the live viewport bounds for the given target.
     */
    fun getLiveTargetBounds(target: ScrollTarget): Rect? {
        // If word-level positioning is available for this active target
        if (activeWordVerseIndex == target.verseIndex &&
            activeWordPosition == target.wordIndex &&
            activeWordBoundsInList != null
        ) {
            return activeWordBoundsInList
        }

        // Fallback to Ayah item bounds from layoutInfo (e.g. WBW off or before word attaches)
        val item = listState.layoutInfo.visibleItemsInfo.find { it.index == target.verseIndex }
        if (item != null) {
            val top = item.offset.toFloat()
            val bottom = (item.offset + item.size).toFloat()
            return Rect(left = 0f, top = top, right = 0f, bottom = bottom)
        }

        return null
    }

    /**
     * Pure calculation function for scroll decision.
     */
    fun computeDecision(
        wordTop: Float,
        wordBottom: Float,
        geometry: ViewportGeometry,
        reason: ScrollReason,
        isPaused: Boolean,
        verseIndex: Int = 0
    ): ScrollDecision {
        if (isPaused || geometry.visibleHeight <= 0f) {
            return ScrollDecision.NoOp
        }

        val safeTop = geometry.safeTop
        val safeBottom = geometry.safeBottom
        val targetTop = geometry.targetTop

        return when {
            // Target is below the safe bottom boundary
            wordBottom > safeBottom -> {
                val delta = wordTop - targetTop
                if (delta > 2f * geometry.visibleHeight) {
                    ScrollDecision.InstantJump(verseIndex)
                } else if (delta > 3f) {
                    ScrollDecision.Animate(delta)
                } else {
                    ScrollDecision.NoOp
                }
            }

            // Target is above the safe top boundary
            wordTop < safeTop -> {
                if (reason == ScrollReason.PLAYBACK) {
                    // DIRECTION RULE: During normal forward playback, NEVER scroll upward.
                    // Clamped to 0 and logged.
                    try {
                        Log.w(TAG, "Negative delta clamped to 0 during PLAYBACK: wordTop=$wordTop < safeTop=$safeTop")
                    } catch (_: Throwable) {
                        println("[$TAG] Negative delta clamped to 0 during PLAYBACK: wordTop=$wordTop < safeTop=$safeTop")
                    }
                    ScrollDecision.NoOp
                } else {
                    // SEEK, TAP, JUMP allows scrolling upward
                    val delta = wordTop - targetTop
                    if (abs(delta) > 2f * geometry.visibleHeight) {
                        ScrollDecision.InstantJump(verseIndex)
                    } else if (abs(delta) > 3f) {
                        ScrollDecision.Animate(delta)
                    } else {
                        ScrollDecision.NoOp
                    }
                }
            }

            // Inside comfort zone
            else -> ScrollDecision.NoOp
        }
    }
}
