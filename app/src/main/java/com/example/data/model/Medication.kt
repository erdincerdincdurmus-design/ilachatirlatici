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
            .distinct()
    }

    fun parseIntervalDays(): Int? {
        val match = Regex("^EVERY_(\\d+)_DAYS$").find(frequencyType)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it >= 2 }
    }

    private fun daysSinceCreation(target: Calendar): Int {
        val createdNoon = Calendar.getInstance().apply {
            timeInMillis = createdAt
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val targetNoon = (target.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val diffMillis = targetNoon.timeInMillis - createdNoon.timeInMillis
        return kotlin.math.round(diffMillis / (1000.0 * 60.0 * 60.0 * 24.0)).toInt()
    }

    fun appliesToToday(calendar: Calendar = Calendar.getInstance()): Boolean {
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        // Convert Java Calendar (Sunday=1, Monday=2 .. Saturday=7) to 1=Monday..7=Sunday
        val normalizedDay = if (dayOfWeek == Calendar.SUNDAY) 7 else dayOfWeek - 1

        val intervalDays = parseIntervalDays()
        if (intervalDays != null) {
            val daysDiff = daysSinceCreation(calendar)
            return daysDiff >= 0 && daysDiff % intervalDays == 0
        }

        return when (frequencyType) {
            "DAILY" -> true
            "WEEKLY_DAYS", "CUSTOM_DAYS" -> {
                val allowedDays = daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }
                allowedDays.isEmpty() || allowedDays.contains(normalizedDay)
            }
            else -> true
        }
    }
}

object ScheduleCalculator {
    fun formatTime(hour: Int, minute: Int): String {
        val h = ((hour % 24) + 24) % 24
        val m = minute.coerceIn(0, 59)
        return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
    }

    fun parseTime(timeStr: String): Pair<Int, Int> {
        val parts = timeStr.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 8
        val m = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        return h to m
    }

    /**
     * Divides the 24-hour day equally into [doseCount] intervals (24 / doseCount hours)
     * starting from [startTime] and preserves chronological cycle order.
     * Examples from 08:00:
     * - 1x (24h): ["08:00"]
     * - 2x (12h): ["08:00", "20:00"]
     * - 3x (8h):  ["08:00", "16:00", "00:00"]
     * - 4x (6h):  ["08:00", "14:00", "20:00", "02:00"]
     * - 5x (4h48m): ["08:00", "12:48", "17:36", "22:24", "03:12"]
     * - 6x (4h):  ["08:00", "12:00", "16:00", "20:00", "00:00", "04:00"]
     */
    fun generateDailyTimes(startTime: String, doseCount: Int): List<String> {
        val count = doseCount.coerceIn(1, 24)
        val (startH, startM) = parseTime(startTime)
        val startMins = startH * 60 + startM

        if (count == 1) {
            return listOf(formatTime(startH, startM))
        }

        val stepMins = 1440.0 / count
        return (0 until count).map { i ->
            val total = kotlin.math.round(startMins + i * stepMins).toInt() % 1440
            formatTime(total / 60, total % 60)
        }.distinct()
    }

    /**
     * Generates alarm times spaced by [everyHours] starting from [startTime], preserving cycle order.
     */
    fun generateIntervalHourTimes(startTime: String, everyHours: Int): List<String> {
        val stepHours = everyHours.coerceIn(1, 24)
        val (startH, startM) = parseTime(startTime)
        val startMins = startH * 60 + startM
        val count = (24 / stepHours).coerceAtLeast(1)

        return (0 until count).map { i ->
            val total = (startMins + i * stepHours * 60) % 1440
            formatTime(total / 60, total % 60)
        }.distinct()
    }
}
