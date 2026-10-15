package com.example.licenseexpiry.data

import android.content.Context

/**
 * Holds the settings the networking layer needs. Backed by SharedPreferences
 * for now — swap this for DataStore/EncryptedSharedPreferences later if you
 * want stronger at-rest protection for the auth token.
 */
class AppConfig(context: Context) {

    private val prefs = context.getSharedPreferences("license_expiry_config", Context.MODE_PRIVATE)

    var backendBaseUrl: String
        get() = prefs.getString(KEY_BACKEND_URL, "http://127.0.0.1:3000/") ?: ""
        set(value) = prefs.edit().putString(KEY_BACKEND_URL, value).apply()

    /** Null until the user successfully registers or logs in. */
    var authToken: String?
        get() = prefs.getString(KEY_AUTH_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_AUTH_TOKEN, value).apply()

    var userEmail: String?
        get() = prefs.getString(KEY_USER_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_USER_EMAIL, value).apply()

    var userId: Long?
        get() = prefs.getLong(KEY_USER_ID, -1L).takeIf { it != -1L }
        set(value) = prefs.edit().putLong(KEY_USER_ID, value ?: -1L).apply()

    val wasRunningCleanly: Boolean
        get() = prefs.getBoolean(KEY_WAS_RUNNING, false)

    val lastActiveTime: Long
        get() = prefs.getLong(KEY_LAST_ACTIVE_TIME, System.currentTimeMillis())

    fun markAppStarted() {
        prefs.edit()
            .putBoolean(KEY_WAS_RUNNING, true)
            .putLong(KEY_LAST_ACTIVE_TIME, System.currentTimeMillis())
            .apply()
    }

    fun markAppStopped() {
        prefs.edit()
            .putBoolean(KEY_WAS_RUNNING, false)
            .putLong(KEY_LAST_ACTIVE_TIME, System.currentTimeMillis())
            .apply()
    }

    val isLoggedIn: Boolean
        get() = authToken != null

    /** Clears everything auth-related. Local Room data (vehicles/licenses) is left untouched. */
    fun logOut() {
        prefs.edit()
            .remove(KEY_AUTH_TOKEN)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_USER_ID)
            .putBoolean(KEY_WAS_RUNNING, false)
            .apply()
    }

    companion object {
        private const val KEY_BACKEND_URL = "backend_base_url"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_WAS_RUNNING = "was_running"
        private const val KEY_LAST_ACTIVE_TIME = "last_active_time"
    }
}
