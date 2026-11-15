package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Calendar

/**
 * Entity representing a scheduled medication for elderly care.
 */
@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val dosage: String = "1 Tablet",
    val mealTiming: String = "Tok Karna", // "Tok Karna", "Aç Karna", "Yemekle Birlikte", "Fark Etmez"
    val colorHex: Long = 0xFF1976D2, // Default medical blue
    val iconType: String = "tablet", // "tablet", "capsule", "syrup", "drops"
    val frequencyType: String = "DAILY", // "DAILY", "EVERY_2_DAYS", "EVERY_3_DAYS", "CUSTOM_DAYS", "WEEKLY_DAYS"
    val timesOfDay: String = "08:00", // e.g., "08:00,13:00,20:00"
    val intervalHours: Int = 8, // for every X hours
    val daysOfWeek: String = "1,2,3,4,5,6,7", // comma-separated day numbers (1=Mon..7=Sun)
    val customAudioPath: String? = null, // Local recording file path (e.g. from loved one)
    val isActive: Boolean = true,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun parseTimesList(): List<String> {
        return timesOfDay.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .sorted()
    }

    fun appliesToToday(calendar: Calendar = Calendar.getInstance()): Boolean {
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        // Convert Java Calendar (Sunday=1, Monday=2 .. Saturday=7) to 1=Monday..7=Sunday
        val normalizedDay = if (dayOfWeek == Calendar.SUNDAY) 7 else dayOfWeek - 1

        return when (frequencyType) {
            "DAILY" -> true
            "EVERY_2_DAYS" -> {
                val createdCal = Calendar.getInstance().apply { timeInMillis = createdAt }
                val daysDiff = ((calendar.timeInMillis - createdCal.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()
                daysDiff % 2 == 0
            }
            "EVERY_3_DAYS" -> {
                val createdCal = Calendar.getInstance().apply { timeInMillis = createdAt }
                val daysDiff = ((calendar.timeInMillis - createdCal.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()
                daysDiff % 3 == 0
            }
            "WEEKLY_DAYS", "CUSTOM_DAYS" -> {
                val allowedDays = daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }
                allowedDays.isEmpty() || allowedDays.contains(normalizedDay)
            }
            else -> true
        }
    }
}
