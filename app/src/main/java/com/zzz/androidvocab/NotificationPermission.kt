package com.zzz.androidvocab

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.zzz.androidvocab.core.common.POST_NOTIFICATIONS_RUNTIME_PERMISSION_SDK

internal enum class NotificationAccess {
    Granted,
    MissingRuntimePermission,
    DisabledInSystem,
}

internal enum class ReminderSyncAction {
    Schedule,
    Cancel,
}

internal fun shouldRequestPostNotificationsPermission(
    sdkInt: Int,
    isGranted: Boolean,
): Boolean = sdkInt >= POST_NOTIFICATIONS_RUNTIME_PERMISSION_SDK && !isGranted

internal fun resolveNotificationAccess(
    sdkInt: Int,
    hasRuntimePermission: Boolean,
    notificationsEnabled: Boolean,
): NotificationAccess =
    when {
        shouldRequestPostNotificationsPermission(sdkInt, hasRuntimePermission) ->
            NotificationAccess.MissingRuntimePermission
        !notificationsEnabled -> NotificationAccess.DisabledInSystem
        else -> NotificationAccess.Granted
    }

internal fun reminderSyncAction(
    reminderEnabled: Boolean,
    notificationAccess: NotificationAccess,
): ReminderSyncAction =
    when {
        !reminderEnabled -> ReminderSyncAction.Cancel
        notificationAccess == NotificationAccess.MissingRuntimePermission -> ReminderSyncAction.Cancel
        notificationAccess == NotificationAccess.DisabledInSystem -> ReminderSyncAction.Cancel
        else -> ReminderSyncAction.Schedule
    }

internal fun readNotificationAccess(context: Context): NotificationAccess {
    val hasRuntimePermission =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    return resolveNotificationAccess(
        sdkInt = Build.VERSION.SDK_INT,
        hasRuntimePermission = hasRuntimePermission,
        notificationsEnabled = notificationsEnabled,
    )
}
