package com.example.licenseexpiry.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LicenseDao {

    // --- Vehicles ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicle(vehicle: Vehicle): Long

    @Update
    suspend fun updateVehicle(vehicle: Vehicle)

    @Delete
    suspend fun deleteVehicle(vehicle: Vehicle)

    @Query("SELECT * FROM vehicles ORDER BY brand ASC")
    fun getAllVehicles(): Flow<List<Vehicle>>

    // --- License entries ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLicense(entry: LicenseEntry): Long

    @Update
    suspend fun updateLicense(entry: LicenseEntry)

    @Delete
    suspend fun deleteLicense(entry: LicenseEntry)

    @Query("SELECT * FROM license_entries WHERE vehicleId = :vehicleId ORDER BY expiryDateMillis ASC")
    fun getLicensesForVehicle(vehicleId: Long): Flow<List<LicenseEntry>>

    @Query("SELECT * FROM license_entries ORDER BY expiryDateMillis ASC")
    fun getAllLicenses(): Flow<List<LicenseEntry>>

    @Query("SELECT * FROM license_entries ORDER BY expiryDateMillis ASC")
    suspend fun getAllLicensesOnce(): List<LicenseEntry>

    // Used by the background worker to find licenses that need a reminder today.
    @Query(
        """
        SELECT * FROM license_entries
        WHERE emailNotified = 0
        AND expiryDateMillis <= :windowEndMillis
        """
    )
    suspend fun getLicensesDueForReminder(windowEndMillis: Long): List<LicenseEntry>

    @Query("SELECT * FROM license_entries WHERE alarmScheduled = 0")
    suspend fun getLicensesNeedingAlarm(): List<LicenseEntry>

    // --- Fitness Entry ---
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFitness(entry: FitnessEntry): Long

    @Update
    suspend fun updateFitness(entry: FitnessEntry)

    @Delete
    suspend fun deleteFitness(entry: FitnessEntry)

    @Query("SELECT * FROM fitness_entries WHERE vehicleId = :vehicleId ORDER BY expiryDateMillis ASC")
    fun getFitnessForVehicle(vehicleId: Long): Flow<List<FitnessEntry>>

    @Query("SELECT * FROM fitness_entries ORDER BY expiryDateMillis ASC")
    fun getAllFitness(): Flow<List<FitnessEntry>>

    @Query("SELECT * FROM fitness_entries ORDER BY expiryDateMillis ASC")
    suspend fun getAllFitnessOnce(): List<FitnessEntry>

    @Query(
        """
        SELECT * FROM fitness_entries
        WHERE emailNotified = 0
        AND expiryDateMillis <= :windowEndMillis
        """
    )
    suspend fun getFitnessDueForReminder(windowEndMillis: Long): List<FitnessEntry>

    @Query("SELECT * FROM fitness_entries WHERE alarmScheduled = 0")
    suspend fun getFitnessNeedingAlarm(): List<FitnessEntry>

    // --- Sync support ---

    @Query("SELECT * FROM vehicles WHERE id = :vehicleId")
    suspend fun getVehicleById(vehicleId: Long): Vehicle?

    @Query("SELECT * FROM vehicles WHERE remoteId IS NULL")
    suspend fun getUnsyncedVehicles(): List<Vehicle>

    @Query("SELECT * FROM license_entries WHERE remoteId IS NULL")
    suspend fun getUnsyncedLicenses(): List<LicenseEntry>

    @Query("SELECT * FROM fitness_entries WHERE remoteId IS NULL")
    suspend fun getUnsyncedFitnessEntries(): List<FitnessEntry>

    @Query("SELECT * FROM vehicles WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getVehicleByRemoteId(remoteId: Long): Vehicle?

    @Query("SELECT * FROM license_entries WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getLicenseByRemoteId(remoteId: Long): LicenseEntry?

    @Query("SELECT * FROM fitness_entries WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getFitnessByRemoteId(remoteId: Long): FitnessEntry?    
}
