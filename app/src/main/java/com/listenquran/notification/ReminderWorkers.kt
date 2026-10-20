package com.listenquran.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.listenquran.MainActivity
import com.listenquran.R
import com.listenquran.data.local.dao.StatsDao
import com.listenquran.data.repository.StatsCalculators
import com.listenquran.data.repository.StatsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

const val REMINDER_CHANNEL_ID = "listen_quran_reminders"
const val FRIDAY_KAHF_NOTIFICATION_ID = 1001
const val DAILY_STREAK_NOTIFICATION_ID = 1002

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderWorkerEntryPoint {
    fun statsDao(): StatsDao
    fun statsRepository(): StatsRepository
}

fun createReminderNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            REMINDER_CHANNEL_ID,
            "Quran Reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Daily Quran listening reminders and Friday Surah Al-Kahf notifications"
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}

class FridayKahfReminderWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        createReminderNotificationChannel(context)

        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return Result.success()
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("PLAY_SURAH_ID", 18) // Surah Al-Kahf
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            FRIDAY_KAHF_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app_logo)
            .setContentTitle("Friday Reminder: Surah Al-Kahf")
            .setContentText("Recite or listen to Surah Al-Kahf today for light between two Fridays.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Recite or listen to Surah Al-Kahf today. The Prophet ﷺ said: 'Whoever reads Surah Al-Kahf on Friday, a light will shine for him between this Friday and the next.'")
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(FRIDAY_KAHF_NOTIFICATION_ID, notification)

        return Result.success()
    }
}

class DailyStreakReminderWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        createReminderNotificationChannel(context)

        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return Result.success()
        }

        try {
            val entryPoint = EntryPointAccessors.fromApplication(context, ReminderWorkerEntryPoint::class.java)
            val statsDao = entryPoint.statsDao()

            // 1. Smart Skip: If user already listened today (>= 60s), skip notification
            val zoneId = ZoneId.systemDefault()
            val todayStart = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val listenedToday = statsDao.getListeningSecondsSinceDirect(todayStart)
            if (listenedToday >= 60) {
                return Result.success()
            }

            // 2. Check streak to customize message
            val timestamps = statsDao.getQualifyingTimestamps().first()
            val streak = StatsCalculators.calculateStreak(timestamps, zoneId)

            val title = if (streak > 1) "Keep your $streak-day streak alive! 📖" else "Daily Quran Reminder"
            val text = if (streak > 1) {
                "You're on a $streak-day Quran listening streak. Take a few minutes today to continue."
            } else {
                "Take a peaceful pause in your day to listen to the Holy Quran."
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                DAILY_STREAK_NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_app_logo)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(DAILY_STREAK_NOTIFICATION_ID, notification)
        } catch (_: Exception) {}

        return Result.success()
    }
}
