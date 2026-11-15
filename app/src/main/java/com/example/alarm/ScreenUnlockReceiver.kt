package com.example.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.AppDatabase
import com.example.data.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ensures that if a medication alarm was due and not yet taken,
 * when the user turns on/unlocks their phone, the full-screen reminder
 * immediately pops up in front of them.
 */
class ScreenUnlockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_USER_PRESENT ||
            intent.action == Intent.ACTION_BOOT_COMPLETED
        ) {
            val prefs = PreferencesManager(context)
            val pendingId = prefs.pendingDueMedicationId
            val pendingName = prefs.pendingDueMedicationName

            if (pendingId <= 0L || pendingId == 999999L || pendingName == "İlaç Hatırlatma" || pendingName.isBlank()) {
                prefs.clearPendingDueAlarm()
                return
            }

            if (prefs.isPendingDueAlarm && pendingId > 0) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val db = AppDatabase.getDatabase(context)
                        val med = db.medicationDao().getMedicationByIdSync(pendingId)
                        if (med == null || !med.isActive) {
                            prefs.clearPendingDueAlarm()
                            return@launch
                        }

                        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                        val logs = db.intakeLogDao().getLogsForDateSync(todayStr)
                        val alreadyTaken = logs.any {
                            it.medicationId == prefs.pendingDueMedicationId &&
                            (prefs.pendingDueScheduledTime.isEmpty() || it.scheduledTime == prefs.pendingDueScheduledTime)
                        }

                        if (!alreadyTaken) {
                            val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                                putExtra(AlarmScheduler.EXTRA_MEDICATION_ID, prefs.pendingDueMedicationId)
                                putExtra(AlarmScheduler.EXTRA_MEDICATION_NAME, prefs.pendingDueMedicationName)
                                putExtra(AlarmScheduler.EXTRA_SCHEDULED_TIME, prefs.pendingDueScheduledTime)
                                putExtra(AlarmScheduler.EXTRA_COLOR_HEX, prefs.pendingDueColorHex)
                                putExtra(AlarmScheduler.EXTRA_AUDIO_PATH, prefs.pendingDueAudioPath)
                                putExtra(AlarmScheduler.EXTRA_IS_SNOOZE, true)
                            }
                            context.startActivity(alarmIntent)
                        } else {
                            prefs.clearPendingDueAlarm()
                        }
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }
        }
    }
}
