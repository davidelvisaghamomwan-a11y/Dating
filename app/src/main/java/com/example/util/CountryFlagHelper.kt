package com.example.util

object CountryFlagHelper {
    fun flagEmojiForCountry(country: String?): String {
        return ""
    }

    fun nameAgeWithFlag(displayName: String, age: Long, country: String?): String {
        return "$displayName, $age"
    }

    fun nameWithFlag(displayName: String, country: String?): String {
        return displayName
    }
}
