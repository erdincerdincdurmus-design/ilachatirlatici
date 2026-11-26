package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.alarm.AlarmPermissionHelper
import com.example.alarm.AlarmPermissionStatus
import com.example.alarm.AlarmScheduler
import com.example.alarm.DailyAlarmRescheduleWorker
import com.example.audio.VoiceManager
import com.example.data.AppDatabase
import com.example.data.PreferencesManager
import com.example.data.model.AlarmEventLog
import com.example.data.model.IntakeLog
import com.example.data.model.Medication
import com.example.data.repository.MedicationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class TodayDoseItem(
    val medicationId: Long,
    val medicationName: String,
    val dosage: String,
    val mealTiming: String,
    val colorHex: Long,
    val iconType: String,
    val customAudioPath: String?,
    val scheduledTime: String,
    val isTaken: Boolean,
    val takenTimestamp: Long? = null,
    val isDueOrOverdue: Boolean = false
)

enum class ScreenTab {
    TODAY,
    MEDICATIONS,
    HISTORY,
    SETTINGS
}

class MedicationViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val scheduler = AlarmScheduler(application)
    val repository = MedicationRepository(
        application,
        db.medicationDao(),
        db.intakeLogDao(),
        scheduler,
        db.alarmEventLogDao()
    )
    val prefs = PreferencesManager(application)
    val voiceManager = VoiceManager(application)

    val currentTab = MutableStateFlow(ScreenTab.TODAY)
    val historySubTab = MutableStateFlow(0)
    val editingMedication = MutableStateFlow<Medication?>(null)
    val isAddingNewMedication = MutableStateFlow(false)

    val alarmPermissionStatus = MutableStateFlow(AlarmPermissionHelper.checkStatus(application))
    val hasDismissedPermissionDialog = MutableStateFlow(false)

    fun resetToTodayScreen() {
        currentTab.value = ScreenTab.TODAY
        editingMedication.value = null
        isAddingNewMedication.value = false
    }

    fun refreshAlarmPermissionStatus(context: Context = getApplication()) {
        alarmPermissionStatus.value = AlarmPermissionHelper.checkStatus(context)
    }

    fun rescheduleAllActiveMedications(source: String = "Uygulama Açılışı") {
        viewModelScope.launch(Dispatchers.IO) {
            DailyAlarmRescheduleWorker.scheduleDaily(getApplication())
            scheduler.rescheduleAllActiveMedications(source)
        }
    }

    fun clearAlarmLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAlarmLogs()
        }
    }

    val allMedications: StateFlow<List<Medication>> = repository.allMedications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayLogs: StateFlow<List<IntakeLog>> = repository.getTodayLogsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allLogs: StateFlow<List<IntakeLog>> = repository.allLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAlarmLogs: StateFlow<List<AlarmEventLog>> = repository.allAlarmLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Combined stream for Today's Doses
    val todayDoses: StateFlow<List<TodayDoseItem>> = combine(
        repository.activeMedications,
        todayLogs
    ) { meds, logs ->
        val scoredResult = mutableListOf<Pair<Int, TodayDoseItem>>()
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        val currentMinutesOfDay = currentHour * 60 + currentMinute

        for (med in meds) {
            // Check if this medication applies to today
            if (!med.appliesToToday(calendar)) {
                continue
            }

            val medTimes = med.parseTimesList()
            val firstTimeParts = medTimes.firstOrNull()?.split(":")
            val firstMinutes = (firstTimeParts?.getOrNull(0)?.toIntOrNull() ?: 8) * 60 +
                (firstTimeParts?.getOrNull(1)?.toIntOrNull() ?: 0)

            for (timeStr in medTimes) {
                val log = logs.find { it.medicationId == med.id && it.scheduledTime == timeStr }
                val isTaken = log != null

                val timeParts = timeStr.split(":")
                val scheduledHour = timeParts.getOrNull(0)?.toIntOrNull() ?: 8
                val scheduledMinute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0
                val rawMinutesOfDay = scheduledHour * 60 + scheduledMinute

                // Times that wrap past midnight (e.g. 00:00, 01:00, 02:00 after an 08:00 start) belong at the end of the cycle
                val logicalMinutes = if (rawMinutesOfDay < firstMinutes) rawMinutesOfDay + 1440 else rawMinutesOfDay
                val isDueOrOverdue = !isTaken && (currentMinutesOfDay >= logicalMinutes)

                scoredResult.add(
                    logicalMinutes to TodayDoseItem(
                        medicationId = med.id,
                        medicationName = med.name,
                        dosage = med.dosage,
                        mealTiming = med.mealTiming,
                        colorHex = med.colorHex,
                        iconType = med.iconType,
                        customAudioPath = med.customAudioPath,
                        scheduledTime = timeStr,
                        isTaken = isTaken,
                        takenTimestamp = log?.takenTimestamp,
                        isDueOrOverdue = isDueOrOverdue
                    )
                )
            }
        }

        // Sort: Due first, then Upcoming in cycle order, then Taken at the bottom
        scoredResult.sortedWith(
            compareBy(
                { it.second.isTaken },
                { !it.second.isDueOrOverdue },
                { it.first }
            )
        ).map { it.second }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Audio recording state
    val isRecording = MutableStateFlow(false)
    val lastRecordedFilePath = MutableStateFlow<String?>(null)

    init {
        // Purge any legacy default sample medications (Tansiyon, Şeker, Kemik Vitamini)
        // and phantom/test alarms to ensure user starts with a completely clean slate.
        viewModelScope.launch {
            cleanLegacyDefaultMedications()
            purgePhantomAlarms()
        }
    }

    private suspend fun cleanLegacyDefaultMedications() {
        val legacyDefaultNames = setOf("Tansiyon İlacı", "Şeker İlacı", "Kemik Vitamini (D3)")
        val allMeds = db.medicationDao().getAllMedicationsSync()
        for (med in allMeds) {
            if (med.name in legacyDefaultNames) {
                repository.deleteMedication(med)
            }
        }
        if (prefs.pendingDueMedicationName in legacyDefaultNames) {
            prefs.clearPendingDueAlarm()
        }
    }

    private fun purgePhantomAlarms() {
        scheduler.cancelPhantom999999()
        val pendingId = prefs.pendingDueMedicationId
        val pendingName = prefs.pendingDueMedicationName
        if (pendingId == 999999L || pendingId <= 0L || pendingName == "İlaç Hatırlatma" || pendingName.isBlank()) {
            prefs.clearPendingDueAlarm()
        }
    }

    fun markDoseTaken(dose: TodayDoseItem) {
        viewModelScope.launch {
            repository.markDoseTaken(
                medicationId = dose.medicationId,
                medicationName = dose.medicationName,
                scheduledTime = dose.scheduledTime
            )
        }
    }

    fun markAllDueDosesTaken(scheduledTime: String) {
        val currentDoses = todayDoses.value.filter { it.scheduledTime == scheduledTime && !it.isTaken }
        viewModelScope.launch {
            for (dose in currentDoses) {
                repository.markDoseTaken(
                    medicationId = dose.medicationId,
                    medicationName = dose.medicationName,
                    scheduledTime = dose.scheduledTime
                )
            }
        }
    }

    fun unmarkDoseTaken(dose: TodayDoseItem) {
        viewModelScope.launch {
            repository.unmarkDoseTaken(dose.medicationId, dose.scheduledTime)
        }
    }

    fun saveMedication(medication: Medication, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.insertOrUpdateMedication(medication)
            onDone()
        }
    }

    fun deleteMedication(medication: Medication) {
        viewModelScope.launch {
            repository.deleteMedication(medication)
        }
    }

    fun toggleMedicationActive(medication: Medication) {
        viewModelScope.launch {
            repository.toggleMedicationActive(medication)
        }
    }

    private var lastTestTriggerTime = 0L

    fun triggerTestAlarm(medication: Medication? = null, context: Context? = null) {
        val now = System.currentTimeMillis()
        if (now - lastTestTriggerTime < 1500L) return
        lastTestTriggerTime = now

        val targetMed = medication ?: allMedications.value.firstOrNull { it.isActive }
        val medId = targetMed?.id ?: 888888L
        val medName = targetMed?.name ?: "Örnek İlaç (Test)"
        val medDosage = targetMed?.dosage ?: "1 Tablet"
        val medMeal = targetMed?.mealTiming ?: "Tok Karna"
        val medAudio = targetMed?.customAudioPath ?: prefs.defaultCustomVoicePath

        val targetContext = context ?: getApplication<Application>()
        scheduler.triggerImmediateTest(
            medicationId = medId,
            medicationName = medName,
            dosage = medDosage,
            mealTiming = medMeal,
            audioPath = medAudio
        )
        android.widget.Toast.makeText(
            targetContext,
            "Test alarmı 2 saniye içinde bildirim olarak tetiklenecek.",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    fun checkAndShowPendingAlarm(context: Context) {
        if (!prefs.isPendingDueAlarm) return

        val pendingId = prefs.pendingDueMedicationId
        val pendingName = prefs.pendingDueMedicationName

        if (pendingId <= 0L || pendingId == 999999L || pendingName == "İlaç Hatırlatma" || pendingName.isBlank()) {
            prefs.clearPendingDueAlarm()
            scheduler.cancelPhantom999999()
            return
        }

        viewModelScope.launch {
            val med = repository.getMedicationById(pendingId)
            if (med == null || !med.isActive) {
                prefs.clearPendingDueAlarm()
                scheduler.cancelMedicationById(pendingId)
                scheduler.cancelSnooze(pendingId)
                return@launch
            }

            val todayStr = MedicationRepository.getTodayDateString()
            val isTaken = repository.isDoseTaken(
                pendingId,
                prefs.pendingDueScheduledTime,
                todayStr
            )
            if (!isTaken) {
                val intent = Intent(context, com.example.alarm.AlarmActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(AlarmScheduler.EXTRA_MEDICATION_ID, med.id)
                    putExtra(AlarmScheduler.EXTRA_MEDICATION_NAME, med.name)
                    putExtra(AlarmScheduler.EXTRA_DOSAGE, med.dosage)
                    putExtra(AlarmScheduler.EXTRA_MEAL_TIMING, med.mealTiming)
                    putExtra(AlarmScheduler.EXTRA_SCHEDULED_TIME, prefs.pendingDueScheduledTime)
                    putExtra(AlarmScheduler.EXTRA_COLOR_HEX, med.colorHex)
                    putExtra(AlarmScheduler.EXTRA_AUDIO_PATH, med.customAudioPath)
                    putExtra(AlarmScheduler.EXTRA_IS_SNOOZE, true)
                }
                context.startActivity(intent)
            } else {
                prefs.clearPendingDueAlarm()
            }
        }
    }

    fun startVoiceRecording() {
        val file = voiceManager.startRecording("custom_voice")
        if (file != null) {
            isRecording.value = true
        }
    }

    fun stopVoiceRecording(): String? {
        val file = voiceManager.stopRecording()
        isRecording.value = false
        lastRecordedFilePath.value = file?.absolutePath
        return file?.absolutePath
    }

    fun playRecordedVoice(path: String) {
        voiceManager.playCustomAudioOrSpeak(path, "Test ilacı", "1 Tablet", "Tok Karna")
    }

    fun testTtsSpeech(text: String = "İlaç zamanı! İlaç vakti geldi. Lütfen ilacınızı alınız.") {
        voiceManager.speakPhrase(text)
    }

    fun updateSnoozeMinutes(minutes: Int) {
        prefs.snoozeMinutes = minutes
    }

    fun updateVoiceEnabled(enabled: Boolean) {
        prefs.isVoiceEnabled = enabled
    }

    fun updateVibrationEnabled(enabled: Boolean) {
        prefs.isVibrationEnabled = enabled
    }

    fun updateRepeatUntilTaken(repeat: Boolean) {
        prefs.repeatUntilTaken = repeat
    }

    fun updatePlayAlarmOnce(playOnce: Boolean) {
        prefs.playAlarmOnce = playOnce
    }

    fun importAudioFileFromUri(uri: android.net.Uri): String? {
        val path = voiceManager.copyAudioUriToStorage(uri)
        if (path != null) {
            lastRecordedFilePath.value = path
        }
        return path
    }

    fun setDefaultVoicePath(path: String?) {
        prefs.defaultCustomVoicePath = path
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.release()
    }
}
