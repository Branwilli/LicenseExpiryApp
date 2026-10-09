package com.example.licenseexpiry.network

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

// --- Auth ---

data class AuthRequest(val email: String, val password: String)
data class UserDto(val id: Long, val email: String)
data class AuthResponse(val token: String, val user: UserDto)

// --- Vehicles / licenses ---

data class VehicleDto(
    val id: Long,
    val user_id: Long,
    val nickname: String,
    val plate_number: String,
    val owner_notes: String?
)
data class CreateVehicleRequest(
    val nickname: String,
    val plateNumber: String,
    val ownerNotes: String? = null
)

data class LicenseDto(
    val id: Long,
    val vehicle_id: Long,
    val license_type: String,
    val expiry_date: String, // "YYYY-MM-DD"
    val reminder_days_before: Int,
    val email_notified: Boolean
)
data class CreateLicenseRequest(
    val licenseType: String,
    val expiryDate: String, // "YYYY-MM-DD"
    val reminderDaysBefore: Int
)

/**
 * Mirrors backend/routes. register/login need no token; every other
 * call needs one, which AuthInterceptor attaches automatically from
 * AppConfig.authToken. Vehicle/license paths no longer take a userId -
 * the backend reads the authenticated user from the JWT instead (see
 * middleware/auth.js), so a token only ever sees its own data.
 */
interface ApiService {

    @POST("auth/register")
    suspend fun register(@Body request: AuthRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body request: AuthRequest): AuthResponse

    @GET("me")
    suspend fun me(): UserDto

    @GET("vehicles")
    suspend fun listVehicles(): List<VehicleDto>

    @POST("vehicles")
    suspend fun createVehicle(@Body request: CreateVehicleRequest): VehicleDto

    @DELETE("vehicles/{id}")
    suspend fun deleteVehicle(@Path("id") vehicleId: Long)

    @GET("vehicles/{vehicleId}/licenses")
    suspend fun listLicenses(@Path("vehicleId") vehicleId: Long): List<LicenseDto>

    @POST("vehicles/{vehicleId}/licenses")
    suspend fun createLicense(
        @Path("vehicleId") vehicleId: Long,
        @Body request: CreateLicenseRequest
    ): LicenseDto

    @PUT("licenses/{id}")
    suspend fun updateLicense(
        @Path("id") licenseId: Long,
        @Body request: CreateLicenseRequest
    ): LicenseDto

    @DELETE("licenses/{id}")
    suspend fun deleteLicense(@Path("id") licenseId: Long)
}
