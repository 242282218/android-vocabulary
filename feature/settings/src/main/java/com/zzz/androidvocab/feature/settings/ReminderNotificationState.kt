package com.zzz.androidvocab.feature.settings

import android.os.Build

enum class ReminderNotificationState {
    Granted,
    MissingRuntimePermission,
    DisabledInSystem,
}

internal fun ReminderNotificationState.statusSummary(reminderEnabled: Boolean): String =
    when {
        !reminderEnabled -> "已关闭"
        this == ReminderNotificationState.MissingRuntimePermission -> "需授权"
        this == ReminderNotificationState.DisabledInSystem -> "已暂停"
        else -> "已开启"
    }

internal fun ReminderNotificationState.hasVisibleRisk(reminderEnabled: Boolean): Boolean =
    reminderEnabled && this != ReminderNotificationState.Granted

internal fun resolveReminderNotificationState(
    sdkInt: Int,
    hasRuntimePermission: Boolean,
    notificationsEnabled: Boolean,
): ReminderNotificationState =
    when {
        sdkInt >= Build.VERSION_CODES.TIRAMISU && !hasRuntimePermission ->
            ReminderNotificationState.MissingRuntimePermission
        !notificationsEnabled -> ReminderNotificationState.DisabledInSystem
        else -> ReminderNotificationState.Granted
    }
