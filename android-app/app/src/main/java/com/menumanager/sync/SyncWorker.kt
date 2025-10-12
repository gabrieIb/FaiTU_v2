package com.menumanager.sync

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.menumanager.MenuManagerApp
import com.menumanager.R

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        SyncNotifications.ensureChannel(applicationContext)
        setForeground(createForegroundInfo())
        val repository = (applicationContext.applicationContext as MenuManagerApp).container.menuRepository
        val result = repository.syncPending()
        return if (result.isSuccess) {
            showCompletionNotification(success = true)
            Result.success()
        } else {
            showCompletionNotification(success = false, message = result.exceptionOrNull()?.message)
            if (runAttemptCount < MAX_RETRY_COUNT) Result.retry() else Result.failure()
        }
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, SyncNotifications.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(applicationContext.getString(R.string.app_name))
            .setContentText(applicationContext.getString(R.string.notification_sync_running))
            .setOngoing(true)
            .setProgress(0, 0, true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                SyncNotifications.FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            @Suppress("DEPRECATION")
            ForegroundInfo(SyncNotifications.FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    private fun showCompletionNotification(success: Boolean, message: String? = null) {
        val title = applicationContext.getString(R.string.app_name)
        val text = when {
            success -> applicationContext.getString(R.string.notification_sync_success)
            !message.isNullOrBlank() -> applicationContext.getString(R.string.notification_sync_failure_with_reason, message)
            else -> applicationContext.getString(R.string.notification_sync_failure)
        }
        val notification = NotificationCompat.Builder(applicationContext, SyncNotifications.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext)
                .notify(SyncNotifications.COMPLETE_NOTIFICATION_ID, notification)
        } catch (security: SecurityException) {
            Log.w(TAG, "Notifications permission not granted; skipping sync completion toast", security)
        }
    }

    companion object {
        const val KEY_TRIGGERED_BY_USER = "triggered_by_user"
        private const val MAX_RETRY_COUNT = 2
        private const val TAG = "SyncWorker"
    }
}
