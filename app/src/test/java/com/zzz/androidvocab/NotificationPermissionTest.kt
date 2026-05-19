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
            reminderSyncAction(reminderEnabled = false, hasNotificationPermission = true) ==
                ReminderSyncAction.Cancel,
        )
    }

    @Test
    fun reminderSyncSchedulesWhenEnabledAndPermissionGranted() {
        assertTrue(
            reminderSyncAction(reminderEnabled = true, hasNotificationPermission = true) ==
                ReminderSyncAction.Schedule,
        )
    }

    @Test
    fun reminderSyncRequestsPermissionWhenEnabledAndPermissionMissing() {
        assertTrue(
            reminderSyncAction(reminderEnabled = true, hasNotificationPermission = false) ==
                ReminderSyncAction.RequestPermission,
        )
    }
}
