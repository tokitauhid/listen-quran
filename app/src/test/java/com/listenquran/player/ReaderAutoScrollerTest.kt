package com.listenquran.player

import androidx.compose.foundation.lazy.LazyListState
import com.listenquran.ui.screens.player.ReaderAutoScroller
import com.listenquran.ui.screens.player.ScrollDecision
import com.listenquran.ui.screens.player.ScrollReason
import com.listenquran.ui.screens.player.ScrollTarget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderAutoScrollerTest {

    private val standardGeometry = ReaderAutoScroller.ViewportGeometry(
        visibleTop = 0f,
        visibleHeight = 1000f,
        safeZoneTopRatio = 0.15f,    // safeTop = 150f
        safeZoneBottomRatio = 0.65f, // safeBottom = 650f
        targetTopRatio = 0.35f       // targetTop = 350f
    )

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val activeScrollers = mutableListOf<ReaderAutoScroller>()

    private fun createScroller(): ReaderAutoScroller {
        val listState = LazyListState()
        val scroller = ReaderAutoScroller(listState, testScope)
        activeScrollers.add(scroller)
        return scroller
    }

    @org.junit.After
    fun tearDown() {
        activeScrollers.forEach { it.dispose() }
        activeScrollers.clear()
    }

    @Test
    fun testWordInsideComfortZoneReturnsNoOp() {
        val scroller = createScroller()

        // Word between safeTop (150f) and safeBottom (650f)
        val decision = scroller.computeDecision(
            wordTop = 300f,
            wordBottom = 360f,
            geometry = standardGeometry,
            reason = ScrollReason.PLAYBACK,
            isPaused = false
        )

        assertEquals(ScrollDecision.NoOp, decision)
    }

    @Test
    fun testWordBelowSafeBottomScrollsToTargetZone() {
        val scroller = createScroller()

        // Word bottom is at 720f (> 650f safeBottom)
        val wordTop = 660f
        val wordBottom = 720f

        val decision = scroller.computeDecision(
            wordTop = wordTop,
            wordBottom = wordBottom,
            geometry = standardGeometry,
            reason = ScrollReason.PLAYBACK,
            isPaused = false
        )

        // Expected delta: wordTop (660f) - targetTop (350f) = +310f
        assertTrue("Decision must be Animate", decision is ScrollDecision.Animate)
        assertEquals(310f, (decision as ScrollDecision.Animate).delta, 0.001f)
    }

    @Test
    fun testDirectionRuleForwardOnlyDuringPlayback() {
        val scroller = createScroller()

        // Word is above safeTop (e.g. 50f < 150f)
        // During normal forward PLAYBACK, negative deltas (upward scrolls) must be clamped to 0!
        val decision = scroller.computeDecision(
            wordTop = 50f,
            wordBottom = 110f,
            geometry = standardGeometry,
            reason = ScrollReason.PLAYBACK,
            isPaused = false
        )

        assertEquals("PLAYBACK must never scroll backward/upward", ScrollDecision.NoOp, decision)
    }

    @Test
    fun testAllowedBackwardOnSeekAndTapAndJump() {
        val scroller = createScroller()

        val wordTop = 50f
        val wordBottom = 110f

        // On SEEK: upward scroll is allowed!
        val seekDecision = scroller.computeDecision(
            wordTop = wordTop,
            wordBottom = wordBottom,
            geometry = standardGeometry,
            reason = ScrollReason.SEEK,
            isPaused = false
        )
        assertTrue("SEEK must allow upward animation", seekDecision is ScrollDecision.Animate)
        assertEquals(-300f, (seekDecision as ScrollDecision.Animate).delta, 0.001f)

        // On TAP: upward scroll is allowed!
        val tapDecision = scroller.computeDecision(
            wordTop = wordTop,
            wordBottom = wordBottom,
            geometry = standardGeometry,
            reason = ScrollReason.TAP,
            isPaused = false
        )
        assertTrue("TAP must allow upward animation", tapDecision is ScrollDecision.Animate)
        assertEquals(-300f, (tapDecision as ScrollDecision.Animate).delta, 0.001f)

        // On JUMP: upward scroll is allowed!
        val jumpDecision = scroller.computeDecision(
            wordTop = wordTop,
            wordBottom = wordBottom,
            geometry = standardGeometry,
            reason = ScrollReason.JUMP,
            isPaused = false
        )
        assertTrue("JUMP must allow upward animation", jumpDecision is ScrollDecision.Animate)
        assertEquals(-300f, (jumpDecision as ScrollDecision.Animate).delta, 0.001f)
    }

    @Test
    fun testLargeDistanceTriggersInstantJump() {
        val scroller = createScroller()

        // Distance > 2 * visibleHeight (> 2000f)
        val wordTop = 2600f
        val wordBottom = 2660f

        val decision = scroller.computeDecision(
            wordTop = wordTop,
            wordBottom = wordBottom,
            geometry = standardGeometry,
            reason = ScrollReason.SEEK,
            isPaused = false,
            verseIndex = 255
        )

        assertTrue("Large distance must trigger InstantJump", decision is ScrollDecision.InstantJump)
        assertEquals(255, (decision as ScrollDecision.InstantJump).itemIndex)
    }

    @Test
    fun testTallVerseProgressiveScroll() {
        val scroller = createScroller()

        // Simulating words progressing down a 3000px tall verse
        // Word 1: inside comfort zone
        val w1 = scroller.computeDecision(350f, 410f, standardGeometry, ScrollReason.PLAYBACK, false)
        assertEquals(ScrollDecision.NoOp, w1)

        // Word 10: reaches safe bottom (680f > 650f)
        val w10 = scroller.computeDecision(620f, 680f, standardGeometry, ScrollReason.PLAYBACK, false)
        assertTrue(w10 is ScrollDecision.Animate)
        assertEquals(270f, (w10 as ScrollDecision.Animate).delta, 0.001f)

        // Word 20: further down at 750f
        val w20 = scroller.computeDecision(690f, 750f, standardGeometry, ScrollReason.PLAYBACK, false)
        assertTrue(w20 is ScrollDecision.Animate)
        assertEquals(340f, (w20 as ScrollDecision.Animate).delta, 0.001f)
    }

    @Test
    fun testUserDragPausesFollow() {
        val scroller = createScroller()

        // When paused by user drag, even if word is outside comfort zone, decision is NoOp
        val decision = scroller.computeDecision(
            wordTop = 800f,
            wordBottom = 860f,
            geometry = standardGeometry,
            reason = ScrollReason.PLAYBACK,
            isPaused = true
        )

        assertEquals("Auto-follow must not scroll when paused by user drag", ScrollDecision.NoOp, decision)
    }

    @Test
    fun testZeroOrNegativeViewportReturnsNoOp() {
        val scroller = createScroller()

        val zeroGeometry = ReaderAutoScroller.ViewportGeometry(
            visibleTop = 0f,
            visibleHeight = 0f
        )

        val decision = scroller.computeDecision(
            wordTop = 500f,
            wordBottom = 560f,
            geometry = zeroGeometry,
            reason = ScrollReason.PLAYBACK,
            isPaused = false
        )

        assertEquals(ScrollDecision.NoOp, decision)
    }

    @Test
    fun testTargetConflationLatestTargetWins() {
        val scroller = createScroller()

        scroller.submitTarget(ScrollTarget(verseIndex = 1, wordIndex = 1))
        scroller.submitTarget(ScrollTarget(verseIndex = 1, wordIndex = 2))
        scroller.submitTarget(ScrollTarget(verseIndex = 2, wordIndex = 5))

        // Latest target must win
        assertEquals(2, scroller.currentTarget?.verseIndex)
        assertEquals(5, scroller.currentTarget?.wordIndex)
    }

    @Test
    fun testUserDragAndResumeLifecycle() = testScope.runTest {
        val scroller = createScroller()

        assertFalse(scroller.isAutoFollowPaused.value)

        // User starts dragging
        scroller.onUserDrag(true)
        assertTrue(scroller.isAutoFollowPaused.value)

        // User stops dragging
        scroller.onUserDrag(false)
        // Still paused immediately after release
        assertTrue(scroller.isAutoFollowPaused.value)

        // Advance 2.9 seconds -> still paused
        advanceTimeBy(2900L)
        assertTrue(scroller.isAutoFollowPaused.value)

        // Advance past 3 seconds -> resumed!
        advanceTimeBy(200L)
        assertFalse(scroller.isAutoFollowPaused.value)

        // Pausing again and calling resumeFollow explicitly
        scroller.onUserDrag(true)
        assertTrue(scroller.isAutoFollowPaused.value)
        scroller.resumeFollow()
        assertFalse(scroller.isAutoFollowPaused.value)
        scroller.dispose()
    }

    @Test
    fun testScriptedWordChangeSequenceMaintainsWordWithinComfortZoneAndZeroUpwardScrolls() {
        val scroller = createScroller()

        // 50 words distributed over a 2500px tall verse
        var currentScrollY = 0f
        var upwardScrollCount = 0

        for (wordIndex in 1..50) {
            val absoluteWordTop = 200f + (wordIndex - 1) * 45f
            val absoluteWordBottom = absoluteWordTop + 40f

            // Relative to current viewport scroll
            val relativeWordTop = absoluteWordTop - currentScrollY
            val relativeWordBottom = absoluteWordBottom - currentScrollY

            val decision = scroller.computeDecision(
                wordTop = relativeWordTop,
                wordBottom = relativeWordBottom,
                geometry = standardGeometry,
                reason = ScrollReason.PLAYBACK,
                isPaused = false
            )

            when (decision) {
                is ScrollDecision.NoOp -> {
                    // Check that the word is indeed within safe margins or hasn't exceeded safeBottom
                    assertTrue(
                        "Word $wordIndex at $relativeWordBottom must be <= safeBottom (${standardGeometry.safeBottom})",
                        relativeWordBottom <= standardGeometry.safeBottom + 1f
                    )
                }
                is ScrollDecision.Animate -> {
                    if (decision.delta < 0f) {
                        upwardScrollCount++
                    }
                    assertTrue(
                        "Delta during PLAYBACK must be positive (forward only): got ${decision.delta}",
                        decision.delta > 0f
                    )
                    // Apply scroll
                    currentScrollY += decision.delta

                    // After scroll, word should be brought to targetTop (350f)
                    val newRelativeTop = absoluteWordTop - currentScrollY
                    assertEquals(
                        "After scroll, word top should align near targetTop (350f)",
                        standardGeometry.targetTop,
                        newRelativeTop,
                        1f
                    )
                }
                is ScrollDecision.InstantJump -> {
                    // Not expected for normal word steps
                }
            }

            // Invariant 1: Upward scrolls during PLAYBACK must be strictly 0
            assertEquals("Upward scrolls during PLAYBACK must remain 0", 0, upwardScrollCount)

            // Invariant 2: Active word must always be within the visible bounds [0, visibleHeight]
            val finalRelTop = absoluteWordTop - currentScrollY
            val finalRelBottom = absoluteWordBottom - currentScrollY
            assertTrue("Word top must be >= 0", finalRelTop >= 0f)
            assertTrue("Word bottom must be <= visibleHeight", finalRelBottom <= standardGeometry.visibleHeight)
        }
        scroller.dispose()
    }

    @Test
    fun testVerseChangeResumesPausedFollow() = testScope.runTest {
        val scroller = createScroller()

        scroller.submitTarget(ScrollTarget(verseIndex = 1, wordIndex = 3))
        scroller.onUserDrag(true)
        assertTrue(scroller.isAutoFollowPaused.value)

        // When verse advances to verse 2, auto-follow resumes automatically
        scroller.submitTarget(ScrollTarget(verseIndex = 2, wordIndex = 1))
        assertFalse(scroller.isAutoFollowPaused.value)
        scroller.dispose()
    }
}
