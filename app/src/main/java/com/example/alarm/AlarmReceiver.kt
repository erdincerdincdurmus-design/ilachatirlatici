package com.example.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.data.PreferencesManager

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "medication_silent_alarm_channel_v10"
        const val CHANNEL_NAME = "İlaç Hatırlatıcı Bildirimleri"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val medicationId = intent.getLongExtra(AlarmScheduler.EXTRA_MEDICATION_ID, -1L)
        val isTest = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_TEST, false)
        val rawName = intent.getStringExtra(AlarmScheduler.EXTRA_MEDICATION_NAME) ?: ""
        val dosage = intent.getStringExtra(AlarmScheduler.EXTRA_DOSAGE) ?: "1 Tablet"
        val mealTiming = intent.getStringExtra(AlarmScheduler.EXTRA_MEAL_TIMING) ?: "Tok Karna"
        val scheduledTime = intent.getStringExtra(AlarmScheduler.EXTRA_SCHEDULED_TIME) ?: ""
        val colorHex = intent.getLongExtra(AlarmScheduler.EXTRA_COLOR_HEX, 0xFF1976D2)
        val audioPath = intent.getStringExtra(AlarmScheduler.EXTRA_AUDIO_PATH)
        val isSnooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_SNOOZE, false)

        // Early handling for Test Alarms:
        // Test alarms must NEVER be rejected or crash due to DB checks or dose-taken checks.
        if (isTest) {
            val alarmActivityIntent = Intent(context, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(AlarmScheduler.EXTRA_MEDICATION_ID, medicationId)
                putExtra(AlarmScheduler.EXTRA_MEDICATION_NAME, rawName.ifBlank { "Örnek İlaç (Tansiyon)" })
                putExtra(AlarmScheduler.EXTRA_DOSAGE, dosage)
                putExtra(AlarmScheduler.EXTRA_MEAL_TIMING, mealTiming)
                putExtra(AlarmScheduler.EXTRA_SCHEDULED_TIME, scheduledTime.ifBlank { "12:00" })
                putExtra(AlarmScheduler.EXTRA_COLOR_HEX, colorHex)
                putExtra(AlarmScheduler.EXTRA_AUDIO_PATH, audioPath)
                putExtra(AlarmScheduler.EXTRA_IS_SNOOZE, false)
                putExtra(AlarmScheduler.EXTRA_IS_TEST, true)
            }
            try {
                context.startActivity(alarmActivityIntent)
            } catch (e: Exception) {
                // Handled
            }
            return
        }

        val prefs = PreferencesManager(context)
        val scheduler = AlarmScheduler(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 1. Immediately reject and silence any phantom, stray, or unnamed alarms
        if (medicationId == 999999L || medicationId <= 0L || rawName.isBlank() || rawName == "İlaç Hatırlatma") {
            scheduler.cancelPhantom999999()
            if (prefs.pendingDueMedicationId == 999999L || prefs.pendingDueMedicationName == "İlaç Hatırlatma" || prefs.pendingDueMedicationId <= 0L) {
                prefs.clearPendingDueAlarm()
            }
            if (medicationId > 0) {
                nm.cancel(medicationId.toInt())
                scheduler.cancelMedicationById(medicationId)
                scheduler.cancelSnooze(medicationId)
            }
            nm.cancel(999999)
            return
        }

        // 2. Strict Database Verification:
        // Every real alarm MUST correspond to an existing, active medication in the database.
        val db = com.example.data.AppDatabase.getDatabase(context)
        val med = try {
            db.medicationDao().getMedicationByIdSync(medicationId)
        } catch (t: Throwable) {
            null
        }

        if (med == null || !med.isActive) {
            scheduler.cancelMedicationById(medicationId)
            scheduler.cancelSnooze(medicationId)
            if (prefs.pendingDueMedicationId == medicationId) {
                prefs.clearPendingDueAlarm()
            }
            nm.cancel(medicationId.toInt())
            return
        }

        // 3. If dose is already taken today, do not alarm
        val todayStr = com.example.data.repository.MedicationRepository.getTodayDateString()
        val isAlreadyTaken = try {
            db.intakeLogDao().getSpecificLogSync(medicationId, scheduledTime, todayStr)?.isTaken == true
        } catch (t: Throwable) {
            false
        }

        if (isAlreadyTaken) {
            scheduler.cancelSnooze(medicationId)
            if (prefs.pendingDueMedicationId == medicationId) {
                prefs.clearPendingDueAlarm()
            }
            nm.cancel(medicationId.toInt())
            return
        }

        // 4. Reschedule for future recurrences if this was a normal (non-snooze) alarm
        if (!isSnooze) {
            scheduler.scheduleMedication(med)
        }

        val medicationName = med.name

        // 5. Store as pending due medication
        prefs.setPendingDueAlarm(
            id = med.id,
            name = med.name,
            time = scheduledTime,
            colorHex = med.colorHex,
            audioPath = med.customAudioPath
        )

        // Wake screen safely using PARTIAL_WAKE_LOCK to prevent Samsung Device Care crash
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "MedicationReminder:AlarmWakeLock"
            )
            wakeLock?.acquire(15000L) // 15 seconds to ensure screen turns on and activity starts
        } catch (t: Throwable) {
            // Handled
        }

        // Intent to launch full-screen AlarmActivity
        val alarmActivityIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(AlarmScheduler.EXTRA_MEDICATION_ID, med.id)
            putExtra(AlarmScheduler.EXTRA_MEDICATION_NAME, medicationName)
            putExtra(AlarmScheduler.EXTRA_DOSAGE, med.dosage.ifBlank { dosage })
            putExtra(AlarmScheduler.EXTRA_MEAL_TIMING, med.mealTiming.ifBlank { mealTiming })
            putExtra(AlarmScheduler.EXTRA_SCHEDULED_TIME, scheduledTime)
            putExtra(AlarmScheduler.EXTRA_COLOR_HEX, med.colorHex)
            putExtra(AlarmScheduler.EXTRA_AUDIO_PATH, med.customAudioPath)
            putExtra(AlarmScheduler.EXTRA_IS_SNOOZE, isSnooze)
            putExtra(AlarmScheduler.EXTRA_IS_TEST, false)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            med.id.toInt(),
            alarmActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        createNotificationChannel(context)

        val title = if (isSnooze) "⏰ TEKRAR HATIRLATMA: $medicationName" else "⏰ İLAÇ VAKTİ GELDİ!"
        val contentText = "$medicationName - $dosage ($mealTiming)"

        // Active high priority notification for full screen popup over lockscreen.
        // Sound is null so the system notification never loops audio in the background.
        // AlarmActivity cleanly handles the single-play melody and voice reminder.
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .setAutoCancel(true)
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setSound(null)
            .setVibrate(longArrayOf(0, 500, 250, 500))
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(medicationId.toInt(), notification)

        // Directly launch the activity as well to guarantee popup
        try {
            context.startActivity(alarmActivityIntent)
        } catch (e: Exception) {
            // Notification fullScreenIntent will handle lock screen
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Clean up any legacy notification channels that may have cached old sound settings
            val legacyChannels = listOf(
                "medication_alarm_channel",
                "medication_alarm_channel_v1",
                "medication_alarm_channel_v2",
                "medication_alarm_channel_v3",
                "medication_alarm_channel_v4",
                "medication_alarm_channel_v5",
                "medication_alarm_channel_v6",
                "medication_alarm_channel_v7"
            )
            for (chId in legacyChannels) {
                try {
                    notificationManager.deleteNotificationChannel(chId)
                } catch (t: Throwable) {}
            }

            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Tam ekran ilaç hatırlatıcı alarmları"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500)
                setBypassDnd(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                // Explicitly silent - sound is controlled exclusively and cleanly by AlarmActivity
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
