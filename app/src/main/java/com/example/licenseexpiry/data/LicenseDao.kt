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

    @Query("SELECT * FROM vehicles ORDER BY nickname ASC")
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
}
