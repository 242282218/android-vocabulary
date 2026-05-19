package com.zzz.androidvocab

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

internal const val POST_NOTIFICATIONS_RUNTIME_PERMISSION_SDK = 33

internal fun shouldRequestPostNotificationsPermission(
    sdkInt: Int,
    isGranted: Boolean,
): Boolean = sdkInt >= POST_NOTIFICATIONS_RUNTIME_PERMISSION_SDK && !isGranted

internal enum class ReminderSyncAction {
    Schedule,
    RequestPermission,
    Cancel,
}

internal fun reminderSyncAction(
    reminderEnabled: Boolean,
    hasNotificationPermission: Boolean,
): ReminderSyncAction =
    when {
        !reminderEnabled -> ReminderSyncAction.Cancel
        hasNotificationPermission -> ReminderSyncAction.Schedule
        else -> ReminderSyncAction.RequestPermission
    }

internal fun hasPostNotificationsPermission(context: Context): Boolean {
    val isGranted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    return !shouldRequestPostNotificationsPermission(Build.VERSION.SDK_INT, isGranted)
}
