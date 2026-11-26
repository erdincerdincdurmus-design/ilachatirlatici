package com.example.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            AlarmReceiver.createNotificationChannel(context)
            DailyAlarmRescheduleWorker.scheduleDaily(context)

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    AlarmScheduler(context).rescheduleAllActiveMedications(
                        "Telefon Açılışı / Güncelleme (${intent.action})"
                    )
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
