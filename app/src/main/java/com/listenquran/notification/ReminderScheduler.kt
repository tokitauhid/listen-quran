package com.listenquran.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    const val FRIDAY_WORK_NAME = "friday_kahf_reminder_work"
    const val DAILY_REMINDER_WORK_NAME = "daily_quran_reminder_work"

    fun scheduleDailyReminder(context: Context, hour: Int = 20, minute: Int = 0) {
        val workManager = WorkManager.getInstance(context)

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        val initialDelay = (calendar.timeInMillis - System.currentTimeMillis()).coerceAtLeast(0L)

        val request = PeriodicWorkRequestBuilder<DailyStreakReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            DAILY_REMINDER_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancelDailyReminder(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(DAILY_REMINDER_WORK_NAME)
    }

    fun scheduleFridayReminder(context: Context, hour: Int = 8, minute: Int = 0) {
        val workManager = WorkManager.getInstance(context)

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // Advance to next Friday
            while (get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY || timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        val initialDelay = (calendar.timeInMillis - System.currentTimeMillis()).coerceAtLeast(0L)

        val request = PeriodicWorkRequestBuilder<FridayKahfReminderWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            FRIDAY_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancelFridayReminder(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(FRIDAY_WORK_NAME)
    }

    fun cancelAllReminders(context: Context) {
        cancelDailyReminder(context)
        cancelFridayReminder(context)
    }
}
