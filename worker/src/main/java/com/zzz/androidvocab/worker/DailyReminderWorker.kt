package com.zzz.androidvocab.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zzz.androidvocab.core.common.POST_NOTIFICATIONS_RUNTIME_PERMISSION_SDK
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class DailyReminderWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    applicationContext.getString(R.string.notification_channel_daily_review),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
            val permissionGranted =
                ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            val notificationsEnabled = NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
            if (!canPostNotifications(Build.VERSION.SDK_INT, permissionGranted, notificationsEnabled)) {
                return Result.success()
            }
            val launchIntent =
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setPackage(applicationContext.packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            val contentIntent =
                PendingIntent.getActivity(
                    applicationContext,
                    0,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val notification =
                NotificationCompat
                    .Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(applicationContext.getString(R.string.notification_daily_review_title))
                    .setContentText(applicationContext.getString(R.string.notification_daily_review_text))
                    .setContentIntent(contentIntent)
                    .setAutoCancel(true)
                    .build()
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
            return Result.success()
        }

        companion object {
            const val NOTIFICATION_ID = 1001
        }
    }

internal fun canPostNotifications(
    sdkInt: Int,
    permissionGranted: Boolean,
    notificationsEnabled: Boolean,
): Boolean = notificationsEnabled && (sdkInt < POST_NOTIFICATIONS_RUNTIME_PERMISSION_SDK || permissionGranted)

private const val NOTIFICATION_CHANNEL_ID = "daily_review"
