package com.example.licenseexpiry.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.licenseexpiry.data.AppConfig
import com.example.licenseexpiry.data.AppDatabase
import com.example.licenseexpiry.data.LicenseRepository
import com.example.licenseexpiry.data.SyncRepository
import com.example.licenseexpiry.network.RetrofitProvider

/**
 * Runs periodically (see LicenseExpiryApp.kt). Two jobs:
 *   1. Make sure every license has a local AlarmManager alarm scheduled
 *      (covers licenses added while this worker wasn't running, or restored
 *      from a backup).
 *   2. Push any vehicle/license that doesn't have a remoteId yet to the
 *      backend, so its daily cron job (cron/dailyReminders.js) can see it
 *      and email about it. Actually sending email is no longer this
 *      worker's job — the backend owns that now.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = LicenseRepository(AppDatabase.getInstance(applicationContext).licenseDao())
        val appConfig = AppConfig(applicationContext)

        return try {
            // 1. Local alarms - works even if the network call below fails.
            val needingAlarm = repository.licensesNeedingAlarm()
            for (entry in needingAlarm) {
                val vehicle = repository.getVehicleById(entry.vehicleId)
                AlarmScheduler.scheduleReminder(
                    applicationContext,
                    entry,
                    vehicleNickname = vehicle?.nickname ?: "Your vehicle"
                )
                repository.updateLicense(entry.copy(alarmScheduled = true))
            }

            // 2. Push anything not yet synced to the backend, then pull down
            // anything that exists on the backend but not locally yet (e.g.
            // added from another device) - syncAllPending()/pullFromBackend()
            // both check AppConfig.isLoggedIn and no-op if not logged in.
            val api = RetrofitProvider.create(appConfig)
            val syncRepository = SyncRepository(api, repository, appConfig)
            syncRepository.syncAllPending()

            val newlyPulled = syncRepository.pullFromBackend()
            for ((entry, vehicleNickname) in newlyPulled) {
                AlarmScheduler.scheduleReminder(applicationContext, entry, vehicleNickname)
                repository.updateLicense(entry.copy(alarmScheduled = true))
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "sync_and_alarm_backfill"
    }
}
