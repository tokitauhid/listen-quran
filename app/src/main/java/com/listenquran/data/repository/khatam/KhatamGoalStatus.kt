package com.listenquran.data.repository.khatam

data class KhatamGoalStatus(
    val hasGoal: Boolean = false,
    val targetDays: Int? = null,
    val daysElapsed: Int = 0,
    val daysRemaining: Int = 0,
    val dailyTargetAyahs: Int = 0,
    val statusText: String = "",
    val isOnTrack: Boolean = true
)
