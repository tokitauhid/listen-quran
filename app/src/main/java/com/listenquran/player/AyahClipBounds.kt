package com.listenquran.player

import com.listenquran.domain.model.VerseTiming

/**
 * Resolved millisecond clipping window for a bounded ayah range [startAyah, endAyah]
 * within a Surah's full-chapter audio file.
 */
data class AyahClipBounds(
    val startAyah: Int,
    val endAyah: Int,
    val startMs: Long,
    val endMs: Long,
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
}

/**
 * Resolves millisecond clip bounds for a bounded ayah range.
 * Returns null if [endAyah] is null (full surah or unbounded mid-surah entry),
 * if bounds are invalid, or if matching verse timings are unavailable.
 */
fun resolveAyahClipBounds(
    surahId: Int,
    startAyah: Int,
    endAyah: Int?,
    timings: List<VerseTiming>,
): AyahClipBounds? {
    if (endAyah == null) return null
    val safeStart = startAyah.coerceAtLeast(1)
    if (endAyah < safeStart) return null

    val endTiming = timings.find { it.verseKey == "$surahId:$endAyah" } ?: return null
    val startMs = if (safeStart == 1) {
        0L
    } else {
        val startTiming = timings.find { it.verseKey == "$surahId:$safeStart" } ?: return null
        startTiming.timestampFrom.coerceAtLeast(0L)
    }
    val endMs = endTiming.timestampTo
    if (endMs <= startMs) return null

    return AyahClipBounds(
        startAyah = safeStart,
        endAyah = endAyah,
        startMs = startMs,
        endMs = endMs,
    )
}

/**
 * Computes the initial playback position passed to Media3 setMediaItems.
 * Clipped items always start at 0ms relative to their clip window so we never double-offset.
 */
fun computeInitialStartPositionMs(
    isClipped: Boolean,
    surahId: Int,
    startAyah: Int,
    timings: List<VerseTiming>,
): Long {
    if (isClipped) return 0L
    if (startAyah > 1) {
        return timings.find { it.verseKey == "$surahId:$startAyah" }
            ?.timestampFrom
            ?.coerceAtLeast(0L)
            ?: 0L
    }
    return 0L
}
