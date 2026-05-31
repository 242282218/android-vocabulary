package com.zzz.androidvocab

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPermissionTest {
    @Test
    fun preAndroid13DoesNotNeedRuntimeNotificationPermission() {
        assertFalse(shouldRequestPostNotificationsPermission(sdkInt = 32, isGranted = false))
    }

    @Test
    fun android13NeedsRuntimeNotificationPermissionWhenMissing() {
        assertTrue(shouldRequestPostNotificationsPermission(sdkInt = 33, isGranted = false))
    }

    @Test
    fun android13DoesNotRequestWhenAlreadyGranted() {
        assertFalse(shouldRequestPostNotificationsPermission(sdkInt = 33, isGranted = true))
    }

    @Test
    fun reminderSyncCancelsWhenReminderIsDisabled() {
        assertTrue(
            reminderSyncAction(reminderEnabled = false, notificationAccess = NotificationAccess.Granted) ==
                ReminderSyncAction.Cancel,
        )
    }

    @Test
    fun reminderSyncSchedulesWhenEnabledAndPermissionGranted() {
        assertTrue(
            reminderSyncAction(reminderEnabled = true, notificationAccess = NotificationAccess.Granted) ==
                ReminderSyncAction.Schedule,
        )
    }

    @Test
    fun reminderSyncCancelsWhenEnabledAndPermissionMissing() {
        assertTrue(
            reminderSyncAction(
                reminderEnabled = true,
                notificationAccess = NotificationAccess.MissingRuntimePermission,
            ) ==
                ReminderSyncAction.Cancel,
        )
    }

    @Test
    fun reminderSyncCancelsWhenSystemNotificationsAreDisabled() {
        assertTrue(
            reminderSyncAction(
                reminderEnabled = true,
                notificationAccess = NotificationAccess.DisabledInSystem,
            ) == ReminderSyncAction.Cancel,
        )
    }

    @Test
    fun resolveNotificationAccessRequestsRuntimePermissionBeforeTreatingAppAsDisabled() {
        assertTrue(
            resolveNotificationAccess(
                sdkInt = 33,
                hasRuntimePermission = false,
                notificationsEnabled = false,
            ) == NotificationAccess.MissingRuntimePermission,
        )
    }

    @Test
    fun resolveNotificationAccessTreatsDisabledSystemNotificationsAsUnavailableAfterPermissionGranted() {
        assertTrue(
            resolveNotificationAccess(
                sdkInt = 33,
                hasRuntimePermission = true,
                notificationsEnabled = false,
            ) == NotificationAccess.DisabledInSystem,
        )
    }
}
