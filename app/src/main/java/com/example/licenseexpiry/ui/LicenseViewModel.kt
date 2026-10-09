package com.example.licenseexpiry.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.licenseexpiry.data.AppConfig
import com.example.licenseexpiry.data.AppDatabase
import com.example.licenseexpiry.data.AuthResult
import com.example.licenseexpiry.data.LicenseEntry
import com.example.licenseexpiry.data.LicenseRepository
import com.example.licenseexpiry.data.SyncRepository
import com.example.licenseexpiry.data.Vehicle
import com.example.licenseexpiry.network.RetrofitProvider
import com.example.licenseexpiry.notifications.AlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LicenseViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LicenseRepository(
        AppDatabase.getInstance(application).licenseDao()
    )

    val appConfig = AppConfig(application)

    // var, not val: rebuilt whenever the backend URL changes, since Retrofit's
    // base URL is fixed at construction time.
    private var syncRepository = buildSyncRepository()

    private fun buildSyncRepository() = SyncRepository(
        RetrofitProvider.create(appConfig),
        repository,
        appConfig
    )

    private val _isLoggedIn = MutableStateFlow(appConfig.isLoggedIn)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    val vehicles: StateFlow<List<Vehicle>> = repository.allVehicles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allLicenses: StateFlow<List<LicenseEntry>> = repository.allLicenses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Auth ---

    fun register(email: String, password: String, onResult: (AuthResult) -> Unit) {
        viewModelScope.launch {
            val result = syncRepository.register(email, password)
            if (result is AuthResult.Success) {
                _isLoggedIn.value = true
                syncRepository.syncAllPending() // push anything added while offline/logged out
                schedulePulledAlarms(syncRepository.pullFromBackend())
            }
            onResult(result)
        }
    }

    fun login(email: String, password: String, onResult: (AuthResult) -> Unit) {
        viewModelScope.launch {
            val result = syncRepository.login(email, password)
            if (result is AuthResult.Success) {
                _isLoggedIn.value = true
                syncRepository.syncAllPending()
                schedulePulledAlarms(syncRepository.pullFromBackend())
            }
            onResult(result)
        }
    }

    /** Schedules local alarms for licenses pulled from the backend that didn't exist locally yet. */
    private suspend fun schedulePulledAlarms(newlyAdded: List<Pair<LicenseEntry, String>>) {
        for ((entry, vehicleNickname) in newlyAdded) {
            AlarmScheduler.scheduleReminder(getApplication(), entry, vehicleNickname)
            repository.updateLicense(entry.copy(alarmScheduled = true))
        }
    }

    fun logOut() {
        syncRepository.logOut()
        _isLoggedIn.value = false
        // Local Room data (vehicles/licenses/alarms) is untouched - logging
        // out only stops backend sync until the user logs in again.
    }

    // --- Vehicles / licenses ---

    fun addVehicle(nickname: String, plateNumber: String) {
        viewModelScope.launch {
            val id = repository.addVehicle(Vehicle(nickname = nickname, plateNumber = plateNumber))
            val vehicle = repository.getVehicleById(id) ?: return@launch
            syncRepository.syncVehicle(vehicle)
        }
    }

    fun addLicense(
        vehicleId: Long,
        vehicleNickname: String,
        licenseType: String,
        expiryDateMillis: Long,
        reminderDaysBefore: Int
    ) {
        viewModelScope.launch {
            val entry = LicenseEntry(
                vehicleId = vehicleId,
                licenseType = licenseType,
                expiryDateMillis = expiryDateMillis,
                reminderDaysBefore = reminderDaysBefore
            )
            val newId = repository.addLicense(entry)
            val savedEntry = entry.copy(id = newId)

            AlarmScheduler.scheduleReminder(getApplication(), savedEntry, vehicleNickname)
            repository.updateLicense(savedEntry.copy(alarmScheduled = true))

            syncRepository.syncLicense(savedEntry.copy(alarmScheduled = true))
        }
    }

    fun updateLicense(
        entry: LicenseEntry,
        vehicleNickname: String,
        licenseType: String,
        expiryDateMillis: Long,
        reminderDaysBefore: Int
    ) {
        viewModelScope.launch {
            val updated = entry.copy(
                licenseType = licenseType,
                expiryDateMillis = expiryDateMillis,
                reminderDaysBefore = reminderDaysBefore,
                emailNotified = false,
                alarmScheduled = false
            )
            repository.updateLicense(updated)

            AlarmScheduler.cancelReminder(getApplication(), entry.id)
            AlarmScheduler.scheduleReminder(getApplication(), updated, vehicleNickname)
            val scheduled = updated.copy(alarmScheduled = true)
            repository.updateLicense(scheduled)

            syncRepository.updateLicenseRemote(scheduled)
        }
    }

    fun deleteLicense(entry: LicenseEntry) {
        viewModelScope.launch {
            AlarmScheduler.cancelReminder(getApplication(), entry.id)
            syncRepository.deleteLicenseRemote(entry)
            repository.deleteLicense(entry)
        }
    }

    fun deleteVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            syncRepository.deleteVehicleRemote(vehicle)
            repository.deleteVehicle(vehicle)
        }
    }

    // --- Settings ---

    /** Only the backend URL lives in Settings now - email/password are handled by login/register. */
    fun saveBackendUrl(backendUrl: String) {
        appConfig.backendBaseUrl = if (backendUrl.endsWith("/")) backendUrl else "$backendUrl/"
        syncRepository = buildSyncRepository()
    }
}
