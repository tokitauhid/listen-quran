package com.listenquran.player

import androidx.compose.runtime.Immutable

sealed interface SleepTimerMode {
    data object Off : SleepTimerMode

    data class Duration(val totalMinutes: Int) : SleepTimerMode

    data object EndOfSurah : SleepTimerMode

    data object EndOfPlaylist : SleepTimerMode

    data class Custom(val totalMinutes: Int) : SleepTimerMode
}

@Immutable
data class SleepTimerState(
    val mode: SleepTimerMode = SleepTimerMode.Off,
    val remainingSeconds: Long? = null,
    val isRunning: Boolean = false,
) {
    val isEndOfSurah: Boolean
        get() = mode is SleepTimerMode.EndOfSurah

    val isEndOfPlaylist: Boolean
        get() = mode is SleepTimerMode.EndOfPlaylist

    val isDurationTimer: Boolean
        get() = mode is SleepTimerMode.Duration || mode is SleepTimerMode.Custom
}
