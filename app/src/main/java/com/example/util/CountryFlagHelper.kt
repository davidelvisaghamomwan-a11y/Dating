package com.example.util

import java.util.Locale

object CountryFlagHelper {
    private val countryToIso: Map<String, String> = mapOf(
        "united states" to "US",
        "usa" to "US",
        "us" to "US",
        "united states of america" to "US",
        "united kingdom" to "GB",
        "uk" to "GB",
        "great britain" to "GB",
        "england" to "GB",
        "canada" to "CA",
        "nigeria" to "NG",
        "france" to "FR",
        "germany" to "DE",
        "spain" to "ES",
        "italy" to "IT",
        "netherlands" to "NL",
        "australia" to "AU",
        "japan" to "JP",
        "south korea" to "KR",
        "korea" to "KR",
        "india" to "IN",
        "brazil" to "BR",
        "kenya" to "KE",
        "south africa" to "ZA",
        "ghana" to "GH",
        "united arab emirates" to "AE",
        "uae" to "AE",
        "singapore" to "SG",
        "mexico" to "MX",
        "portugal" to "PT",
        "ireland" to "IE",
        "sweden" to "SE",
        "norway" to "NO",
        "denmark" to "DK",
        "switzerland" to "CH",
        "poland" to "PL",
        "egypt" to "EG",
        "morocco" to "MA",
        "philippines" to "PH",
        "indonesia" to "ID",
        "thailand" to "TH",
        "argentina" to "AR",
        "colombia" to "CO",
        "new zealand" to "NZ"
    )

    private val isoByDisplayName: Map<String, String> by lazy {
        val map = HashMap<String, String>()
        Locale.getISOCountries().forEach { iso ->
            val name = Locale("", iso).getDisplayCountry(Locale.ENGLISH).lowercase(Locale.US)
            if (name.isNotBlank()) {
                map[name] = iso
            }
        }
        map
    }

    fun flagEmojiForCountry(country: String?): String {
        val raw = country?.trim()?.takeIf { it.isNotBlank() } ?: return ""
        val key = raw.lowercase(Locale.US)
        val iso = countryToIso[key]
            ?: if (raw.length == 2 && raw.all { it.isLetter() }) raw.uppercase(Locale.US) else isoByDisplayName[key]
            ?: return ""
        if (iso.length != 2) return ""
        val first = Character.codePointAt(iso, 0) - 0x41 + 0x1F1E6
        val second = Character.codePointAt(iso, 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(first)) + String(Character.toChars(second))
    }

    fun nameAgeWithFlag(displayName: String, age: Long, country: String?): String {
        val flag = flagEmojiForCountry(country)
        return if (flag.isNotBlank()) "$displayName, $age $flag" else "$displayName, $age"
    }

    fun nameWithFlag(displayName: String, country: String?): String {
        val flag = flagEmojiForCountry(country)
        return if (flag.isNotBlank()) "$displayName $flag" else displayName
    }
}
