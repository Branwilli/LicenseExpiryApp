package com.example.licenseexpiry.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST


data class ExpiryEmailRequest(
    val toEmail: String,
    val licenseType: String,
    val expiryDate: String
)

interface EmailApiService {
    @POST("notify-expiry")
    suspend fun sendExpiryEmail(@Body request: ExpiryEmailRequest)
    companion object {
        fun create(baseUrl: String): EmailApiService {
            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(EmailApiService::class.java)
        }
    }
}
