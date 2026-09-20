package com.example.licenseexpiry

import android.app.Application
import androidx.work.*
import com.example.licenseexpiry.notifications.ExpiryCheckWorker
import java.util.concurrent.TimeUnit

class LicenseExpiryApp : Application() {

    override fun onCreate() {
        super.onCreate()
        scheduleDailyExpiryCheck()
    }

    private fun scheduleDailyExpiryCheck() {
       
        val data = Data.Builder()
            .putString(ExpiryCheckWorker.KEY_USER_EMAIL, "user@example.com")
            .putString(ExpiryCheckWorker.KEY_BACKEND_URL, "https://your-backend.example.com/")
            .build()

        val request = PeriodicWorkRequestBuilder<ExpiryCheckWorker>(1, TimeUnit.DAYS)
            .setInputData(data)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ExpiryCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
