package com.example.data.repository

import android.app.NotificationManager
import android.content.Context
import com.example.alarm.AlarmScheduler
import com.example.data.PreferencesManager
import com.example.data.dao.IntakeLogDao
import com.example.data.dao.MedicationDao
import com.example.data.model.IntakeLog
import com.example.data.model.Medication
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MedicationRepository(
    private val context: Context,
    private val medicationDao: MedicationDao,
    private val intakeLogDao: IntakeLogDao,
    private val alarmScheduler: AlarmScheduler
) {
    private val prefs = PreferencesManager(context)
    val allMedications: Flow<List<Medication>> = medicationDao.getAllMedications()
    val activeMedications: Flow<List<Medication>> = medicationDao.getActiveMedications()
    val allLogs: Flow<List<IntakeLog>> = intakeLogDao.getAllLogs()

    fun getTodayLogsFlow(): Flow<List<IntakeLog>> {
        val todayStr = getTodayDateString()
        return intakeLogDao.getLogsForDate(todayStr)
    }

    suspend fun getMedicationById(id: Long): Medication? {
        return medicationDao.getMedicationById(id)
    }

    suspend fun insertOrUpdateMedication(medication: Medication): Long {
        val id = medicationDao.insertMedication(medication)
        val savedMed = if (medication.id == 0L) medication.copy(id = id) else medication

        // Update exact alarm schedule
        alarmScheduler.scheduleMedication(savedMed)
        return id
    }

    suspend fun deleteMedication(medication: Medication) {
        alarmScheduler.cancelMedication(medication)
        alarmScheduler.cancelMedicationById(medication.id, medication.parseTimesList())
        medicationDao.deleteMedication(medication)
        intakeLogDao.deleteLogsForMedication(medication.id)

        // Clear pending due alarm if it belonged to this deleted medication
        if (prefs.pendingDueMedicationId == medication.id) {
            prefs.clearPendingDueAlarm()
        }

        // Cancel any active system notification
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(medication.id.toInt())
    }

    suspend fun toggleMedicationActive(medication: Medication) {
        val updated = medication.copy(isActive = !medication.isActive)
        medicationDao.updateMedication(updated)
        if (updated.isActive) {
            alarmScheduler.scheduleMedication(updated)
        } else {
            alarmScheduler.cancelMedication(updated)
        }
    }

    suspend fun markDoseTaken(
        medicationId: Long,
        medicationName: String,
        scheduledTime: String
    ): Long {
        val todayStr = getTodayDateString()
        alarmScheduler.cancelSnooze(medicationId)

        val log = IntakeLog(
            medicationId = medicationId,
            medicationName = medicationName,
            scheduledTime = scheduledTime,
            dateString = todayStr,
            takenTimestamp = System.currentTimeMillis(),
            isTaken = true
        )
        return intakeLogDao.insertLog(log)
    }

    suspend fun unmarkDoseTaken(medicationId: Long, scheduledTime: String) {
        val todayStr = getTodayDateString()
        intakeLogDao.deleteLog(medicationId, scheduledTime, todayStr)
    }

    suspend fun isDoseTaken(medicationId: Long, scheduledTime: String, dateString: String): Boolean {
        val log = intakeLogDao.getSpecificLog(medicationId, scheduledTime, dateString)
        return log?.isTaken == true
    }

    fun triggerTestAlarm(medication: Medication) {
        alarmScheduler.triggerImmediateTest(
            medicationId = medication.id,
            medicationName = medication.name,
            dosage = medication.dosage,
            mealTiming = medication.mealTiming,
            audioPath = medication.customAudioPath
        )
    }

    companion object {
        fun getTodayDateString(): String {
            return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        }
    }
}
