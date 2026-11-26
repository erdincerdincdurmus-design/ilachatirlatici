package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.model.AlarmEventLog

object AlarmEventLogger {

    const val TYPE_TRIGGERED = "TETİKLENDİ"
    const val TYPE_NEXT_SCHEDULED = "SONRAKİ_KURULDU"
    const val TYPE_NOTIFICATION_SHOWN = "BİLDİRİM_GÖSTERİLDİ"
    const val TYPE_RESCHEDULED = "YENİDEN_KURULDU"
    const val TYPE_SKIPPED = "ATLANDI"
    const val TYPE_CANCELLED = "İPTAL"
    const val TYPE_ERROR = "HATA"

    fun log(
        context: Context,
        eventType: String,
        medicationId: Long = -1L,
        medicationName: String = "",
        scheduledTime: String = "",
        details: String
    ) {
        try {
            val dao = AppDatabase.getDatabase(context).alarmEventLogDao()
            dao.insertSync(
                AlarmEventLog(
                    timestamp = System.currentTimeMillis(),
                    eventType = eventType,
                    medicationId = medicationId,
                    medicationName = medicationName,
                    scheduledTime = scheduledTime,
                    details = details
                )
            )
            dao.trimOldLogsSync(200)
        } catch (t: Throwable) {
            Log.e("AlarmEventLogger", "Failed to write alarm event log [$eventType]: ${t.message}")
        }
    }
}
