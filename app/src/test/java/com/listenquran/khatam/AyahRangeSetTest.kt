package com.listenquran.khatam

import com.listenquran.domain.model.khatam.AyahRange
import com.listenquran.domain.model.khatam.AyahRangeSet
import org.junit.Assert.*
import org.junit.Test

class AyahRangeSetTest {

    @Test
    fun emptySet_hasZeroCount() {
        val set = AyahRangeSet.EMPTY
        assertEquals(0, set.completedCount)
        assertFalse(set.contains(1))
        assertFalse(set.isComplete)
        assertEquals(1, set.nextUnreadAyah)
    }

    @Test
    fun add_singleRange_computesCountCorrectly() {
        val set = AyahRangeSet.EMPTY.add(1, 7) // Al-Fatihah
        assertEquals(7, set.completedCount)
        assertTrue(set.contains(1))
        assertTrue(set.contains(7))
        assertFalse(set.contains(8))
        assertEquals(8, set.nextUnreadAyah)
    }

    @Test
    fun add_overlappingAndAdjacentRanges_mergesCorrectly() {
        val set = AyahRangeSet.EMPTY
            .add(1, 10)
            .add(8, 20) // Overlaps [1, 10] -> [1, 20]
            .add(21, 30) // Adjacent to [1, 20] -> [1, 30]

        assertEquals(1, set.ranges.size)
        assertEquals(AyahRange(1, 30), set.ranges[0])
        assertEquals(30, set.completedCount)
        assertEquals(31, set.nextUnreadAyah)
    }

    @Test
    fun add_disjointRanges_preservesOrder() {
        val set = AyahRangeSet.EMPTY
            .add(100, 200)
            .add(1, 10)
            .add(300, 400)

        assertEquals(3, set.ranges.size)
        assertEquals(AyahRange(1, 10), set.ranges[0])
        assertEquals(AyahRange(100, 200), set.ranges[1])
        assertEquals(AyahRange(300, 400), set.ranges[2])
        assertEquals(10 + 101 + 101, set.completedCount)
        assertEquals(11, set.nextUnreadAyah) // Gaps handled correctly
    }

    @Test
    fun remove_middlePunch_splitsRange() {
        val set = AyahRangeSet.EMPTY
            .add(1, 100)
            .remove(40, 60)

        assertEquals(2, set.ranges.size)
        assertEquals(AyahRange(1, 39), set.ranges[0])
        assertEquals(AyahRange(61, 100), set.ranges[1])
        assertEquals(39 + 40, set.completedCount)
        assertTrue(set.contains(39))
        assertFalse(set.contains(40))
        assertFalse(set.contains(60))
        assertTrue(set.contains(61))
    }

    @Test
    fun remove_entireRange_emptiesSet() {
        val set = AyahRangeSet.EMPTY
            .add(10, 20)
            .remove(5, 25)

        assertEquals(0, set.completedCount)
        assertTrue(set.ranges.isEmpty())
    }

    @Test
    fun overlapCount_computesAccurately() {
        val set = AyahRangeSet.EMPTY
            .add(1, 50)
            .add(100, 150)

        assertEquals(50, set.overlapCount(1, 50))
        assertEquals(25, set.overlapCount(26, 75)) // [26, 50] is 25 ayahs
        assertEquals(0, set.overlapCount(51, 99))
        assertEquals(51, set.overlapCount(100, 150))
    }

    @Test
    fun jsonSerialization_roundtrip() {
        val original = AyahRangeSet.EMPTY
            .add(1, 148)
            .add(200, 300)

        val json = original.toJson()
        val reconstructed = AyahRangeSet.fromJson(json)

        assertEquals(original, reconstructed)
        assertEquals(original.completedCount, reconstructed.completedCount)
        assertEquals(original.ranges, reconstructed.ranges)
    }

    @Test
    fun fullQuran_isCompleteDetection() {
        val set = AyahRangeSet.EMPTY.add(1, 6236)
        assertTrue(set.isComplete)
        assertEquals(6236, set.completedCount)
        assertEquals(100f, set.completionPercentage, 0.01f)
        assertNull(set.nextUnreadAyah)
        for (juz in 1..30) {
            assertTrue("Juz $juz must be complete", set.isJuzComplete(juz))
        }
    }
}
