package com.example.licenseexpiry.data

import kotlinx.coroutines.flow.Flow

class LicenseRepository(private val dao: LicenseDao) {

    fun allVehicles(): Flow<List<Vehicle>> = dao.getAllVehicles()

    fun allLicenses(): Flow<List<LicenseEntry>> = dao.getAllLicenses()

    fun licensesForVehicle(vehicleId: Long): Flow<List<LicenseEntry>> =
        dao.getLicensesForVehicle(vehicleId)

    suspend fun addVehicle(vehicle: Vehicle): Long = dao.insertVehicle(vehicle)

    suspend fun addLicense(entry: LicenseEntry): Long = dao.insertLicense(entry)

    suspend fun updateLicense(entry: LicenseEntry) = dao.updateLicense(entry)

    suspend fun deleteVehicle(vehicle: Vehicle) = dao.deleteVehicle(vehicle)

    suspend fun deleteLicense(entry: LicenseEntry) = dao.deleteLicense(entry)

    suspend fun licensesDueForReminder(windowEndMillis: Long): List<LicenseEntry> =
        dao.getLicensesDueForReminder(windowEndMillis)

    suspend fun licensesNeedingAlarm(): List<LicenseEntry> = dao.getLicensesNeedingAlarm()
}
