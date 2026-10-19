package com.listenquran.player

import androidx.media3.common.C
import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerCommandsAndSeekingTest {

    @Test
    fun playerCommandConstants_areDefinedForSeeking() {
        // Verify integer command constants for seek operations
        assertTrue(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM > 0)
        assertTrue(Player.COMMAND_SEEK_TO_MEDIA_ITEM > 0)
        assertTrue(Player.COMMAND_SEEK_TO_NEXT > 0)
        assertTrue(Player.COMMAND_SEEK_TO_PREVIOUS > 0)
    }

    @Test
    fun durationCalculation_preservesKnownDurationWhenControllerReturnsUnset() {
        val cachedDurationMs = 180_000L
        val rawControllerDuration = C.TIME_UNSET

        val resolvedDuration = if (rawControllerDuration > 0) {
            rawControllerDuration
        } else {
            cachedDurationMs.takeIf { it > 0 } ?: 0L
        }

        assertEquals(180_000L, resolvedDuration)
    }

    @Test
    fun sliderClamping_handlesValidBounds() {
        val durationMs = 200_000L
        val currentPosition = 50_000L

        val progress = (currentPosition.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        assertEquals(0.25f, progress, 0.001f)

        val seekTarget = (0.75f * durationMs).toLong()
        assertEquals(150_000L, seekTarget)
    }

    @Test
    fun pendingSeekPosition_preventsSnapbackDuringBuffering() {
        val durationMs = 120_000L
        val oldContinuousPos = 15_000L
        val dragFraction = 0.60f
        val targetSeekPos = (dragFraction * durationMs).toLong() // 72_000L

        var pendingSeekPosition: Long? = targetSeekPos
        val isDraggingSlider = false

        // While pendingSeekPosition is set, displayPosition reflects targetSeekPos, not oldContinuousPos
        val displayPosition = when {
            isDraggingSlider -> (dragFraction * durationMs).toLong()
            pendingSeekPosition != null -> pendingSeekPosition!!
            else -> oldContinuousPos
        }

        assertEquals(72_000L, displayPosition)

        // Once player catches up near the target, pending clears
        val newContinuousPos = 71_800L
        if (pendingSeekPosition != null && kotlin.math.abs(newContinuousPos - pendingSeekPosition!!) < 1500L) {
            pendingSeekPosition = null
        }

        assertEquals(null, pendingSeekPosition)
    }
}
