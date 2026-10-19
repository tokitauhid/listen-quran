package com.listenquran.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ShuffleOrder
import java.util.Random

/**
 * A controllable [ShuffleOrder] implementation that guarantees:
 * 1. The currently playing item index stays first at position 0 when shuffle is enabled.
 * 2. The remaining items are shuffled in a deterministic/controlled pseudo-random order.
 * 3. The exact playback order is exposed to the UI via [shuffledIndices] so the Queue
 *    screen accurately reflects the true playback sequence.
 * 4. Toggling shuffle off restores the exact original timeline order without mutating the timeline.
 */
@OptIn(UnstableApi::class)
class ControllableShuffleOrder(
    val shuffledIndices: IntArray,
    private val random: Random = Random()
) : ShuffleOrder {

    private val indexInShuffled: IntArray = IntArray(shuffledIndices.size) { -1 }

    init {
        for (pos in shuffledIndices.indices) {
            val timelineIndex = shuffledIndices[pos]
            if (timelineIndex in indexInShuffled.indices) {
                indexInShuffled[timelineIndex] = pos
            }
        }
    }

    override fun getLength(): Int = shuffledIndices.size

    override fun getNextIndex(index: Int): Int {
        if (index !in indexInShuffled.indices) return C.INDEX_UNSET
        val pos = indexInShuffled[index]
        if (pos == -1) return C.INDEX_UNSET
        return if (pos + 1 < shuffledIndices.size) shuffledIndices[pos + 1] else C.INDEX_UNSET
    }

    override fun getPreviousIndex(index: Int): Int {
        if (index !in indexInShuffled.indices) return C.INDEX_UNSET
        val pos = indexInShuffled[index]
        if (pos == -1) return C.INDEX_UNSET
        return if (pos - 1 >= 0) shuffledIndices[pos - 1] else C.INDEX_UNSET
    }

    override fun getLastIndex(): Int = if (shuffledIndices.isNotEmpty()) shuffledIndices.last() else C.INDEX_UNSET

    override fun getFirstIndex(): Int = if (shuffledIndices.isNotEmpty()) shuffledIndices.first() else C.INDEX_UNSET

    override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder {
        // Shift existing timeline indices >= insertionIndex by insertionCount
        val newShuffled = IntArray(shuffledIndices.size + insertionCount)
        var writePos = 0
        for (idx in shuffledIndices) {
            newShuffled[writePos++] = if (idx >= insertionIndex) idx + insertionCount else idx
        }
        // Newly added items placed at the end of the shuffled order
        for (i in 0 until insertionCount) {
            newShuffled[shuffledIndices.size + i] = insertionIndex + i
        }
        return ControllableShuffleOrder(newShuffled, random)
    }

    override fun cloneAndRemove(indexFrom: Int, indexToExclusive: Int): ShuffleOrder {
        val count = indexToExclusive - indexFrom
        val newShuffledList = mutableListOf<Int>()
        for (idx in shuffledIndices) {
            if (idx !in indexFrom until indexToExclusive) {
                newShuffledList.add(if (idx >= indexToExclusive) idx - count else idx)
            }
        }
        return ControllableShuffleOrder(newShuffledList.toIntArray(), random)
    }

    override fun cloneAndClear(): ShuffleOrder = ControllableShuffleOrder(intArrayOf(), random)

    companion object {
        /**
         * Creates a shuffle order for a timeline of length [length] where [currentIndex]
         * is placed at position 0, and the remaining indices are shuffled.
         */
        fun createWithCurrentFirst(length: Int, currentIndex: Int, random: Random = Random()): ControllableShuffleOrder {
            if (length <= 0) return ControllableShuffleOrder(intArrayOf(), random)
            val clampedCurrent = currentIndex.coerceIn(0, length - 1)
            val others = ArrayList<Int>(length - 1)
            for (i in 0 until length) {
                if (i != clampedCurrent) {
                    others.add(i)
                }
            }
            // Shuffle remaining items using Fisher-Yates
            for (i in others.indices.reversed()) {
                val j = random.nextInt(i + 1)
                val temp = others[i]
                others[i] = others[j]
                others[j] = temp
            }
            val result = IntArray(length)
            result[0] = clampedCurrent
            for (i in others.indices) {
                result[i + 1] = others[i]
            }
            return ControllableShuffleOrder(result, random)
        }

        /**
         * Creates a shuffle order for "Play Next": inserts the new item index right after
         * the current item in the shuffled order.
         */
        fun cloneWithPlayNext(
            currentOrder: ControllableShuffleOrder,
            insertionIndex: Int
        ): ControllableShuffleOrder {
            val oldShuffled = currentOrder.shuffledIndices
            val newShuffled = IntArray(oldShuffled.size + 1)
            // Position 0 is current item
            newShuffled[0] = if (oldShuffled.isNotEmpty()) {
                if (oldShuffled[0] >= insertionIndex) oldShuffled[0] + 1 else oldShuffled[0]
            } else 0

            // Position 1 is the inserted item (Play Next)
            newShuffled[1] = insertionIndex

            // Remaining items shifted
            var targetPos = 2
            for (i in 1 until oldShuffled.size) {
                val idx = oldShuffled[i]
                newShuffled[targetPos++] = if (idx >= insertionIndex) idx + 1 else idx
            }
            return ControllableShuffleOrder(newShuffled)
        }
    }
}
