package com.zzz.androidvocab.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val DAILY_REVIEW_REMINDER_WORK = "daily-review-reminder"

@Singleton
class ReminderScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun scheduleDailyReminder(
            hour: Int,
            minute: Int,
        ) {
            val request =
                PeriodicWorkRequestBuilder<DailyReminderWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(nextDailyReminderDelay(LocalDateTime.now(), hour, minute))
                    .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                DAILY_REVIEW_REMINDER_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancelDailyReminder() {
            WorkManager.getInstance(context).cancelUniqueWork(DAILY_REVIEW_REMINDER_WORK)
        }
    }

internal fun nextDailyReminderDelay(
    now: LocalDateTime,
    hour: Int,
    minute: Int,
): Duration {
    val reminderTime = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    val nextReminder =
        now.toLocalDate().atTime(reminderTime).let { today ->
            if (today.isAfter(now)) today else today.plusDays(1)
        }
    return Duration.between(now, nextReminder)
}
