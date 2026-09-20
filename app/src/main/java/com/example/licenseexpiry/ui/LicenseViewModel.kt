package com.example.licenseexpiry.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.licenseexpiry.data.AppDatabase
import com.example.licenseexpiry.data.LicenseEntry
import com.example.licenseexpiry.data.LicenseRepository
import com.example.licenseexpiry.data.Vehicle
import com.example.licenseexpiry.notifications.AlarmScheduler
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

class LicenseViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LicenseRepository(
        AppDatabase.getInstance(application).licenseDao()
    )

    val vehicles: StateFlow<List<Vehicle>> = repository.allVehicles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allLicenses: StateFlow<List<LicenseEntry>> = repository.allLicenses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addVehicle(nickname: String, plateNumber: String) {
        viewModelScope.launch {
            repository.addVehicle(Vehicle(nickname = nickname, plateNumber = plateNumber))
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
            AlarmScheduler.scheduleReminder(
                getApplication(),
                entry.copy(id = newId),
                vehicleNickname
            )
            repository.updateLicense(entry.copy(id = newId, alarmScheduled = true))
        }
    }

    fun deleteLicense(entry: LicenseEntry) {
        viewModelScope.launch {
            AlarmScheduler.cancelReminder(getApplication(), entry.id)
            repository.deleteLicense(entry)
        }
    }

    fun deleteVehicle(vehicle: Vehicle) {
        viewModelScope.launch { repository.deleteVehicle(vehicle) }
    }
}
