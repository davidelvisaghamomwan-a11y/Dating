package com.example.auth

import android.content.Context

data class PendingRegistrationData(
    val displayName: String = "",
    val age: Int = 26,
    val gender: String = "Woman"
)

object PendingRegistrationPrefs {
    private const val PREFS_NAME = "kindred_pending_registration"
    private const val KEY_DISPLAY_NAME = "display_name"
    private const val KEY_AGE = "age"
    private const val KEY_GENDER = "gender"
    private const val KEY_HAS_PENDING = "has_pending"

    fun save(context: Context, displayName: String, age: Int, gender: String) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DISPLAY_NAME, displayName.trim().take(40))
            .putInt(KEY_AGE, age.coerceIn(18, 99))
            .putString(KEY_GENDER, gender)
            .putBoolean(KEY_HAS_PENDING, true)
            .apply()
    }

    fun get(context: Context): PendingRegistrationData? {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_HAS_PENDING, false)) return null
        return PendingRegistrationData(
            displayName = prefs.getString(KEY_DISPLAY_NAME, "").orEmpty(),
            age = prefs.getInt(KEY_AGE, 26).coerceIn(18, 99),
            gender = prefs.getString(KEY_GENDER, "Woman") ?: "Woman"
        )
    }

    fun clear(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
