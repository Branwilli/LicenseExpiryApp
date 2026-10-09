package com.example.licenseexpiry.data

import android.util.Log
import com.example.licenseexpiry.network.ApiService
import com.example.licenseexpiry.network.AuthRequest
import com.example.licenseexpiry.network.CreateLicenseRequest
import com.example.licenseexpiry.network.CreateVehicleRequest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "SyncRepository"

sealed class AuthResult {
    data object Success : AuthResult()
    data class Failure(val message: String) : AuthResult()
}

/**
 * Handles login/register and pushes local Room data up to the backend's
 * Postgres database once authenticated. The app stays fully usable offline
 * (Room + AlarmManager don't need any of this) — this is what lets the
 * backend's daily cron (cron/dailyReminders.js) know a license exists so it
 * can email about it.
 */
class SyncRepository(
    private val api: ApiService,
    private val licenseRepository: LicenseRepository,
    private val appConfig: AppConfig
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    suspend fun register(email: String, password: String): AuthResult = try {
        val response = api.register(AuthRequest(email, password))
        appConfig.authToken = response.token
        appConfig.userEmail = response.user.email
        appConfig.userId = response.user.id
        AuthResult.Success
    } catch (e: Exception) {
        Log.w(TAG, "Registration failed", e)
        AuthResult.Failure(friendlyMessage(e, fallback = "Couldn't create an account. Try again."))
    }

    suspend fun login(email: String, password: String): AuthResult = try {
        val response = api.login(AuthRequest(email, password))
        appConfig.authToken = response.token
        appConfig.userEmail = response.user.email
        appConfig.userId = response.user.id
        AuthResult.Success
    } catch (e: Exception) {
        Log.w(TAG, "Login failed", e)
        AuthResult.Failure(friendlyMessage(e, fallback = "Invalid email or password."))
    }

    fun logOut() = appConfig.logOut()

    /** Pushes a single vehicle if it doesn't have a remoteId yet, and saves the id it gets back. */
    suspend fun syncVehicle(vehicle: Vehicle): Long? {
        vehicle.remoteId?.let { return it }
        if (!appConfig.isLoggedIn) return null

        return try {
            val dto = api.createVehicle(
                CreateVehicleRequest(
                    nickname = vehicle.nickname,
                    plateNumber = vehicle.plateNumber,
                    ownerNotes = vehicle.ownerNotes
                )
            )
            licenseRepository.updateVehicle(vehicle.copy(remoteId = dto.id))
            dto.id
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync vehicle ${vehicle.id}", e)
            null
        }
    }

    /** Pushes a single license if it doesn't have a remoteId yet; syncs its parent vehicle first if needed. */
    suspend fun syncLicense(entry: LicenseEntry) {
        if (entry.remoteId != null) return
        if (!appConfig.isLoggedIn) return

        val vehicle = licenseRepository.getVehicleById(entry.vehicleId) ?: return
        val vehicleRemoteId = syncVehicle(vehicle) ?: return

        try {
            val dto = api.createLicense(
                vehicleRemoteId,
                CreateLicenseRequest(
                    licenseType = entry.licenseType,
                    expiryDate = dateFormat.format(Date(entry.expiryDateMillis)),
                    reminderDaysBefore = entry.reminderDaysBefore
                )
            )
            licenseRepository.updateLicense(entry.copy(remoteId = dto.id))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync license ${entry.id}", e)
        }
    }

    /** Pushes changes to an already-synced license, or does the initial sync if it was never pushed. */
    suspend fun updateLicenseRemote(entry: LicenseEntry) {
        if (!appConfig.isLoggedIn) return
        val remoteId = entry.remoteId
        if (remoteId == null) {
            syncLicense(entry)
            return
        }
        try {
            api.updateLicense(
                remoteId,
                CreateLicenseRequest(
                    licenseType = entry.licenseType,
                    expiryDate = dateFormat.format(Date(entry.expiryDateMillis)),
                    reminderDaysBefore = entry.reminderDaysBefore
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update remote license $remoteId", e)
        }
    }

    /** Deletes the remote counterpart of a license, if it was ever synced. Call before deleting locally. */
    suspend fun deleteLicenseRemote(entry: LicenseEntry) {
        val remoteId = entry.remoteId ?: return
        if (!appConfig.isLoggedIn) return
        try {
            api.deleteLicense(remoteId)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete remote license $remoteId", e)
        }
    }

    /** Deletes the remote counterpart of a vehicle, if it was ever synced. Call before deleting locally. */
    suspend fun deleteVehicleRemote(vehicle: Vehicle) {
        val remoteId = vehicle.remoteId ?: return
        if (!appConfig.isLoggedIn) return
        try {
            api.deleteVehicle(remoteId)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete remote vehicle $remoteId", e)
        }
    }

    /** Sweeps anything still missing a remoteId — used by the periodic SyncWorker to catch up after offline use. */
    suspend fun syncAllPending() {
        if (!appConfig.isLoggedIn) return
        licenseRepository.unsyncedVehicles().forEach { syncVehicle(it) }
        licenseRepository.unsyncedLicenses().forEach { syncLicense(it) }
    }

    /**
     * Pulls every vehicle and license the backend has for the logged-in user
     * and upserts them into Room, matched by remoteId. This is what makes a
     * fresh install or a second device show existing data instead of an
     * empty list — syncAllPending() only ever pushed local -> remote before.
     *
     * Returns the licenses that were newly inserted (didn't exist locally
     * yet), paired with their vehicle's nickname, so the caller can schedule
     * their local alarms — that needs an Android Context, which this
     * repository deliberately doesn't hold.
     */
    suspend fun pullFromBackend(): List<Pair<LicenseEntry, String>> {
        if (!appConfig.isLoggedIn) return emptyList()

        val newlyAddedLicenses = mutableListOf<Pair<LicenseEntry, String>>()

        try {
            val remoteVehicles = api.listVehicles()

            for (remoteVehicle in remoteVehicles) {
                val existingVehicle = licenseRepository.getVehicleByRemoteId(remoteVehicle.id)
                val localVehicleId: Long
                val vehicleNickname: String

                if (existingVehicle != null) {
                    val merged = existingVehicle.copy(
                        nickname = remoteVehicle.nickname,
                        plateNumber = remoteVehicle.plate_number,
                        ownerNotes = remoteVehicle.owner_notes
                    )
                    licenseRepository.updateVehicle(merged)
                    localVehicleId = merged.id
                    vehicleNickname = merged.nickname
                } else {
                    localVehicleId = licenseRepository.addVehicle(
                        Vehicle(
                            nickname = remoteVehicle.nickname,
                            plateNumber = remoteVehicle.plate_number,
                            ownerNotes = remoteVehicle.owner_notes,
                            remoteId = remoteVehicle.id
                        )
                    )
                    vehicleNickname = remoteVehicle.nickname
                }

                val remoteLicenses = try {
                    api.listLicenses(remoteVehicle.id)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to fetch licenses for vehicle ${remoteVehicle.id}", e)
                    emptyList()
                }

                for (remoteLicense in remoteLicenses) {
                    val expiryMillis = try {
                        dateFormat.parse(remoteLicense.expiry_date)?.time
                    } catch (e: Exception) {
                        null
                    } ?: continue

                    val existingLicense = licenseRepository.getLicenseByRemoteId(remoteLicense.id)
                    if (existingLicense != null) {
                        licenseRepository.updateLicense(
                            existingLicense.copy(
                                licenseType = remoteLicense.license_type,
                                expiryDateMillis = expiryMillis,
                                reminderDaysBefore = remoteLicense.reminder_days_before,
                                emailNotified = remoteLicense.email_notified
                            )
                        )
                    } else {
                        val newEntry = LicenseEntry(
                            vehicleId = localVehicleId,
                            licenseType = remoteLicense.license_type,
                            expiryDateMillis = expiryMillis,
                            reminderDaysBefore = remoteLicense.reminder_days_before,
                            emailNotified = remoteLicense.email_notified,
                            remoteId = remoteLicense.id
                        )
                        val newLocalId = licenseRepository.addLicense(newEntry)
                        newlyAddedLicenses.add(newEntry.copy(id = newLocalId) to vehicleNickname)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to pull data from backend", e)
        }

        return newlyAddedLicenses
    }

    /** Retrofit's HttpException carries the backend's own error message in its body when available. */
    private fun friendlyMessage(e: Exception, fallback: String): String {
        // Network-level failure (server down, wrong URL/port, cleartext blocked) - no HTTP response at all.
        if (e is java.io.IOException) {
            return "Can't reach the server at ${appConfig.backendBaseUrl} - is the backend running, and is the URL right?"
        }
        return (e as? retrofit2.HttpException)?.response()?.errorBody()?.string()
            ?.let { body ->
                Regex("\"error\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
            } ?: fallback
    }
}
