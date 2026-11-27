package com.listenquran.player

import com.listenquran.domain.model.VerseTiming

object BismillahRule {

    private const val PREAMBLE_THRESHOLD_MS = 3000L

    /**
     * Returns true when we should play the reciter's Bismillah cue before starting [targetSurahId].
     *
     * Rules:
     * - Never before Surah 1 (Al-Fatihah starts with Bismillah as Ayah 1) or Surah 9 (At-Tawbah).
     * - Never when timing metadata is empty (cannot verify if recording already includes Bismillah).
     * - Never when Ayah 1's first word segment (or timestampFrom when segments are absent)
     *   begins at >= 3000ms, indicating the audio file already contains a Bismillah preamble.
     */
    fun shouldPlayBismillah(
        targetSurahId: Int,
        targetSurahTimings: List<VerseTiming>,
    ): Boolean {
        if (targetSurahId == 1 || targetSurahId == 9) return false
        if (targetSurahTimings.isEmpty()) return false

        val v1 = targetSurahTimings.firstOrNull { it.verseKey.endsWith(":1") }
            ?: targetSurahTimings.first()

        val firstSpeechMs = if (v1.segments.isNotEmpty()) {
            v1.segments.minOf { it.startMs }
        } else {
            v1.timestampFrom
        }

        return firstSpeechMs < PREAMBLE_THRESHOLD_MS
    }
}
