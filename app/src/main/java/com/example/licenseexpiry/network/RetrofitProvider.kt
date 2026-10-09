package com.example.licenseexpiry.network

import com.example.licenseexpiry.data.AppConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitProvider {

    /**
     * Builds a fresh ApiService each call rather than caching a singleton,
     * since the backend URL can change (e.g. once a settings screen exists)
     * and OkHttpClient construction is cheap relative to a network call.
     */
    fun create(appConfig: AppConfig): ApiService {
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor { appConfig.authToken })
            .build()

        return Retrofit.Builder()
            .baseUrl(appConfig.backendBaseUrl) // must end with "/"
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
