package com.example.licenseexpiry.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches "Authorization: Bearer <token>" to every outgoing request using
 * whatever JWT is currently stored (from a successful login/register).
 * Requests made before logging in (there's no token yet) simply go out
 * without the header - the backend's requireAuth middleware will 401 them,
 * which is correct: register/login are the only routes that don't need one.
 */
class AuthInterceptor(private val tokenProvider: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = tokenProvider()

        val request = if (token != null) {
            original.newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            original
        }
        return chain.proceed(request)
    }
}
