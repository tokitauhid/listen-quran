package com.listenquran.domain.model.khatam

import org.json.JSONArray
import kotlin.math.max
import kotlin.math.min

/**
 * Represents a continuous, closed interval of global ayah indices [start, end].
 * Global ayah indices run from 1 to 6236.
 */
data class AyahRange(
    val start: Int,
    val end: Int
) : Comparable<AyahRange> {
    init {
        require(start in 1..QuranAyahMetadata.TOTAL_AYAHS) { "start ayah $start out of bounds (1..${QuranAyahMetadata.TOTAL_AYAHS})" }
        require(end in 1..QuranAyahMetadata.TOTAL_AYAHS) { "end ayah $end out of bounds (1..${QuranAyahMetadata.TOTAL_AYAHS})" }
        require(start <= end) { "start $start cannot be greater than end $end" }
    }

    val count: Int get() = end - start + 1

    operator fun contains(ayah: Int): Boolean = ayah in start..end

    override fun compareTo(other: AyahRange): Int = start.compareTo(other.start)
}

/**
 * Immutable collection of sorted, disjoint Ayah ranges representing progress toward Khatam.
 * Guarantees zero overlaps and canonical interval merging.
 */
class AyahRangeSet(ranges: List<AyahRange> = emptyList()) {

    val ranges: List<AyahRange> = mergeInternal(ranges)

    val completedCount: Int = this.ranges.sumOf { it.count }

    val isComplete: Boolean = completedCount >= QuranAyahMetadata.TOTAL_AYAHS

    val completionPercentage: Float = (completedCount.toFloat() / QuranAyahMetadata.TOTAL_AYAHS) * 100f

    /**
     * Checks if a single global ayah is marked complete.
     */
    fun contains(ayah: Int): Boolean {
        if (ayah !in 1..QuranAyahMetadata.TOTAL_AYAHS) return false
        var low = 0
        var high = ranges.size - 1
        while (low <= high) {
            val mid = (low + high).ushr(1)
            val range = ranges[mid]
            when {
                ayah < range.start -> high = mid - 1
                ayah > range.end -> low = mid + 1
                else -> return true
            }
        }
        return false
    }

    /**
     * Checks if the entire range [start, end] is marked complete.
     */
    fun containsRange(start: Int, end: Int): Boolean {
        if (start > end || start < 1 || end > QuranAyahMetadata.TOTAL_AYAHS) return false
        var current = start
        for (range in ranges) {
            if (range.end < current) continue
            if (range.start > current) return false
            if (range.end >= end) return true
            current = range.end + 1
        }
        return false
    }

    /**
     * Returns how many ayahs in [start, end] are marked complete.
     */
    fun overlapCount(start: Int, end: Int): Int {
        if (start > end || start < 1 || end > QuranAyahMetadata.TOTAL_AYAHS) return 0
        var count = 0
        for (range in ranges) {
            if (range.end < start) continue
            if (range.start > end) break
            val overlapStart = max(start, range.start)
            val overlapEnd = min(end, range.end)
            if (overlapStart <= overlapEnd) {
                count += (overlapEnd - overlapStart + 1)
            }
        }
        return count
    }

    /**
     * Returns the number of completed ayahs in a specific Juz (1..30).
     */
    fun juzCompletedCount(juzNumber: Int): Int {
        val range = QuranAyahMetadata.juzRange(juzNumber)
        return overlapCount(range.first, range.last)
    }

    /**
     * Checks if a Juz is 100% complete.
     */
    fun isJuzComplete(juzNumber: Int): Boolean {
        val range = QuranAyahMetadata.juzRange(juzNumber)
        val juzTotal = range.last - range.first + 1
        return juzCompletedCount(juzNumber) >= juzTotal
    }

    /**
     * Returns the next unread global ayah (1..6236), or null if all completed.
     */
    val nextUnreadAyah: Int?
        get() {
            var expected = 1
            for (range in ranges) {
                if (range.start > expected) {
                    return expected
                }
                expected = range.end + 1
            }
            return if (expected <= QuranAyahMetadata.TOTAL_AYAHS) expected else null
        }

    /**
     * Adds an ayah range [start, end], merging with existing overlapping or adjacent ranges.
     */
    fun add(start: Int, end: Int): AyahRangeSet {
        val clampedStart = start.coerceIn(1, QuranAyahMetadata.TOTAL_AYAHS)
        val clampedEnd = end.coerceIn(1, QuranAyahMetadata.TOTAL_AYAHS)
        if (clampedStart > clampedEnd) return this

        val newRange = AyahRange(clampedStart, clampedEnd)
        val updated = ArrayList<AyahRange>(ranges.size + 1)
        var inserted = false

        for (existing in ranges) {
            if (!inserted && newRange.start <= existing.start) {
                updated.add(newRange)
                inserted = true
            }
            updated.add(existing)
        }
        if (!inserted) {
            updated.add(newRange)
        }

        return AyahRangeSet(updated)
    }

    /**
     * Removes an ayah range [start, end] from this set.
     */
    fun remove(start: Int, end: Int): AyahRangeSet {
        val clampedStart = start.coerceIn(1, QuranAyahMetadata.TOTAL_AYAHS)
        val clampedEnd = end.coerceIn(1, QuranAyahMetadata.TOTAL_AYAHS)
        if (clampedStart > clampedEnd || ranges.isEmpty()) return this

        val result = ArrayList<AyahRange>(ranges.size)
        for (range in ranges) {
            // Case 1: No overlap, range is completely before or after
            if (range.end < clampedStart || range.start > clampedEnd) {
                result.add(range)
                continue
            }

            // Case 2: Left segment survives
            if (range.start < clampedStart) {
                result.add(AyahRange(range.start, clampedStart - 1))
            }

            // Case 3: Right segment survives
            if (range.end > clampedEnd) {
                result.add(AyahRange(clampedEnd + 1, range.end))
            }
        }
        return AyahRangeSet(result)
    }

    /**
     * Unions this set with another AyahRangeSet.
     */
    fun union(other: AyahRangeSet): AyahRangeSet {
        if (other.ranges.isEmpty()) return this
        if (this.ranges.isEmpty()) return other
        val combined = ArrayList<AyahRange>(this.ranges.size + other.ranges.size)
        combined.addAll(this.ranges)
        combined.addAll(other.ranges)
        return AyahRangeSet(combined)
    }

    /**
     * Compact JSON serialization: e.g. "[[1,148],[260,385]]"
     */
    fun toJson(): String {
        val jsonArray = JSONArray()
        for (range in ranges) {
            val pair = JSONArray()
            pair.put(range.start)
            pair.put(range.end)
            jsonArray.put(pair)
        }
        return jsonArray.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AyahRangeSet) return false
        return ranges == other.ranges
    }

    override fun hashCode(): Int = ranges.hashCode()

    override fun toString(): String = "AyahRangeSet(count=$completedCount, ranges=$ranges)"

    companion object {
        val EMPTY = AyahRangeSet(emptyList())

        fun fromJson(json: String?): AyahRangeSet {
            if (json.isNullOrBlank()) return EMPTY
            return try {
                val array = JSONArray(json)
                val list = ArrayList<AyahRange>(array.length())
                for (i in 0 until array.length()) {
                    val pair = array.getJSONArray(i)
                    if (pair.length() >= 2) {
                        val s = pair.getInt(0)
                        val e = pair.getInt(1)
                        if (s in 1..QuranAyahMetadata.TOTAL_AYAHS && e in 1..QuranAyahMetadata.TOTAL_AYAHS && s <= e) {
                            list.add(AyahRange(s, e))
                        }
                    }
                }
                AyahRangeSet(list)
            } catch (_: Exception) {
                EMPTY
            }
        }

        private fun mergeInternal(input: List<AyahRange>): List<AyahRange> {
            if (input.isEmpty()) return emptyList()
            val sorted = input.sorted()
            val merged = ArrayList<AyahRange>(sorted.size)
            var currentStart = sorted[0].start
            var currentEnd = sorted[0].end

            for (i in 1 until sorted.size) {
                val next = sorted[i]
                if (next.start <= currentEnd + 1) {
                    // Overlapping or strictly adjacent intervals merge
                    currentEnd = max(currentEnd, next.end)
                } else {
                    merged.add(AyahRange(currentStart, currentEnd))
                    currentStart = next.start
                    currentEnd = next.end
                }
            }
            merged.add(AyahRange(currentStart, currentEnd))
            return merged
        }
    }
}
