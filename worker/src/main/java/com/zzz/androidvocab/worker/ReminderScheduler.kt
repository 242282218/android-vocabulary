package com.zzz.androidvocab.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.zzz.androidvocab.core.common.ClockProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val DAILY_REVIEW_REMINDER_WORK = "daily-review-reminder"

@Singleton
class ReminderScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val clockProvider: ClockProvider,
    ) {
        fun scheduleDailyReminder(
            hour: Int,
            minute: Int,
        ) {
            val now = clockProvider.now().atZone(clockProvider.zoneId())
            val request =
                PeriodicWorkRequestBuilder<DailyReminderWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(nextDailyReminderDelay(now, hour, minute))
                    .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                DAILY_REVIEW_REMINDER_WORK,
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                request,
            )
        }

        fun cancelDailyReminder() {
            WorkManager.getInstance(context).cancelUniqueWork(DAILY_REVIEW_REMINDER_WORK)
        }
    }

internal fun nextDailyReminderDelay(
    now: ZonedDateTime,
    hour: Int,
    minute: Int,
): Duration {
    val reminderTime = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    val nowInstant = now.toInstant()
    val todayReminder = nextReminderCandidate(now.toLocalDate(), reminderTime, now.zone, nowInstant)
    val nextReminder =
        if (todayReminder.toInstant().isBefore(nowInstant)) {
            nextReminderCandidate(now.toLocalDate().plusDays(1), reminderTime, now.zone, nowInstant)
        } else {
            todayReminder
        }
    return Duration.between(nowInstant, nextReminder.toInstant())
}

private fun nextReminderCandidate(
    date: LocalDate,
    time: LocalTime,
    zoneId: ZoneId,
    notBefore: Instant,
): ZonedDateTime {
    val localDateTime = LocalDateTime.of(date, time)
    val offsets = zoneId.rules.getValidOffsets(localDateTime)
    if (offsets.isEmpty()) {
        val transition = zoneId.rules.getTransition(localDateTime)
        // Preserve the selected wall-clock minute when DST skips the requested local time.
        return localDateTime.plus(transition.duration).atZone(zoneId)
    }
    return offsets
        .map { offset -> ZonedDateTime.ofLocal(localDateTime, zoneId, offset) }
        .sortedBy { candidate -> candidate.toInstant() }
        .firstOrNull { candidate -> !candidate.toInstant().isBefore(notBefore) }
        ?: ZonedDateTime.ofLocal(localDateTime, zoneId, offsets.first())
}
