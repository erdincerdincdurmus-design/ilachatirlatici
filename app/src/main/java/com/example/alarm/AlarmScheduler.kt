package com.example.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.PreferencesManager
import com.example.data.model.Medication
import java.util.Calendar

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
    }

    init {
        cancelPhantom999999()
    }

    /**
     * Schedules alarms for all scheduled times of an active medication.
     */
    fun scheduleMedication(medication: Medication) {
        if (!medication.isActive) {
            cancelMedication(medication)
            return
        }

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
            while (attempts < 14 && !medication.appliesToToday(calendar)) {
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

            scheduleExact(calendar.timeInMillis, pendingIntent)
            Log.d("AlarmScheduler", "Scheduled alarm for ${medication.name} at ${calendar.time}")
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

        scheduleExact(triggerTime, pendingIntent)
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
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun scheduleExact(timeInMillis: Long, pendingIntent: PendingIntent) {
        try {
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
        } catch (e: Exception) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
                }
            } catch (e2: Exception) {
                // If exact alarm permission not yet granted, fallback to standard alarm
                alarmManager.set(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
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
        }
    }

    private fun getRequestCode(medicationId: Long, hour: Int, minute: Int): Int {
        return (medicationId * 10000 + hour * 60 + minute).toInt()
    }
}
