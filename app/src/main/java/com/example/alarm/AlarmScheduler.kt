package com.example.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.AlarmEventLogger
import com.example.data.AppDatabase
import com.example.data.PreferencesManager
import com.example.data.model.Medication
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ScheduleOutcome(
    val success: Boolean,
    val triggerAtMillis: Long = 0L,
    val methodUsed: String = "",
    val error: String? = null
)

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs = PreferencesManager(context)

    companion object {
        const val ACTION_MEDICATION_ALARM = "com.example.ACTION_MEDICATION_ALARM"
        const val ACTION_SNOOZE_ALARM = "com.example.ACTION_SNOOZE_ALARM"

        const val EXTRA_MEDICATION_ID = "extra_medication_id"
        const val EXTRA_MEDICATION_NAME = "extra_medication_name"
        const val EXTRA_DOSAGE = "extra_dosage"
        const val EXTRA_MEAL_TIMING = "extra_meal_timing"
        const val EXTRA_SCHEDULED_TIME = "extra_scheduled_time"
        const val EXTRA_COLOR_HEX = "extra_color_hex"
        const val EXTRA_AUDIO_PATH = "extra_audio_path"
        const val EXTRA_IS_SNOOZE = "extra_is_snooze"
        const val EXTRA_IS_TEST = "extra_is_test"

        // Schedule metadata carried in Intent so AlarmReceiver can schedule the next occurrence
        // BEFORE reading the database.
        const val EXTRA_FREQUENCY_TYPE = "extra_frequency_type"
        const val EXTRA_DAYS_OF_WEEK = "extra_days_of_week"
        const val EXTRA_TIMES_OF_DAY = "extra_times_of_day"
        const val EXTRA_INTERVAL_HOURS = "extra_interval_hours"
        const val EXTRA_CREATED_AT = "extra_created_at"
    }

    init {
        cancelPhantom999999()
    }

    /**
     * Schedules the NEXT occurrence of an alarm using ONLY Intent extras (zero database reads).
     * Intended to run at the very beginning of AlarmReceiver.onReceive().
     */
    fun scheduleNextOccurrenceFromIntent(intent: Intent): ScheduleOutcome {
        val medicationId = intent.getLongExtra(EXTRA_MEDICATION_ID, -1L)
        val scheduledTime = intent.getStringExtra(EXTRA_SCHEDULED_TIME) ?: ""
        if (medicationId <= 0L || medicationId == 999999L || !scheduledTime.contains(":")) {
            return ScheduleOutcome(
                success = false,
                error = "Geçersiz ilaç kimliği veya saat ($medicationId / $scheduledTime)"
            )
        }

        val parts = scheduledTime.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull()
        val minute = parts.getOrNull(1)?.toIntOrNull()
        if (hour == null || minute == null) {
            return ScheduleOutcome(
                success = false,
                error = "Saat ayrıştırılamadı: $scheduledTime"
            )
        }

        val name = intent.getStringExtra(EXTRA_MEDICATION_NAME) ?: "İlaç"
        val dosage = intent.getStringExtra(EXTRA_DOSAGE) ?: "1 Tablet"
        val mealTiming = intent.getStringExtra(EXTRA_MEAL_TIMING) ?: "Tok Karna"
        val colorHex = intent.getLongExtra(EXTRA_COLOR_HEX, 0xFF1976D2)
        val audioPath = intent.getStringExtra(EXTRA_AUDIO_PATH)
        val frequencyType = intent.getStringExtra(EXTRA_FREQUENCY_TYPE) ?: "DAILY"
        val daysOfWeek = intent.getStringExtra(EXTRA_DAYS_OF_WEEK) ?: "1,2,3,4,5,6,7"
        val timesOfDay = intent.getStringExtra(EXTRA_TIMES_OF_DAY) ?: scheduledTime
        val intervalHours = intent.getIntExtra(EXTRA_INTERVAL_HOURS, 8)
        val createdAt = intent.getLongExtra(EXTRA_CREATED_AT, System.currentTimeMillis())

        val ephemeralMed = Medication(
            id = medicationId,
            name = name,
            dosage = dosage,
            mealTiming = mealTiming,
            colorHex = colorHex,
            frequencyType = frequencyType,
            timesOfDay = timesOfDay,
            intervalHours = intervalHours,
            daysOfWeek = daysOfWeek,
            customAudioPath = audioPath,
            isActive = true,
            createdAt = createdAt
        )

        val nowPlusBuffer = System.currentTimeMillis() + 60_000L
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            while (timeInMillis <= nowPlusBuffer) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        var attempts = 0
        val maxAttempts = (ephemeralMed.parseIntervalDays() ?: 7) + 7
        while (attempts < maxOf(14, maxAttempts) && !ephemeralMed.appliesToToday(calendar)) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            attempts++
        }

        val requestCode = getRequestCode(medicationId, hour, minute)
        val nextIntent = createAlarmIntent(ephemeralMed, scheduledTime, isSnooze = false)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return scheduleExact(calendar.timeInMillis, pendingIntent)
    }

    /**
     * Schedules alarms for all scheduled times of an active medication.
     */
    fun scheduleMedication(medication: Medication): Int {
        if (!medication.isActive) {
            cancelMedication(medication)
            return 0
        }

        var scheduledCount = 0
        val times = medication.parseTimesList()
        for (timeStr in times) {
            val parts = timeStr.split(":")
            if (parts.size != 2) continue
            val hour = parts[0].toIntOrNull() ?: continue
            val minute = parts[1].toIntOrNull() ?: continue

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)

                // If already in the past today, schedule for tomorrow
                if (before(Calendar.getInstance())) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            var attempts = 0
            val maxAttempts = (medication.parseIntervalDays() ?: 7) + 7
            while (attempts < maxOf(14, maxAttempts) && !medication.appliesToToday(calendar)) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                attempts++
            }

            val requestCode = getRequestCode(medication.id, hour, minute)
            val intent = createAlarmIntent(medication, timeStr, isSnooze = false)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val outcome = scheduleExact(calendar.timeInMillis, pendingIntent)
            if (outcome.success) {
                scheduledCount++
            } else {
                AlarmEventLogger.log(
                    context = context,
                    eventType = AlarmEventLogger.TYPE_ERROR,
                    medicationId = medication.id,
                    medicationName = medication.name,
                    scheduledTime = timeStr,
                    details = "Alarm kurulamadı: ${outcome.error ?: "Bilinmeyen hata"}"
                )
            }
            Log.d("AlarmScheduler", "Scheduled alarm for ${medication.name} at ${calendar.time} (${outcome.methodUsed})")
        }
        return scheduledCount
    }

    /**
     * Re-arms all active medications from Room DB (used on app launch, boot, and daily WorkManager).
     */
    suspend fun rescheduleAllActiveMedications(source: String): Int {
        return try {
            val db = AppDatabase.getDatabase(context)
            val activeMeds = db.medicationDao().getActiveMedicationsSync()
            var totalAlarms = 0
            for (med in activeMeds) {
                totalAlarms += scheduleMedication(med)
            }
            AlarmEventLogger.log(
                context = context,
                eventType = AlarmEventLogger.TYPE_RESCHEDULED,
                medicationId = -1L,
                medicationName = "${activeMeds.size} aktif ilaç",
                scheduledTime = "",
                details = "$source: ${activeMeds.size} aktif ilaç için $totalAlarms alarm yeniden kuruldu."
            )
            totalAlarms
        } catch (t: Throwable) {
            AlarmEventLogger.log(
                context = context,
                eventType = AlarmEventLogger.TYPE_ERROR,
                medicationId = -1L,
                medicationName = "Sistem",
                scheduledTime = "",
                details = "$source sırasında alarmlar yeniden kurulamadı: ${t.localizedMessage ?: t::class.java.simpleName}"
            )
            0
        }
    }

    /**
     * Schedules repeat / snooze alarm when user snoozes or leaves without confirming intake.
     */
    fun scheduleSnooze(
        medicationId: Long,
        medicationName: String,
        dosage: String,
        mealTiming: String,
        scheduledTime: String,
        colorHex: Long,
        audioPath: String?,
        delayMinutes: Int = prefs.snoozeMinutes
    ) {
        val triggerTime = System.currentTimeMillis() + (delayMinutes * 60 * 1000L)
        val requestCode = (medicationId * 1000 + 999).toInt()

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_SNOOZE_ALARM
            putExtra(EXTRA_MEDICATION_ID, medicationId)
            putExtra(EXTRA_MEDICATION_NAME, medicationName)
            putExtra(EXTRA_DOSAGE, dosage)
            putExtra(EXTRA_MEAL_TIMING, mealTiming)
            putExtra(EXTRA_SCHEDULED_TIME, scheduledTime)
            putExtra(EXTRA_COLOR_HEX, colorHex)
            putExtra(EXTRA_AUDIO_PATH, audioPath)
            putExtra(EXTRA_IS_SNOOZE, true)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val outcome = scheduleExact(triggerTime, pendingIntent)
        val formattedTarget = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(triggerTime))
        AlarmEventLogger.log(
            context = context,
            eventType = if (outcome.success) AlarmEventLogger.TYPE_NEXT_SCHEDULED else AlarmEventLogger.TYPE_ERROR,
            medicationId = medicationId,
            medicationName = medicationName,
            scheduledTime = scheduledTime,
            details = if (outcome.success) {
                "Erteleme alarmı $delayMinutes dk sonrası ($formattedTarget) için kuruldu."
            } else {
                "Erteleme alarmı kurulamadı: ${outcome.error}"
            }
        )
        Log.d("AlarmScheduler", "Scheduled snooze alarm for $medicationName in $delayMinutes min")
    }

    /**
     * Cancels any pending snooze for this medication.
     */
    fun cancelSnooze(medicationId: Long) {
        val requestCode = (medicationId * 1000 + 999).toInt()
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_SNOOZE_ALARM
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    /**
     * Cancels all scheduled alarms for a medication.
     */
    fun cancelMedication(medication: Medication) {
        cancelMedicationById(medication.id, medication.parseTimesList())
    }

    /**
     * Cancels all scheduled alarms for a medication by ID and known times.
     */
    fun cancelMedicationById(medicationId: Long, times: List<String> = emptyList()) {
        cancelSnooze(medicationId)
        val combinedTimes = (times + listOf(
            "00:00", "01:00", "02:00", "03:00", "04:00", "05:00",
            "06:00", "07:00", "08:00", "09:00", "10:00", "11:00", "12:00",
            "13:00", "14:00", "15:00", "16:00", "17:00", "18:00", "19:00",
            "20:00", "21:00", "22:00", "23:00"
        )).distinct()

        for (timeStr in combinedTimes) {
            val parts = timeStr.split(":")
            if (parts.size != 2) continue
            val hour = parts[0].toIntOrNull() ?: continue
            val minute = parts[1].toIntOrNull() ?: continue

            val requestCode = getRequestCode(medicationId, hour, minute)
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_MEDICATION_ALARM
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    /**
     * Quick test alarm trigger for demonstration or immediate test of an existing medication.
     */
    fun triggerImmediateTest(
        medicationId: Long,
        medicationName: String,
        dosage: String,
        mealTiming: String,
        audioPath: String?
    ) {
        val triggerTime = System.currentTimeMillis() + 2000L // 2 seconds from now
        val requestCode = (medicationId * 1000 + 888).toInt()

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_MEDICATION_ALARM
            putExtra(EXTRA_MEDICATION_ID, medicationId)
            putExtra(EXTRA_MEDICATION_NAME, medicationName)
            putExtra(EXTRA_DOSAGE, dosage)
            putExtra(EXTRA_MEAL_TIMING, mealTiming)
            putExtra(EXTRA_SCHEDULED_TIME, "12:00")
            putExtra(EXTRA_COLOR_HEX, 0xFF1976D2)
            putExtra(EXTRA_AUDIO_PATH, audioPath)
            putExtra(EXTRA_IS_SNOOZE, false)
            putExtra(EXTRA_IS_TEST, true)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        scheduleExact(triggerTime, pendingIntent)
    }

    /**
     * Cancels any phantom or legacy alarms from past test runs.
     */
    fun cancelPhantom999999() {
        try {
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_MEDICATION_ALARM
            }
            val pi = PendingIntent.getBroadcast(
                context,
                999999,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pi)
            pi.cancel()

            val snoozeIntent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_SNOOZE_ALARM
            }
            val snoozePi = PendingIntent.getBroadcast(
                context,
                (999999L * 1000 + 999).toInt(),
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(snoozePi)
            snoozePi.cancel()
        } catch (_: Exception) {
            // ignore
        }
    }

    private fun scheduleExact(timeInMillis: Long, pendingIntent: PendingIntent): ScheduleOutcome {
        return try {
            val showIntent = Intent(context, com.example.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val showPendingIntent = PendingIntent.getActivity(
                context,
                0,
                showIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmClockInfo = AlarmManager.AlarmClockInfo(timeInMillis, showPendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            ScheduleOutcome(success = true, triggerAtMillis = timeInMillis, methodUsed = "setAlarmClock")
        } catch (e: Exception) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
                }
                ScheduleOutcome(success = true, triggerAtMillis = timeInMillis, methodUsed = "setExactAndAllowWhileIdle")
            } catch (e2: Exception) {
                try {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
                    ScheduleOutcome(
                        success = true,
                        triggerAtMillis = timeInMillis,
                        methodUsed = "set (exact alarm izni yok: ${e2.localizedMessage})"
                    )
                } catch (e3: Exception) {
                    ScheduleOutcome(
                        success = false,
                        triggerAtMillis = timeInMillis,
                        error = e3.localizedMessage ?: e3::class.java.simpleName
                    )
                }
            }
        }
    }

    private fun createAlarmIntent(medication: Medication, scheduledTime: String, isSnooze: Boolean): Intent {
        return Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_MEDICATION_ALARM
            putExtra(EXTRA_MEDICATION_ID, medication.id)
            putExtra(EXTRA_MEDICATION_NAME, medication.name)
            putExtra(EXTRA_DOSAGE, medication.dosage)
            putExtra(EXTRA_MEAL_TIMING, medication.mealTiming)
            putExtra(EXTRA_SCHEDULED_TIME, scheduledTime)
            putExtra(EXTRA_COLOR_HEX, medication.colorHex)
            putExtra(EXTRA_AUDIO_PATH, medication.customAudioPath)
            putExtra(EXTRA_IS_SNOOZE, isSnooze)
            putExtra(EXTRA_FREQUENCY_TYPE, medication.frequencyType)
            putExtra(EXTRA_DAYS_OF_WEEK, medication.daysOfWeek)
            putExtra(EXTRA_TIMES_OF_DAY, medication.timesOfDay)
            putExtra(EXTRA_INTERVAL_HOURS, medication.intervalHours)
            putExtra(EXTRA_CREATED_AT, medication.createdAt)
        }
    }

    private fun getRequestCode(medicationId: Long, hour: Int, minute: Int): Int {
        return (medicationId * 10000 + hour * 60 + minute).toInt()
    }
}
