package com.listenquran.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    const val FRIDAY_WORK_NAME = "friday_kahf_reminder_work"
    const val DAILY_STREAK_WORK_NAME = "daily_streak_reminder_work"

    fun scheduleReminders(context: Context) {
        val workManager = WorkManager.getInstance(context)

        // 1. Friday Kahf Reminder: runs every 24 hours, worker triggers if Friday morning (08:00 AM)
        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 8)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        if (calendar.timeInMillis <= now) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        val initialDelay = calendar.timeInMillis - now

        val fridayRequest = PeriodicWorkRequestBuilder<FridayKahfReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            FRIDAY_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            fridayRequest
        )

        // 2. Daily Streak Reminder: runs once every 24 hours at 20:00 (8:00 PM)
        val dailyCal = Calendar.getInstance()
        val dailyNow = dailyCal.timeInMillis
        dailyCal.set(Calendar.HOUR_OF_DAY, 20)
        dailyCal.set(Calendar.MINUTE, 0)
        dailyCal.set(Calendar.SECOND, 0)
        if (dailyCal.timeInMillis <= dailyNow) {
            dailyCal.add(Calendar.DAY_OF_YEAR, 1)
        }
        val dailyDelay = dailyCal.timeInMillis - dailyNow

        val streakRequest = PeriodicWorkRequestBuilder<DailyStreakReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(dailyDelay, TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            DAILY_STREAK_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            streakRequest
        )
    }

    fun cancelReminders(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(FRIDAY_WORK_NAME)
        workManager.cancelUniqueWork(DAILY_STREAK_WORK_NAME)
    }
}
