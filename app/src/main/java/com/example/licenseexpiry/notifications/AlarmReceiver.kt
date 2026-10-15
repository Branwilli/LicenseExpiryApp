package com.example.licenseexpiry.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val licenseId = intent.getLongExtra(EXTRA_LICENSE_ID, -1L)
        val vehicleNickname = intent.getStringExtra(EXTRA_VEHICLE_NAME) ?: "Your vehicle"
        val licenseType = intent.getStringExtra(EXTRA_LICENSE_TYPE) ?: "License"
        val fitnessId = intent.getLongExtra(EXTRA_FITNESS_ID, -1L)

        NotificationHelper.createChannel(context)
        NotificationHelper.notify(
            context=context,
            notificationId=licenseId.toInt(),
            title="$licenseType expiring soon",
            message="$vehicleNickname's $licenseType is about to expire."
        )
    }

    companion object {
        const val EXTRA_LICENSE_ID = "extra_license_id"
        const val EXTRA_FITNESS_ID = "extra_fitness_id"
        const val EXTRA_VEHICLE_NAME = "extra_vehicle_name"
        const val EXTRA_LICENSE_TYPE = "extra_license_type"
    }
}
