package com.example.licenseexpiry.data

import kotlinx.coroutines.flow.Flow

class LicenseRepository(private val dao: LicenseDao) {

    fun allVehicles(): Flow<List<Vehicle>> = dao.getAllVehicles()

    fun allLicenses(): Flow<List<LicenseEntry>> = dao.getAllLicenses()

    suspend fun allLicensesOnce(): List<LicenseEntry> = dao.getAllLicensesOnce()

    fun licensesForVehicle(vehicleId: Long): Flow<List<LicenseEntry>> =
        dao.getLicensesForVehicle(vehicleId)

    fun allFitnessEntries(): Flow<List<FitnessEntry>> = dao.getAllFitness()

    suspend fun allFitnessEntriesOnce(): List<FitnessEntry> = dao.getAllFitnessOnce()

    fun fitnessForVehicle(vehicleId: Long): Flow<List<FitnessEntry>> =
        dao.getFitnessForVehicle(vehicleId)

    suspend fun addVehicle(vehicle: Vehicle): Long = dao.insertVehicle(vehicle)

    suspend fun addLicense(entry: LicenseEntry): Long = dao.insertLicense(entry)

    suspend fun addFitness(entry: FitnessEntry): Long = dao.insertFitness(entry)

    suspend fun updateVehicle(vehicle: Vehicle) = dao.updateVehicle(vehicle)

    suspend fun updateLicense(entry: LicenseEntry) = dao.updateLicense(entry)

    suspend fun updateFitness(entry: FitnessEntry) = dao.updateFitness(entry)

    suspend fun deleteVehicle(vehicle: Vehicle) = dao.deleteVehicle(vehicle)

    suspend fun deleteLicense(entry: LicenseEntry) = dao.deleteLicense(entry)

    suspend fun deleteFitness(entry: FitnessEntry) = dao.deleteFitness(entry)

    suspend fun licensesDueForReminder(windowEndMillis: Long): List<LicenseEntry> =
        dao.getLicensesDueForReminder(windowEndMillis)

    suspend fun fitnessDueForReminder(windowEndMillis: Long): List<FitnessEntry> =
        dao.getFitnessDueForReminder(windowEndMillis)

    suspend fun fitnessNeedingAlarm(): List<FitnessEntry> = dao.getFitnessNeedingAlarm()

    suspend fun licensesNeedingAlarm(): List<LicenseEntry> = dao.getLicensesNeedingAlarm()

    suspend fun getVehicleById(vehicleId: Long): Vehicle? = dao.getVehicleById(vehicleId)

    suspend fun unsyncedVehicles(): List<Vehicle> = dao.getUnsyncedVehicles()

    suspend fun unsyncedLicenses(): List<LicenseEntry> = dao.getUnsyncedLicenses()

    suspend fun unsyncedFitnessEntries(): List<FitnessEntry> = dao.getUnsyncedFitnessEntries()

    suspend fun getVehicleByRemoteId(remoteId: Long): Vehicle? = dao.getVehicleByRemoteId(remoteId)

    suspend fun getLicenseByRemoteId(remoteId: Long): LicenseEntry? = dao.getLicenseByRemoteId(remoteId)

    suspend fun getFitnessByRemoteId(remoteId: Long): FitnessEntry? = dao.getFitnessByRemoteId(remoteId)
}
