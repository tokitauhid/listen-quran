package com.listenquran.player

import androidx.compose.runtime.Immutable
import com.listenquran.domain.model.QueueItem

@Immutable
data class PlaybackQueueState(
    val currentItem: QueueItem? = null,
    val upcomingItems: List<QueueItem> = emptyList(),
    val historyItems: List<QueueItem> = emptyList(),
    val sourceTitle: String? = null,
    val isShuffleEnabled: Boolean = false,
    val isRadio: Boolean = false,
    val totalCount: Int = 0,
) {
    val allItems: List<QueueItem>
        get() = buildList {
            addAll(historyItems)
            currentItem?.let { add(it) }
            addAll(upcomingItems)
        }

    val totalVersesCount: Int
        get() = upcomingItems.sumOf { it.surah.versesCount } + (currentItem?.surah?.versesCount ?: 0)

    val estimatedRemainingMinutes: Int
        get() = (totalVersesCount * 4.5 / 60).toInt().coerceAtLeast(1)
}
