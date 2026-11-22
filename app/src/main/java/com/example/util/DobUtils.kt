package com.example.util

import java.util.Calendar

data class DobMonthOption(
    val number: Int,
    val label: String
)

sealed interface DobValidationResult {
    data class Valid(
        val dobFormatted: String,
        val age: Int
    ) : DobValidationResult

    data class Invalid(val message: String) : DobValidationResult
}

object DobUtils {
    val DAY_OPTIONS: List<Int> = (1..31).toList()

    val MONTH_OPTIONS: List<DobMonthOption> = listOf(
        DobMonthOption(1, "01 • Jan"),
        DobMonthOption(2, "02 • Feb"),
        DobMonthOption(3, "03 • Mar"),
        DobMonthOption(4, "04 • Apr"),
        DobMonthOption(5, "05 • May"),
        DobMonthOption(6, "06 • Jun"),
        DobMonthOption(7, "07 • Jul"),
        DobMonthOption(8, "08 • Aug"),
        DobMonthOption(9, "09 • Sep"),
        DobMonthOption(10, "10 • Oct"),
        DobMonthOption(11, "11 • Nov"),
        DobMonthOption(12, "12 • Dec")
    )

    fun yearOptions(referenceCal: Calendar = Calendar.getInstance()): List<Int> {
        val currentYear = referenceCal.get(Calendar.YEAR)
        val maxAdultYear = (currentYear - 18).coerceAtLeast(1930)
        return (maxAdultYear downTo 1925).toList()
    }

    fun isLeapYear(year: Int): Boolean {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
    }

    fun daysInMonth(month: Int, year: Int): Int {
        return when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if (isLeapYear(year)) 29 else 28
            else -> 31
        }
    }

    fun calculateAge(
        day: Int,
        month: Int,
        year: Int,
        referenceCal: Calendar = Calendar.getInstance()
    ): Int {
        val todayYear = referenceCal.get(Calendar.YEAR)
        val todayMonth = referenceCal.get(Calendar.MONTH) + 1
        val todayDay = referenceCal.get(Calendar.DAY_OF_MONTH)

        var age = todayYear - year
        if (todayMonth < month || (todayMonth == month && todayDay < day)) {
            age -= 1
        }
        return age
    }

    fun formatDob(day: Int, month: Int, year: Int): String =
        "%02d-%02d-%04d".format(day, month, year)

    fun parseDob(dob: String?): Triple<Int, Int, Int>? {
        val clean = dob?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val parts = clean.split("-")
        if (parts.size != 3) return null
        val d = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val y = parts[2].toIntOrNull() ?: return null
        if (m !in 1..12 || y < 1900 || d !in 1..daysInMonth(m, y)) return null
        return Triple(d, m, y)
    }

    fun validateDob(
        day: Int?,
        month: Int?,
        year: Int?,
        referenceCal: Calendar = Calendar.getInstance()
    ): DobValidationResult {
        if (day == null || month == null || year == null) {
            return DobValidationResult.Invalid("Select Day, Month, and Year of birth (DD-MM-YYYY).")
        }
        if (month !in 1..12) {
            return DobValidationResult.Invalid("Select a valid birth month.")
        }
        val maxDay = daysInMonth(month, year)
        if (day !in 1..maxDay) {
            return DobValidationResult.Invalid("Selected day ($day) does not exist in month $month of $year.")
        }
        val age = calculateAge(day, month, year, referenceCal)
        if (age < 18) {
            return DobValidationResult.Invalid("You must be at least 18 years old to join Kindred.")
        }
        if (age > 99) {
            return DobValidationResult.Invalid("Please enter a valid date of birth (age 18–99).")
        }
        return DobValidationResult.Valid(
            dobFormatted = formatDob(day, month, year),
            age = age
        )
    }
}
