package com.example.alarm

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MainActivity
import com.example.audio.VoiceManager
import com.example.data.AppDatabase
import com.example.data.PreferencesManager
import com.example.data.model.IntakeLog
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AlarmActivity : ComponentActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var voiceManager: VoiceManager? = null
    private var hasConfirmedTaken = false
    private lateinit var alarmScheduler: AlarmScheduler
    private lateinit var prefs: PreferencesManager

    private val handler = Handler(Looper.getMainLooper())
    private var stopSoundRunnable: Runnable? = null
    private var autoSnoozeRunnable: Runnable? = null

    // Alarm metadata
    private var medicationId: Long = -1L
    private var primaryMedicationName: String = "İlaç"
    private var scheduledTime: String = ""
    private var colorHex: Long = 0xFF1976D2
    private var audioPath: String? = null
    private var isSnooze: Boolean = false
    private var isTest: Boolean = false

    // List of all medication names scheduled at this same time slot
    private val activeMedicationNames = mutableStateListOf<String>()

    private var hasStartedAlert = false
    private var hasPlayedVoice = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initFromIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newMedId = intent.getLongExtra(AlarmScheduler.EXTRA_MEDICATION_ID, -1L)
        if (newMedId != medicationId) {
            hasStartedAlert = false
            hasPlayedVoice = false
            stopSoundAndVibration()
        }
        initFromIntent(intent)
    }

    private fun initFromIntent(intent: Intent) {
        // Ensure activity wakes up the device, turns screen on, and displays over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguardManager?.requestDismissKeyguard(this, null)
        }

        alarmScheduler = AlarmScheduler(this)
        prefs = PreferencesManager(this)
        voiceManager = VoiceManager(this)

        medicationId = intent.getLongExtra(AlarmScheduler.EXTRA_MEDICATION_ID, -1L)
        primaryMedicationName = intent.getStringExtra(AlarmScheduler.EXTRA_MEDICATION_NAME) ?: "İlaç"
        scheduledTime = intent.getStringExtra(AlarmScheduler.EXTRA_SCHEDULED_TIME) ?: ""
        colorHex = intent.getLongExtra(AlarmScheduler.EXTRA_COLOR_HEX, 0xFF1976D2)
        audioPath = intent.getStringExtra(AlarmScheduler.EXTRA_AUDIO_PATH)
        isSnooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_SNOOZE, false)
        isTest = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_TEST, false)

        // Dismiss immediately if it's a phantom or invalid alarm
        if (!isTest && (medicationId <= 0L || medicationId == 999999L || primaryMedicationName == "İlaç Hatırlatma")) {
            prefs.clearPendingDueAlarm()
            alarmScheduler.cancelPhantom999999()
            finish()
            return
        }

        // Cancel notification immediately so system notification service does not loop audio
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (medicationId > 0) {
            nm?.cancel(medicationId.toInt())
        }
        nm?.cancel(999999)

        activeMedicationNames.clear()
        activeMedicationNames.add(primaryMedicationName)

        // For real alarms, query database to check if there are other medications scheduled at the exact same hour
        if (!isTest) {
            loadAllMedicationsForThisTime()
        }

        if (!hasStartedAlert) {
            hasStartedAlert = true
            startSoundAndVibration()
        }

        setContent {
            MyApplicationTheme {
                AlarmFixedScreenContent(
                    medicationNames = activeMedicationNames,
                    scheduledTime = scheduledTime,
                    isSnooze = isSnooze,
                    isTest = isTest,
                    snoozeMinutes = prefs.snoozeMinutes,
                    onTakeMedicine = {
                        confirmMedicationTaken()
                    },
                    onSnooze = {
                        snoozeAlarm()
                    }
                )
            }
        }
    }

    private fun loadAllMedicationsForThisTime() {
        Thread {
            try {
                val db = AppDatabase.getDatabase(this)
                val activeMeds = db.medicationDao().getActiveMedicationsList()
                val calendar = Calendar.getInstance()
                val matchingMeds = activeMeds.filter {
                    it.appliesToToday(calendar) && it.parseTimesList().contains(scheduledTime)
                }
                val names = matchingMeds.map { it.name }.distinct()
                if (names.isNotEmpty()) {
                    runOnUiThread {
                        activeMedicationNames.clear()
                        activeMedicationNames.addAll(names)
                    }
                }
            } catch (e: Exception) {
                // Keep initial name
            }
        }.start()
    }

    private fun startSoundAndVibration() {
        try {
            // Cancel system notification in background immediately
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            try {
                nm?.cancelAll()
            } catch (t: Throwable) {}

            // Volume check: ensure alarm stream is loud and clearly audible for the elderly user
            try {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                if (audioManager != null) {
                    val maxAlarmVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                    val currentAlarmVol = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                    if (currentAlarmVol < (maxAlarmVol * 0.70f).toInt()) {
                        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, (maxAlarmVol * 0.85f).toInt(), 0)
                    }
                }
            } catch (t: Throwable) {
                // Safeguard against SecurityException or Samsung DND state restrictions
            }

            // 1. Vibration: Vibrate once only (repeatIndex = -1: NEVER loops!)
            try {
                vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vibratorManager?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }

                if (prefs.isVibrationEnabled) {
                    val pattern = longArrayOf(0, 400, 200, 400)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(pattern, -1)
                    }
                }
            } catch (t: Throwable) {
                // ignore
            }

            // 2. Play Alarm Melody ONCE using our high-quality, gentle chime (isLooping = false)
            var alarmMelodyFinished = false
            fun onAlarmMelodyDone() {
                if (alarmMelodyFinished) return
                alarmMelodyFinished = true
                stopAlarmSound()
                try { vibrator?.cancel() } catch (t: Throwable) {}

                // Play voice recording / TTS speech ONCE right after the alarm chime finishes
                playVoiceReminderOnce()
            }

            try {
                mediaPlayer?.release()
                mediaPlayer = null
                mediaPlayer = MediaPlayer.create(this@AlarmActivity, com.example.R.raw.alarm_chime)?.apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setVolume(1.0f, 1.0f)
                    isLooping = false
                    setOnCompletionListener {
                        onAlarmMelodyDone()
                    }
                    setOnErrorListener { _, _, _ ->
                        onAlarmMelodyDone()
                        true
                    }
                    start()
                }
            } catch (e: Throwable) {
                onAlarmMelodyDone()
            }

            // Safety timer: alarm chime finishes after 2.6 seconds, then transitions to voice
            stopSoundRunnable = Runnable {
                onAlarmMelodyDone()
            }
            handler.postDelayed(stopSoundRunnable!!, 2600L)

            // Auto-snooze if un-intervened after 3 minutes so device doesn't stay on forever
            autoSnoozeRunnable = Runnable {
                if (!hasConfirmedTaken && !isFinishing) {
                    snoozeAlarm()
                }
            }
            handler.postDelayed(autoSnoozeRunnable!!, 180000L)

        } catch (e: Throwable) {
            // Fallback handled
        }
    }

    private fun playVoiceReminderOnce() {
        if (hasPlayedVoice || isFinishing || hasConfirmedTaken) return
        hasPlayedVoice = true
        try {
            if (prefs.isVoiceEnabled) {
                val spokenName = if (activeMedicationNames.size > 1) {
                    activeMedicationNames.joinToString(" ve ")
                } else {
                    primaryMedicationName
                }
                voiceManager?.playCustomAudioOrSpeak(
                    audioPath = audioPath,
                    fallbackMedicationName = spokenName,
                    dosage = "",
                    mealTiming = "",
                    onCompleted = {
                        // Complete silence after single play
                    }
                )
            }
        } catch (t: Throwable) {
            // ignore
        }
    }

    private fun stopAlarmSound() {
        try {
            stopSoundRunnable?.let { handler.removeCallbacks(it) }
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            // ignore
        }
        try {
            ringtone?.stop()
            ringtone = null
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun stopSoundAndVibration() {
        stopAlarmSound()
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            // ignore
        }
        try {
            voiceManager?.stopPlayback()
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun confirmMedicationTaken() {
        hasConfirmedTaken = true
        stopSoundAndVibration()
        prefs.clearPendingDueAlarm()

        if (isTest) {
            android.widget.Toast.makeText(this, "Test alarmı başarıyla kapatıldı.", android.widget.Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        alarmScheduler.cancelSnooze(medicationId)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(medicationId.toInt())

        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val logTime = if (scheduledTime.isNotEmpty()) scheduledTime else SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        if (medicationId > 0 && medicationId != 999999L) {
            Thread {
                try {
                    val db = AppDatabase.getDatabase(this)
                    val activeMeds = db.medicationDao().getActiveMedicationsList()
                    val calendar = Calendar.getInstance()
                    val matchingMeds = activeMeds.filter {
                        it.appliesToToday(calendar) && it.parseTimesList().contains(scheduledTime)
                    }

                    if (matchingMeds.isNotEmpty()) {
                        for (med in matchingMeds) {
                            alarmScheduler.cancelSnooze(med.id)
                            db.intakeLogDao().insertLogSync(
                                IntakeLog(
                                    medicationId = med.id,
                                    medicationName = med.name,
                                    scheduledTime = logTime,
                                    dateString = todayStr,
                                    takenTimestamp = System.currentTimeMillis(),
                                    isTaken = true
                                )
                            )
                        }
                    } else {
                        db.intakeLogDao().insertLogSync(
                            IntakeLog(
                                medicationId = medicationId,
                                medicationName = primaryMedicationName,
                                scheduledTime = logTime,
                                dateString = todayStr,
                                takenTimestamp = System.currentTimeMillis(),
                                isTaken = true
                            )
                        )
                    }
                } catch (e: Exception) {
                    // Log recorded
                }
            }.start()
        }

        // Return immediately to Today screen
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(mainIntent)
        finish()
    }

    private fun snoozeAlarm() {
        stopSoundAndVibration()
        prefs.clearPendingDueAlarm()

        if (isTest) {
            android.widget.Toast.makeText(this, "Test alarmı susturuldu.", android.widget.Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(medicationId.toInt())

        if (medicationId > 0 && medicationId != 999999L && primaryMedicationName != "İlaç Hatırlatma") {
            alarmScheduler.scheduleSnooze(
                medicationId = medicationId,
                medicationName = primaryMedicationName,
                dosage = "",
                mealTiming = "",
                scheduledTime = scheduledTime,
                colorHex = colorHex,
                audioPath = audioPath,
                delayMinutes = prefs.snoozeMinutes
            )
        }

        finish()
    }

    override fun onPause() {
        super.onPause()
        // Immediately silence any ongoing sound when paused
        stopSoundAndVibration()
    }

    override fun onStop() {
        super.onStop()
        // Stop audio and vibration so device is quiet while screen is off or backgrounded
        stopSoundAndVibration()

        // If it was a test alarm, close when user navigates away or stops
        if (isTest) {
            finish()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // User explicitly tapped Home or switched apps
        stopSoundAndVibration()
        if (isTest) {
            finish()
            return
        }
        if (!hasConfirmedTaken && prefs.repeatUntilTaken && medicationId > 0 && medicationId != 999999L && primaryMedicationName != "İlaç Hatırlatma") {
            alarmScheduler.scheduleSnooze(
                medicationId = medicationId,
                medicationName = primaryMedicationName,
                dosage = "",
                mealTiming = "",
                scheduledTime = scheduledTime,
                colorHex = colorHex,
                audioPath = audioPath,
                delayMinutes = prefs.snoozeMinutes
            )
        }
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSoundRunnable?.let { handler.removeCallbacks(it) }
        autoSnoozeRunnable?.let { handler.removeCallbacks(it) }
        stopSoundAndVibration()
        voiceManager?.release()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(medicationId.toInt())
    }
}

@Composable
fun AlarmFixedScreenContent(
    medicationNames: List<String>,
    scheduledTime: String,
    isSnooze: Boolean,
    isTest: Boolean = false,
    snoozeMinutes: Int,
    onTakeMedicine: () -> Unit,
    onSnooze: () -> Unit
) {
    BackHandler {
        onSnooze()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F172A)
    ) {
        // Single fixed screen - completely fits without scrolling
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        isTest -> Color(0xFF0284C7)
                        isSnooze -> Color(0xFFB45309)
                        else -> Color(0xFFDC2626)
                    }
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.padding(top = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = "Alarm",
                        tint = Color.White,
                        modifier = Modifier
                            .size(32.dp)
                            .scale(pulseScale)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = when {
                            isTest -> "TEST ALARMI: İLAÇ VAKTİ!"
                            isSnooze -> "TEKRAR HATIRLATMA!"
                            else -> "İLAÇ VAKTİ GELDİ!"
                        },
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            // Pulsing Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(pulseScale)
                    .background(Color(0xFF0284C7), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Medication,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }

            // MEDICINE NAMES - SIMPLE, CLEAN, GIGANTIC (NO confusing tablet/meal notes!)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(3.dp, Color(0xFF38BDF8), RoundedCornerShape(22.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (medicationNames.size > 1) "LÜTFEN BU İLAÇLARI ALINIZ:" else "LÜTFEN BU İLACI ALINIZ:",
                        color = Color(0xFF94A3B8),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Shows all medicine names sharing this time slot
                    medicationNames.forEach { name ->
                        Text(
                            text = name,
                            color = Color.White,
                            fontSize = if (medicationNames.size > 1) 34.sp else 42.sp,
                            lineHeight = if (medicationNames.size > 1) 40.sp else 48.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .testTag("alarm_medication_name")
                        )
                    }

                    if (scheduledTime.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Saat: $scheduledTime",
                            color = Color(0xFF38BDF8),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ACTION BUTTONS
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // HUGE "İLACIMI ALDIM" BUTTON
                Button(
                    onClick = onTakeMedicine,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .testTag("alarm_take_button"),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = "İLACIMI ALDIM",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                // SNOOZE BUTTON
                Button(
                    onClick = onSnooze,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .testTag("alarm_snooze_button"),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Snooze,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "SUSTUR / $snoozeMinutes DK ERTELE",
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
