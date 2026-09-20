package com.example.licenseexpiry.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.licenseexpiry.data.AppDatabase
import com.example.licenseexpiry.data.LicenseRepository
import com.example.licenseexpiry.network.EmailApiService
import com.example.licenseexpiry.network.ExpiryEmailRequest
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Runs once a day (scheduled via WorkManager PeriodicWorkRequest).
 * 1. Finds licenses expiring within their reminder window that haven't been emailed yet.
 * 2. Sends an email via the backend for each.
 * 3. Ensures an AlarmManager alarm is scheduled for licenses that don't have one yet.
 */
class ExpiryCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = LicenseRepository(AppDatabase.getInstance(applicationContext).licenseDao())
        val userEmail = inputData.getString(KEY_USER_EMAIL) ?: return Result.failure()
        val backendBaseUrl = inputData.getString(KEY_BACKEND_URL) ?: return Result.failure()

        return try {
            // 1. Email reminders: window = now + 30 days, catches anything with a
            // reminderDaysBefore of up to 30 that hasn't been emailed yet.
            val windowEnd = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(30)
            val dueForEmail = repository.licensesDueForReminder(windowEnd)

            if (dueForEmail.isNotEmpty()) {
                val emailApi = EmailApiService.create(backendBaseUrl)
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

                for (entry in dueForEmail) {
                    emailApi.sendExpiryEmail(
                        ExpiryEmailRequest(
                            toEmail = userEmail,
                            vehicleNickname = "Vehicle #${entry.vehicleId}",
                            licenseType = entry.licenseType,
                            expiryDate = dateFormat.format(Date(entry.expiryDateMillis))
                        )
                    )
                    repository.updateLicense(entry.copy(emailNotified = true))
                }
            }

            // 2. Make sure every license has an alarm scheduled.
            val needingAlarm = repository.licensesNeedingAlarm()
            for (entry in needingAlarm) {
                AlarmScheduler.scheduleReminder(
                    applicationContext,
                    entry,
                    vehicleNickname = "Vehicle #${entry.vehicleId}"
                )
                repository.updateLicense(entry.copy(alarmScheduled = true))
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_USER_EMAIL = "user_email"
        const val KEY_BACKEND_URL = "backend_url"
        const val WORK_NAME = "expiry_check_daily"
    }
}
