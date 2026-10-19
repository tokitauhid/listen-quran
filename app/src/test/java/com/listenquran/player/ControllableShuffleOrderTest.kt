package com.listenquran.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllableShuffleOrderTest {

    @Test
    fun testCreateWithCurrentFirst_currentIsFirstAndAllElementsPresent() {
        val totalCount = 10
        val currentIdx = 4
        val order = ControllableShuffleOrder.createWithCurrentFirst(totalCount, currentIdx)

        assertEquals(totalCount, order.length)
        assertEquals(currentIdx, order.shuffledIndices[0])
        assertEquals(currentIdx, order.firstIndex)

        // All indices 0 until totalCount must be present exactly once
        val uniqueIndices = order.shuffledIndices.toSet()
        assertEquals(totalCount, uniqueIndices.size)
        assertTrue((0 until totalCount).all { it in uniqueIndices })
    }

    @Test
    fun testNavigation_nextAndPreviousFollowShuffledIndices() {
        val indices = intArrayOf(2, 5, 0, 3, 1, 4)
        val order = ControllableShuffleOrder(indices)

        assertEquals(2, order.firstIndex)
        assertEquals(4, order.lastIndex)

        // Traversal forward
        assertEquals(5, order.getNextIndex(2))
        assertEquals(0, order.getNextIndex(5))
        assertEquals(3, order.getNextIndex(0))
        assertEquals(1, order.getNextIndex(3))
        assertEquals(4, order.getNextIndex(1))
        assertEquals(-1, order.getNextIndex(4))

        // Traversal backward
        assertEquals(-1, order.getPreviousIndex(2))
        assertEquals(2, order.getPreviousIndex(5))
        assertEquals(5, order.getPreviousIndex(0))
        assertEquals(0, order.getPreviousIndex(3))
        assertEquals(3, order.getPreviousIndex(1))
        assertEquals(1, order.getPreviousIndex(4))
    }

    @Test
    fun testCloneAndInsert_adjustsExistingAndAppendsNew() {
        // Shuffled order: [1, 0, 2]
        val order = ControllableShuffleOrder(intArrayOf(1, 0, 2))
        // Insert 1 item at timeline index 1 -> old index 1 becomes 2, old index 2 becomes 3, new item is 1
        val newOrder = order.cloneAndInsert(1, 1) as ControllableShuffleOrder

        assertEquals(4, newOrder.length)
        // Original indices >= 1 shifted up by 1
        assertEquals(2, newOrder.shuffledIndices[0]) // was 1 -> now 2
        assertEquals(0, newOrder.shuffledIndices[1]) // was 0 -> unchanged
        assertEquals(3, newOrder.shuffledIndices[2]) // was 2 -> now 3
        assertEquals(1, newOrder.shuffledIndices[3]) // newly inserted item at timeline index 1
    }

    @Test
    fun testCloneWithPlayNext_placesItemImmediatelyAfterCurrent() {
        // Shuffled indices: [3, 0, 2, 1]
        // Currently playing is 3 (at position 0 in shuffle).
        // New track inserted at timeline index 4.
        val order = ControllableShuffleOrder(intArrayOf(3, 0, 2, 1))
        val newOrder = ControllableShuffleOrder.cloneWithPlayNext(order, 4)

        assertEquals(5, newOrder.length)
        assertEquals(3, newOrder.shuffledIndices[0]) // Current remains at position 0
        assertEquals(4, newOrder.shuffledIndices[1]) // Inserted item is immediately next!
        assertEquals(0, newOrder.shuffledIndices[2])
        assertEquals(2, newOrder.shuffledIndices[3])
        assertEquals(1, newOrder.shuffledIndices[4])
    }

    @Test
    fun testCloneAndRemove_removesItemAndShiftsIndices() {
        // Shuffled indices: [1, 3, 0, 2]
        val order = ControllableShuffleOrder(intArrayOf(1, 3, 0, 2))
        // Remove item at timeline index 1
        val newOrder = order.cloneAndRemove(1, 2) as ControllableShuffleOrder

        assertEquals(3, newOrder.length)
        // Item 1 was removed. Indices > 1 (namely 2 and 3) shifted down by 1.
        // Old 3 -> 2, old 0 -> 0, old 2 -> 1
        assertEquals(2, newOrder.shuffledIndices[0])
        assertEquals(0, newOrder.shuffledIndices[1])
        assertEquals(1, newOrder.shuffledIndices[2])
    }
}
