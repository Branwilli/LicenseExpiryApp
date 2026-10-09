package com.example.licenseexpiry

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.licenseexpiry.data.AppConfig
import com.example.licenseexpiry.data.AppDatabase
import com.example.licenseexpiry.data.LicenseRepository
import com.example.licenseexpiry.data.SyncRepository
import com.example.licenseexpiry.network.RetrofitProvider
import com.example.licenseexpiry.notifications.SyncWorker
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class LicenseExpiryApp : Application() {

    override fun onCreate() {
        super.onCreate()
        registerUserWithBackend()
        schedulePeriodicSync()
    }

    /**
     * Calls POST /users once so the backend knows this user exists (needed
     * before any vehicle/license can be synced, since they're both scoped to
     * a user_id). Cheap no-op on every later launch — AppConfig caches the id.
     */
    private fun registerUserWithBackend() {
        val appConfig = AppConfig(this)
        val repository = LicenseRepository(AppDatabase.getInstance(this).licenseDao())
        val syncRepository = SyncRepository(RetrofitProvider.create(appConfig), repository, appConfig)

        // ProcessLifecycleOwner's scope survives the Application, unlike a
        // one-off GlobalScope.launch, and doesn't require an Activity.
        ProcessLifecycleOwner.get().lifecycleScope.launch {
            syncRepository.syncAllPending()
        }
    }

    private fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
