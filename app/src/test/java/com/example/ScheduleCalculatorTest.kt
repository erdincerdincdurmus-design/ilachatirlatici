package com.example

import com.example.data.model.Medication
import com.example.data.model.ScheduleCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ScheduleCalculatorTest {

    @Test
    fun dailyTimes_respects24HourDivisionFromStartTime() {
        // 08:00 start across 1..6 doses (24 / count hours)
        assertEquals(listOf("08:00"), ScheduleCalculator.generateDailyTimes("08:00", 1))
        assertEquals(listOf("08:00", "20:00"), ScheduleCalculator.generateDailyTimes("08:00", 2))
        assertEquals(listOf("08:00", "16:00", "00:00"), ScheduleCalculator.generateDailyTimes("08:00", 3))
        assertEquals(listOf("08:00", "14:00", "20:00", "02:00"), ScheduleCalculator.generateDailyTimes("08:00", 4))
        assertEquals(
            listOf("08:00", "12:48", "17:36", "22:24", "03:12"),
            ScheduleCalculator.generateDailyTimes("08:00", 5)
        )
        assertEquals(
            listOf("08:00", "12:00", "16:00", "20:00", "00:00", "04:00"),
            ScheduleCalculator.generateDailyTimes("08:00", 6)
        )

        // 09:00 start across 1..6 doses (24 / count hours)
        assertEquals(listOf("09:00"), ScheduleCalculator.generateDailyTimes("09:00", 1))
        assertEquals(listOf("09:00", "21:00"), ScheduleCalculator.generateDailyTimes("09:00", 2))
        assertEquals(listOf("09:00", "17:00", "01:00"), ScheduleCalculator.generateDailyTimes("09:00", 3))
        assertEquals(listOf("09:00", "15:00", "21:00", "03:00"), ScheduleCalculator.generateDailyTimes("09:00", 4))
        assertEquals(
            listOf("09:00", "13:48", "18:36", "23:24", "04:12"),
            ScheduleCalculator.generateDailyTimes("09:00", 5)
        )
        assertEquals(
            listOf("09:00", "13:00", "17:00", "21:00", "01:00", "05:00"),
            ScheduleCalculator.generateDailyTimes("09:00", 6)
        )
    }

    @Test
    fun intervalHourTimes_stepsEvenlyAcross24Hours() {
        assertEquals(
            listOf("09:00", "17:00", "01:00"),
            ScheduleCalculator.generateIntervalHourTimes("09:00", 8)
        )
        assertEquals(
            listOf("09:00", "15:00", "21:00", "03:00"),
            ScheduleCalculator.generateIntervalHourTimes("09:00", 6)
        )
    }

    @Test
    fun dynamicEveryNDays_appliesToExactCalendarDays() {
        val createdCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 15, 22, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val med = Medication(
            name = "Test İlaç",
            frequencyType = "EVERY_5_DAYS",
            createdAt = createdCal.timeInMillis
        )
        assertEquals(5, med.parseIntervalDays())

        val day0 = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 15, 8, 0, 0) }
        val day1 = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 16, 8, 0, 0) }
        val day5 = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 20, 8, 0, 0) }

        assertTrue(med.appliesToToday(day0))
        assertFalse(med.appliesToToday(day1))
        assertTrue(med.appliesToToday(day5))
    }
}
