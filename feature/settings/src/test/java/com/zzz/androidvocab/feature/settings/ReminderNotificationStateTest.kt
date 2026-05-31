package com.zzz.androidvocab.feature.settings

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderNotificationStateTest {
    @Test
    fun statusSummaryPrefersReminderToggleStateBeforePermissionWarnings() {
        assertEquals(
            "已关闭",
            ReminderNotificationState.MissingRuntimePermission.statusSummary(reminderEnabled = false),
        )
        assertEquals(
            "需授权",
            ReminderNotificationState.MissingRuntimePermission.statusSummary(reminderEnabled = true),
        )
        assertEquals(
            "已暂停",
            ReminderNotificationState.DisabledInSystem.statusSummary(reminderEnabled = true),
        )
        assertEquals(
            "已开启",
            ReminderNotificationState.Granted.statusSummary(reminderEnabled = true),
        )
    }

    @Test
    fun visibleRiskOnlyAppliesWhenReminderIsEnabledAndUnavailable() {
        assertEquals(
            false,
            ReminderNotificationState.MissingRuntimePermission.hasVisibleRisk(reminderEnabled = false),
        )
        assertEquals(
            true,
            ReminderNotificationState.MissingRuntimePermission.hasVisibleRisk(reminderEnabled = true),
        )
        assertEquals(
            true,
            ReminderNotificationState.DisabledInSystem.hasVisibleRisk(reminderEnabled = true),
        )
        assertEquals(
            false,
            ReminderNotificationState.Granted.hasVisibleRisk(reminderEnabled = true),
        )
    }

    @Test
    fun runtimePermissionMissingWinsOverDisabledSystemNotifications() {
        assertEquals(
            ReminderNotificationState.MissingRuntimePermission,
            resolveReminderNotificationState(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                hasRuntimePermission = false,
                notificationsEnabled = false,
            ),
        )
    }

    @Test
    fun grantedPermissionStillShowsSystemDisabledWhenNotificationsAreOff() {
        assertEquals(
            ReminderNotificationState.DisabledInSystem,
            resolveReminderNotificationState(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                hasRuntimePermission = true,
                notificationsEnabled = false,
            ),
        )
    }

    @Test
    fun preAndroid13UsesSystemNotificationSwitchAsReminderAvailability() {
        assertEquals(
            ReminderNotificationState.Granted,
            resolveReminderNotificationState(
                sdkInt = Build.VERSION_CODES.S_V2,
                hasRuntimePermission = false,
                notificationsEnabled = true,
            ),
        )
        assertEquals(
            ReminderNotificationState.DisabledInSystem,
            resolveReminderNotificationState(
                sdkInt = Build.VERSION_CODES.S_V2,
                hasRuntimePermission = false,
                notificationsEnabled = false,
            ),
        )
    }
}
