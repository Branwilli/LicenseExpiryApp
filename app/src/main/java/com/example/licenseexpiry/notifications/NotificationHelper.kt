package com.example.licenseexpiry.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {

    const val CHANNEL_ID = "license_expiry_alarms"
    private const val CHANNEL_NAME = "License Expiry Alarms"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH // heads-up, alarm-like behavior
            ).apply {
                description = "Alerts when a vehicle license is about to expire"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun buildExpiryNotification(
        context: Context,
        title: String,
        message: String,
        notificationId: Int
    ): android.app.Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert) // replace with your app icon
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()
    }

    fun notify(context: Context, notificationId: Int, title: String, message: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, buildExpiryNotification(context, title, message, notificationId))
    }
}
