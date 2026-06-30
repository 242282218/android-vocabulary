package com.zzz.androidvocab.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderSchedulerTest {
    @Test
    fun nextDailyReminderDelayUsesTodayWhenReminderTimeIsAhead() {
        val now = zonedDateTime("2026-05-16T08:30:00", "Asia/Shanghai")

        val delay = nextDailyReminderDelay(now, hour = 20, minute = 15)

        assertEquals(Duration.ofHours(11).plusMinutes(45), delay)
    }

    @Test
    fun nextDailyReminderDelayUsesTomorrowWhenReminderTimeHasPassed() {
        val now = zonedDateTime("2026-05-16T20:30:00", "Asia/Shanghai")

        val delay = nextDailyReminderDelay(now, hour = 20, minute = 15)

        assertEquals(Duration.ofHours(23).plusMinutes(45), delay)
    }

    @Test
    fun nextDailyReminderDelayUsesCurrentMomentWhenReminderTimeMatchesNow() {
        val now = zonedDateTime("2026-05-16T20:15:00", "Asia/Shanghai")

        val delay = nextDailyReminderDelay(now, hour = 20, minute = 15)

        assertEquals(Duration.ZERO, delay)
    }

    @Test
    fun nextDailyReminderDelayClampsOutOfRangeReminderTime() {
        val now = zonedDateTime("2026-05-16T08:30:00", "Asia/Shanghai")

        val delay = nextDailyReminderDelay(now, hour = 30, minute = -10)

        assertEquals(Duration.ofHours(14).plusMinutes(30), delay)
    }

    @Test
    fun nextDailyReminderDelayUsesRealElapsedTimeAcrossDaylightSavingStart() {
        val now = zonedDateTime("2026-03-07T08:00:00", "America/New_York")

        val delay = nextDailyReminderDelay(now, hour = 7, minute = 30)

        assertEquals(Duration.ofHours(22).plusMinutes(30), delay)
    }

    @Test
    fun nextDailyReminderDelayPreservesWallMinuteAcrossDaylightSavingGap() {
        val now = zonedDateTime("2026-03-08T01:30:00", "America/New_York")

        val delay = nextDailyReminderDelay(now, hour = 2, minute = 30)

        assertEquals(Duration.ofHours(1), delay)
    }

    @Test
    fun nextDailyReminderDelayUsesLaterOverlapWhenItIsStillAhead() {
        val now =
            zonedDateTime("2026-11-01T01:15:00", "America/New_York")
                .withLaterOffsetAtOverlap()

        val delay = nextDailyReminderDelay(now, hour = 1, minute = 30)

        assertEquals(Duration.ofMinutes(15), delay)
    }

    @Test
    fun canPostNotificationsBeforeAndroid13WithoutRuntimePermission() {
        assertTrue(
            canPostNotifications(
                sdkInt = 32,
                permissionGranted = false,
                notificationsEnabled = true,
            ),
        )
    }

    @Test
    fun canPostNotificationsOnAndroid13OnlyWhenRuntimePermissionGranted() {
        assertFalse(
            canPostNotifications(
                sdkInt = 33,
                permissionGranted = false,
                notificationsEnabled = true,
            ),
        )
        assertTrue(
            canPostNotifications(
                sdkInt = 33,
                permissionGranted = true,
                notificationsEnabled = true,
            ),
        )
    }

    @Test
    fun canPostNotificationsReturnsFalseWhenAppNotificationsAreDisabled() {
        assertFalse(
            canPostNotifications(
                sdkInt = 33,
                permissionGranted = true,
                notificationsEnabled = false,
            ),
        )
    }

    private fun zonedDateTime(
        value: String,
        zoneId: String,
    ) = LocalDateTime.parse(value).atZone(ZoneId.of(zoneId))
}
