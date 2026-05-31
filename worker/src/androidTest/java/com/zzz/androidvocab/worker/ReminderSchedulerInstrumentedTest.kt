package com.zzz.androidvocab.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.zzz.androidvocab.core.common.ClockProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ReminderSchedulerInstrumentedTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @After
    fun tearDown() {
        WorkManager
            .getInstance(context)
            .cancelAllWork()
            .result
            .get(5, TimeUnit.SECONDS)
    }

    @Test
    fun scheduleDailyReminderEnqueuesUniquePeriodicWork() {
        ReminderScheduler(context, FixedClockProvider()).scheduleDailyReminder(hour = 7, minute = 30)

        val workInfos =
            WorkManager
                .getInstance(context)
                .getWorkInfosForUniqueWork(DAILY_REVIEW_REMINDER_WORK_NAME)
                .get(5, TimeUnit.SECONDS)

        assertEquals(1, workInfos.size)
        assertTrue(workInfos.single().state in setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING))
    }

    private companion object {
        const val DAILY_REVIEW_REMINDER_WORK_NAME = "daily-review-reminder"
    }
}

private class FixedClockProvider : ClockProvider {
    override fun now(): Instant = Instant.parse("2026-05-16T08:00:00Z")

    override fun zoneId(): ZoneId = ZoneId.of("Asia/Shanghai")

    override fun today(): LocalDate = LocalDate.ofInstant(now(), zoneId())
}
