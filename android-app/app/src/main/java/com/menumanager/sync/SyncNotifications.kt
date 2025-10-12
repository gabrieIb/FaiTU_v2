package com.menumanager.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat

object SyncNotifications {
    const val CHANNEL_ID = "sync_status"
    const val FOREGROUND_NOTIFICATION_ID = 1001
    const val COMPLETE_NOTIFICATION_ID = 1002

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = NotificationManagerCompat.from(context)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sincronizzazione menu",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Stato della sincronizzazione delle liste"
            }
            manager.createNotificationChannel(channel)
        }
    }
}
