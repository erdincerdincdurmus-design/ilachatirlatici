package com.example

import android.app.Application
import com.example.alarm.AlarmReceiver
import com.example.alarm.AlarmScheduler
import com.example.alarm.DailyAlarmRescheduleWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MedicationApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        AlarmReceiver.createNotificationChannel(this)
        DailyAlarmRescheduleWorker.scheduleDaily(this)

        appScope.launch {
            AlarmScheduler(this@MedicationApp)
                .rescheduleAllActiveMedications("Uygulama Başlatma (Application)")
        }
    }
}
