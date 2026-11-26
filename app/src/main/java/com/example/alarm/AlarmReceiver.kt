package com.example.alarm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.data.AlarmEventLogger
import com.example.data.AppDatabase
import com.example.data.PreferencesManager
import com.example.data.model.Medication
import com.example.data.repository.MedicationRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "medication_alarm_channel_v11"
        const val CHANNEL_NAME = "İlaç Hatırlatıcı Alarmları"

        fun getAlarmSoundUri(): Uri {
            return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        }

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                val legacyChannels = listOf(
                    "medication_alarm_channel",
                    "medication_alarm_channel_v1",
                    "medication_alarm_channel_v2",
                    "medication_alarm_channel_v3",
                    "medication_alarm_channel_v4",
                    "medication_alarm_channel_v5",
                    "medication_alarm_channel_v6",
                    "medication_alarm_channel_v7",
                    "medication_silent_alarm_channel_v10"
                )
                for (chId in legacyChannels) {
                    try {
                        notificationManager.deleteNotificationChannel(chId)
                    } catch (_: Throwable) {
                    }
                }

                val alarmAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Yüksek öncelikli tam ekran ilaç hatırlatıcı alarmları"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 250, 500)
                    setBypassDnd(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setSound(getAlarmSoundUri(), alarmAttributes)
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
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

        val fallbackName = rawName.ifBlank {
            if (isTest) "Örnek İlaç (Test)" else "İlaç (#$medicationId)"
        }

        val scheduler = AlarmScheduler(context)

        // 1. AT THE VERY START of onReceive (BEFORE ANY database read!):
        // Schedule the next occurrence directly from Intent extras for regular alarms.
        val nextScheduleOutcome = if (!isTest && !isSnooze && medicationId > 0L && medicationId != 999999L) {
            scheduler.scheduleNextOccurrenceFromIntent(intent)
        } else {
            null
        }

        // Now record trigger + next-alarm scheduling results in persistent log
        AlarmEventLogger.log(
            context = context,
            eventType = AlarmEventLogger.TYPE_TRIGGERED,
            medicationId = medicationId,
            medicationName = fallbackName,
            scheduledTime = scheduledTime,
            details = when {
                isTest -> "Test alarmı tetiklendi."
                isSnooze -> "Erteleme alarmı tetiklendi (Saat: $scheduledTime)."
                else -> "Zamanlanmış alarm tetiklendi (Saat: $scheduledTime)."
            }
        )

        if (nextScheduleOutcome != null) {
            if (nextScheduleOutcome.success) {
                val formattedNext = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                    .format(Date(nextScheduleOutcome.triggerAtMillis))
                AlarmEventLogger.log(
                    context = context,
                    eventType = AlarmEventLogger.TYPE_NEXT_SCHEDULED,
                    medicationId = medicationId,
                    medicationName = fallbackName,
                    scheduledTime = scheduledTime,
                    details = "Bir sonraki alarm veritabanı okunmadan önce $formattedNext için kuruldu (${nextScheduleOutcome.methodUsed})."
                )
            } else {
                AlarmEventLogger.log(
                    context = context,
                    eventType = AlarmEventLogger.TYPE_ERROR,
                    medicationId = medicationId,
                    medicationName = fallbackName,
                    scheduledTime = scheduledTime,
                    details = "Bir sonraki alarm kurulamadı: ${nextScheduleOutcome.error ?: "Bilinmeyen hata"}"
                )
            }
        }

        val prefs = PreferencesManager(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Test alarm path: high-priority fullScreenIntent notification (NO direct startActivity)
        if (isTest) {
            showFullScreenAlarmNotification(
                context = context,
                notificationManager = nm,
                medicationId = if (medicationId > 0) medicationId else 888888L,
                medicationName = fallbackName,
                dosage = dosage,
                mealTiming = mealTiming,
                scheduledTime = scheduledTime.ifBlank { "12:00" },
                colorHex = colorHex,
                audioPath = audioPath,
                isSnooze = false,
                isTest = true
            )
            return
        }

        // Reject phantom / invalid negative IDs only
        if (medicationId == 999999L || medicationId <= 0L) {
            scheduler.cancelPhantom999999()
            if (prefs.pendingDueMedicationId == 999999L || prefs.pendingDueMedicationId <= 0L) {
                prefs.clearPendingDueAlarm()
            }
            nm.cancel(999999)
            AlarmEventLogger.log(
                context = context,
                eventType = AlarmEventLogger.TYPE_CANCELLED,
                medicationId = medicationId,
                medicationName = fallbackName,
                scheduledTime = scheduledTime,
                details = "Geçersiz/eski alarm kimliği engellendi (ID=$medicationId)."
            )
            return
        }

        // 2. Database verification:
        // CRITICAL: On DB read error OR if record is not found (med == null),
        // DO NOT cancel the alarm and STILL show the notification using Intent extras.
        var dbErrorMessage: String? = null
        val med: Medication? = try {
            AppDatabase.getDatabase(context).medicationDao().getMedicationByIdSync(medicationId)
        } catch (t: Throwable) {
            dbErrorMessage = t.localizedMessage ?: t::class.java.simpleName
            null
        }

        if (dbErrorMessage != null) {
            AlarmEventLogger.log(
                context = context,
                eventType = AlarmEventLogger.TYPE_ERROR,
                medicationId = medicationId,
                medicationName = fallbackName,
                scheduledTime = scheduledTime,
                details = "Veritabanı okuma hatası ($dbErrorMessage). Alarm iptal edilmedi, bildirim yine de gösteriliyor."
            )
        } else if (med == null) {
            AlarmEventLogger.log(
                context = context,
                eventType = AlarmEventLogger.TYPE_ERROR,
                medicationId = medicationId,
                medicationName = fallbackName,
                scheduledTime = scheduledTime,
                details = "Veritabanında kayıt bulunamadı (ID=$medicationId). Alarm iptal edilmedi, bildirim yine de gösteriliyor."
            )
        } else if (!med.isActive) {
            AlarmEventLogger.log(
                context = context,
                eventType = AlarmEventLogger.TYPE_SKIPPED,
                medicationId = medicationId,
                medicationName = med.name,
                scheduledTime = scheduledTime,
                details = "İptal/Atlama nedeni: İlaç pasif durumda (${med.name})."
            )
            return
        }

        // 3. Check if already taken today (if DB read fails, treat as not taken and STILL notify)
        if (med != null) {
            val todayStr = MedicationRepository.getTodayDateString()
            var intakeReadError: String? = null
            val isAlreadyTaken = try {
                AppDatabase.getDatabase(context)
                    .intakeLogDao()
                    .getSpecificLogSync(medicationId, scheduledTime, todayStr)?.isTaken == true
            } catch (t: Throwable) {
                intakeReadError = t.localizedMessage ?: t::class.java.simpleName
                false
            }

            if (intakeReadError != null) {
                AlarmEventLogger.log(
                    context = context,
                    eventType = AlarmEventLogger.TYPE_ERROR,
                    medicationId = medicationId,
                    medicationName = med.name,
                    scheduledTime = scheduledTime,
                    details = "Alım geçmişi okunamadı ($intakeReadError). Güvenlik için bildirim yine de gösteriliyor."
                )
            } else if (isAlreadyTaken) {
                scheduler.cancelSnooze(medicationId)
                if (prefs.pendingDueMedicationId == medicationId) {
                    prefs.clearPendingDueAlarm()
                }
                nm.cancel(medicationId.toInt())
                AlarmEventLogger.log(
                    context = context,
                    eventType = AlarmEventLogger.TYPE_SKIPPED,
                    medicationId = medicationId,
                    medicationName = med.name,
                    scheduledTime = scheduledTime,
                    details = "Atlandı: Bugünkü doz ($scheduledTime) zaten alınmış."
                )
                return
            }
        }

        val resolvedName = med?.name?.ifBlank { fallbackName } ?: fallbackName
        val resolvedDosage = med?.dosage?.ifBlank { dosage } ?: dosage
        val resolvedMeal = med?.mealTiming?.ifBlank { mealTiming } ?: mealTiming
        val resolvedColor = med?.colorHex ?: colorHex
        val resolvedAudio = med?.customAudioPath ?: audioPath

        prefs.setPendingDueAlarm(
            id = medicationId,
            name = resolvedName,
            time = scheduledTime,
            colorHex = resolvedColor,
            audioPath = resolvedAudio
        )

        showFullScreenAlarmNotification(
            context = context,
            notificationManager = nm,
            medicationId = medicationId,
            medicationName = resolvedName,
            dosage = resolvedDosage,
            mealTiming = resolvedMeal,
            scheduledTime = scheduledTime,
            colorHex = resolvedColor,
            audioPath = resolvedAudio,
            isSnooze = isSnooze,
            isTest = false
        )
    }

    private fun showFullScreenAlarmNotification(
        context: Context,
        notificationManager: NotificationManager,
        medicationId: Long,
        medicationName: String,
        dosage: String,
        mealTiming: String,
        scheduledTime: String,
        colorHex: Long,
        audioPath: String?,
        isSnooze: Boolean,
        isTest: Boolean
    ) {
        var wakeLock: PowerManager.WakeLock? = null
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "MedicationReminder:AlarmWakeLock"
            )
            wakeLock?.acquire(10_000L)
        } catch (_: Throwable) {
        }

        try {
            createNotificationChannel(context)

            // Verify notification permissions before posting
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                AlarmEventLogger.log(
                    context = context,
                    eventType = AlarmEventLogger.TYPE_ERROR,
                    medicationId = medicationId,
                    medicationName = medicationName,
                    scheduledTime = scheduledTime,
                    details = "Bildirim gösterilemedi: POST_NOTIFICATIONS izni verilmemiş."
                )
                return
            }

            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                AlarmEventLogger.log(
                    context = context,
                    eventType = AlarmEventLogger.TYPE_ERROR,
                    medicationId = medicationId,
                    medicationName = medicationName,
                    scheduledTime = scheduledTime,
                    details = "Bildirim gösterilemedi: Sistem bildirimleri kapalı."
                )
                return
            }

            val alarmActivityIntent = Intent(context, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(AlarmScheduler.EXTRA_MEDICATION_ID, medicationId)
                putExtra(AlarmScheduler.EXTRA_MEDICATION_NAME, medicationName)
                putExtra(AlarmScheduler.EXTRA_DOSAGE, dosage)
                putExtra(AlarmScheduler.EXTRA_MEAL_TIMING, mealTiming)
                putExtra(AlarmScheduler.EXTRA_SCHEDULED_TIME, scheduledTime)
                putExtra(AlarmScheduler.EXTRA_COLOR_HEX, colorHex)
                putExtra(AlarmScheduler.EXTRA_AUDIO_PATH, audioPath)
                putExtra(AlarmScheduler.EXTRA_IS_SNOOZE, isSnooze)
                putExtra(AlarmScheduler.EXTRA_IS_TEST, isTest)
            }

            val notificationId = medicationId.toInt()
            val fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                alarmActivityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = when {
                isTest -> "⏰ TEST ALARMI: $medicationName"
                isSnooze -> "⏰ TEKRAR HATIRLATMA: $medicationName"
                else -> "⏰ İLAÇ VAKTİ GELDİ!"
            }
            val contentText = "$medicationName - $dosage ($mealTiming)"
            val alarmSoundUri = getAlarmSoundUri()

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
                .setSound(alarmSoundUri, AudioManager.STREAM_ALARM)
                .setVibrate(longArrayOf(0, 500, 250, 500))
                .build()

            notificationManager.notify(notificationId, notification)

            val fullScreenAllowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                try {
                    notificationManager.canUseFullScreenIntent()
                } catch (_: Throwable) {
                    true
                }
            } else {
                true
            }

            AlarmEventLogger.log(
                context = context,
                eventType = AlarmEventLogger.TYPE_NOTIFICATION_SHOWN,
                medicationId = medicationId,
                medicationName = medicationName,
                scheduledTime = scheduledTime,
                details = "Yüksek öncelikli fullScreenIntent bildirimi gösterildi (Alarm ses kanalı; Tam ekran izni=${if (fullScreenAllowed) "Açık" else "Kapalı"})."
            )
        } catch (t: Throwable) {
            AlarmEventLogger.log(
                context = context,
                eventType = AlarmEventLogger.TYPE_ERROR,
                medicationId = medicationId,
                medicationName = medicationName,
                scheduledTime = scheduledTime,
                details = "Bildirim gösterme hatası: ${t.localizedMessage ?: t::class.java.simpleName}"
            )
        } finally {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            } catch (_: Throwable) {
            }
        }
    }
}
