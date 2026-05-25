package com.zzz.androidvocab.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime

class ReminderSchedulerTest {
    @Test
    fun nextDailyReminderDelayUsesTodayWhenReminderTimeIsAhead() {
        val now = LocalDateTime.parse("2026-05-16T08:30:00")

        val delay = nextDailyReminderDelay(now, hour = 20, minute = 15)

        assertEquals(Duration.ofHours(11).plusMinutes(45), delay)
    }

    @Test
    fun nextDailyReminderDelayUsesTomorrowWhenReminderTimeHasPassed() {
        val now = LocalDateTime.parse("2026-05-16T20:30:00")

        val delay = nextDailyReminderDelay(now, hour = 20, minute = 15)

        assertEquals(Duration.ofHours(23).plusMinutes(45), delay)
    }

    @Test
    fun canPostNotificationsBeforeAndroid13WithoutRuntimePermission() {
        assertTrue(canPostNotifications(sdkInt = 32, permissionGranted = false))
    }

    @Test
    fun canPostNotificationsOnAndroid13OnlyWhenRuntimePermissionGranted() {
        assertFalse(canPostNotifications(sdkInt = 33, permissionGranted = false))
        assertTrue(canPostNotifications(sdkInt = 33, permissionGranted = true))
    }
}
