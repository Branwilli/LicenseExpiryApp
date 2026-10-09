package com.example.licenseexpiry.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.licenseexpiry.data.AppDatabase
import com.example.licenseexpiry.data.LicenseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * AlarmManager alarms are cleared when the device reboots, so every license's
 * reminder has to be re-scheduled from scratch. Runs on every BOOT_COMPLETED.
 *
 * Uses goAsync() + a background coroutine rather than relying on WorkManager,
 * so this doesn't depend on WorkManager's own initialization timing right
 * after boot. If a reminder's trigger time already passed while the device
 * was off, AlarmManager fires it almost immediately — a deliberate "catch up"
 * behavior rather than silently dropping it.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = LicenseRepository(AppDatabase.getInstance(appContext).licenseDao())
                val licenses = repository.allLicensesOnce()

                for (entry in licenses) {
                    val vehicle = repository.getVehicleById(entry.vehicleId)
                    AlarmScheduler.scheduleReminder(
                        appContext,
                        entry,
                        vehicleNickname = vehicle?.nickname ?: "Your vehicle"
                    )
                }
            } finally {
                // Required: tells the OS this receiver's async work is done,
                // so it won't kill the process mid-reschedule.
                pendingResult.finish()
            }
        }
    }
}
