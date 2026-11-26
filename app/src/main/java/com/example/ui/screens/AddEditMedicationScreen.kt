package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.data.model.Medication
import com.example.data.model.ScheduleCalculator
import com.example.ui.MedicationViewModel
import java.io.File

private sealed interface TimePickerTarget {
    data object StartTime : TimePickerTarget
    data class EditChip(val index: Int) : TimePickerTarget
    data object AddNew : TimePickerTarget
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditMedicationScreen(
    viewModel: MedicationViewModel,
    initialMedication: Medication? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var name by remember { mutableStateOf(initialMedication?.name ?: "") }
    var dosage by remember { mutableStateOf(initialMedication?.dosage ?: "1 Tablet") }
    var mealTiming by remember { mutableStateOf(initialMedication?.mealTiming ?: "Tok Karna") }
    var selectedColor by remember { mutableStateOf(initialMedication?.colorHex ?: 0xFF1E88E5) }
    val initialIntervalDays = initialMedication?.parseIntervalDays()
    val initialFrequencyMode = when {
        initialIntervalDays != null -> "EVERY_N_DAYS"
        initialMedication?.frequencyType == "CUSTOM_DAYS" || initialMedication?.frequencyType == "WEEKLY_DAYS" -> "CUSTOM_DAYS"
        else -> "DAILY"
    }
    var frequencyMode by remember { mutableStateOf(initialFrequencyMode) }
    var intervalDays by remember { mutableStateOf(initialIntervalDays ?: 2) }

    val initialTimes = initialMedication?.parseTimesList()?.takeIf { it.isNotEmpty() } ?: listOf("08:00")
    var timesList by remember { mutableStateOf(initialTimes.toMutableList()) }
    var startTime by remember { mutableStateOf(initialTimes.first()) }
    var periodMode by remember { mutableStateOf("DAILY_COUNT") } // "DAILY_COUNT" or "HOUR_INTERVAL"
    var dailyDoseCount by remember { mutableStateOf(initialTimes.size.coerceIn(1, 24)) }
    var intervalHours by remember { mutableStateOf(initialMedication?.intervalHours ?: 8) }
    var daysOfWeekStr by remember { mutableStateOf(initialMedication?.daysOfWeek ?: "1,2,3,4,5,6,7") }
    var customAudioPath by remember { mutableStateOf(initialMedication?.customAudioPath) }
    var notes by remember { mutableStateOf(initialMedication?.notes ?: "") }

    val isRecording by viewModel.isRecording.collectAsState()

    // Time picker dialog target (null when hidden)
    var timePickerTarget by remember { mutableStateOf<TimePickerTarget?>(null) }

    // File picker launcher for audio files (.mp3, .wav, .m4a)
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val saved = viewModel.importAudioFileFromUri(uri)
            if (saved != null) {
                customAudioPath = saved
            }
        }
    }

    // Permission launcher for microphone recording
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startVoiceRecording()
        }
    }

    val quickNameSuggestions = listOf(
        "Aspirin", "Ağrı Kesici", "Kalp İlacı",
        "Kolesterol", "Vitamin D3", "Göz Damlası", "Mide Koruyucu"
    )

    val dosageOptions = listOf("1 Tablet", "2 Tablet", "1/2 Tablet", "1 Kaşık", "5 Damla", "1 İğne")
    val mealOptions = listOf("Tok Karna", "Aç Karna", "Yemekle Birlikte", "Fark Etmez")
    val colorsList = listOf(0xFF1E88E5, 0xFFE53935, 0xFF43A047, 0xFFFB8C00, 0xFF8E24AA, 0xFF00ACC1)

    val weekDays = listOf(
        1 to "Pzt",
        2 to "Sal",
        3 to "Çar",
        4 to "Per",
        5 to "Cum",
        6 to "Cmt",
        7 to "Paz"
    )

    val selectedDaysSet = remember(daysOfWeekStr) {
        daysOfWeekStr.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (initialMedication == null) "Yeni İlaç Ekle" else "İlacı Düzenle",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Geri",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF8FAFC))
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. İlaç İsmi
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "1. İLAÇ İSMİ",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0284C7)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("medication_name_input"),
                        placeholder = { Text("Örn: Aspirin", fontSize = 16.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Hızlı Seçim:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickNameSuggestions.forEach { suggestion ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (name == suggestion) Color(0xFF0284C7) else Color(0xFFF1F5F9),
                                modifier = Modifier.clickable { name = suggestion }
                            ) {
                                Text(
                                    text = suggestion,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (name == suggestion) Color.White else Color(0xFF334155),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Alım Sıklığı ve Gün Seçenekleri (DİNAMİK SEÇİM ÖZGÜRLÜĞÜ)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "2. ALIM SIKLIĞI VE GÜNLER",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0284C7)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "İlacın kaç günde bir veya haftanın hangi günlerinde alınacağını seçin:",
                        fontSize = 14.sp,
                        color = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Frequency Mode Pills
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val frequencyOptions = listOf(
                            "DAILY" to "Her Gün",
                            "EVERY_N_DAYS" to "X Günde Bir",
                            "CUSTOM_DAYS" to "Belirli Günler (Haftalık)"
                        )

                        frequencyOptions.forEach { (type, label) ->
                            val isSelected = frequencyMode == type
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) Color(0xFF0284C7) else Color(0xFFF1F5F9),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) Color(0xFF0284C7) else Color(0xFFCBD5E1)
                                ),
                                modifier = Modifier.clickable {
                                    frequencyMode = type
                                }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color(0xFF334155),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }

                    // Dynamic Day Interval Selector (2..90 days)
                    if (frequencyMode == "EVERY_N_DAYS") {
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            color = Color(0xFFF0F9FF),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBAE6FD)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Kaç günde bir alınacak?",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (intervalDays > 2) Color(0xFF0284C7) else Color(0xFFE2E8F0),
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .clickable(enabled = intervalDays > 2) {
                                                if (intervalDays > 2) intervalDays--
                                            }
                                            .testTag("interval_days_decrease")
                                    ) {
                                        Box(
                                            modifier = Modifier.size(48.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Remove,
                                                contentDescription = "Günü azalt",
                                                tint = if (intervalDays > 2) Color.White else Color(0xFF94A3B8)
                                            )
                                        }
                                    }

                                    Text(
                                        text = "$intervalDays Günde 1",
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF0369A1),
                                        modifier = Modifier.testTag("interval_days_value")
                                    )

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (intervalDays < 90) Color(0xFF0284C7) else Color(0xFFE2E8F0),
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .clickable(enabled = intervalDays < 90) {
                                                if (intervalDays < 90) intervalDays++
                                            }
                                            .testTag("interval_days_increase")
                                    ) {
                                        Box(
                                            modifier = Modifier.size(48.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Günü artır",
                                                tint = if (intervalDays < 90) Color.White else Color(0xFF94A3B8)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val quickDayIntervals = listOf(
                                        2 to "2 Günde 1",
                                        3 to "3 Günde 1",
                                        4 to "4 Günde 1",
                                        5 to "5 Günde 1",
                                        7 to "7 Günde 1 (Haftalık)",
                                        10 to "10 Günde 1",
                                        14 to "14 Günde 1",
                                        15 to "15 Günde 1",
                                        30 to "30 Günde 1 (Aylık)"
                                    )
                                    quickDayIntervals.forEach { (days, label) ->
                                        val isPicked = intervalDays == days
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isPicked) Color(0xFF0369A1) else Color.White,
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (isPicked) Color(0xFF0369A1) else Color(0xFFCBD5E1)
                                            ),
                                            modifier = Modifier.clickable { intervalDays = days }
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 12.sp,
                                                fontWeight = if (isPicked) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isPicked) Color.White else Color(0xFF334155),
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Interactive 7-Day Selector if CUSTOM_DAYS is selected
                    if (frequencyMode == "CUSTOM_DAYS") {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Hangi günlerde alınacak? (İstediğiniz günlere dokunup seçebilirsiniz):",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            weekDays.forEach { (dayNum, dayLabel) ->
                                val isDaySelected = selectedDaysSet.contains(dayNum)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isDaySelected) Color(0xFF16A34A) else Color(0xFFF1F5F9),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isDaySelected) Color(0xFF16A34A) else Color(0xFFCBD5E1)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 2.dp)
                                        .clickable {
                                            val newSet = if (isDaySelected) {
                                                if (selectedDaysSet.size > 1) selectedDaysSet - dayNum else selectedDaysSet
                                            } else {
                                                selectedDaysSet + dayNum
                                            }
                                            daysOfWeekStr = newSet.sorted().joinToString(",")
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = dayLabel,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDaySelected) Color.White else Color(0xFF334155)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val dayHelpers = listOf(
                                "Hafta İçi" to "1,2,3,4,5",
                                "Hafta Sonu" to "6,7",
                                "Pzt-Çar-Cum" to "1,3,5",
                                "Sal-Per-Cmt" to "2,4,6"
                            )
                            dayHelpers.forEach { (helperLabel, helperDays) ->
                                val isMatch = daysOfWeekStr == helperDays
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isMatch) Color(0xFF15803D) else Color(0xFFF1F5F9),
                                    modifier = Modifier.clickable { daysOfWeekStr = helperDays }
                                ) {
                                    Text(
                                        text = helperLabel,
                                        fontSize = 12.sp,
                                        fontWeight = if (isMatch) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isMatch) Color.White else Color(0xFF334155),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        val selectedCount = selectedDaysSet.size
                        Text(
                            text = "Haftada $selectedCount gün seçildi.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF166534),
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }

            // 3. Periyot ve Alarm Saatleri (BAŞLANGIÇ SAATİNE GÖRE DİNAMİK BÖLME + DOĞRUDAN DÜZENLEME)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "3. PERİYOT VE SAATLER",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0284C7)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Başlangıç saatini ve periyodu belirleyin; saatler başlangıç saatinize göre otomatik hesaplanır.",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Editable Başlangıç Saati row
                    Surface(
                        color = Color(0xFFF8FAFC),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { timePickerTarget = TimePickerTarget.StartTime }
                            .testTag("start_time_selector")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Başlangıç Saati",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "Değiştirmek için dokunun",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF0F172A)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = startTime,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Başlangıç saatini düzenle",
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Periyot Subheader & Mode Toggle
                    Text(
                        text = "Periyot",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val periodTabs = listOf(
                            "DAILY_COUNT" to "Günde Kaç Kez",
                            "HOUR_INTERVAL" to "Saat Aralığı"
                        )
                        periodTabs.forEach { (modeKey, modeLabel) ->
                            val isSelected = periodMode == modeKey
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) Color(0xFF0369A1) else Color(0xFFF1F5F9),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) Color(0xFF0369A1) else Color(0xFFCBD5E1)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        periodMode = modeKey
                                        timesList = if (modeKey == "DAILY_COUNT") {
                                            ScheduleCalculator.generateDailyTimes(startTime, dailyDoseCount).toMutableList()
                                        } else {
                                            ScheduleCalculator.generateIntervalHourTimes(startTime, intervalHours).toMutableList()
                                        }
                                    }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = modeLabel,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else Color(0xFF334155)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (periodMode == "DAILY_COUNT") {
                        // Dynamic Daily Count Stepper (1..12) + Quick Presets
                        Surface(
                            color = Color(0xFFF0F9FF),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBAE6FD)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (dailyDoseCount > 1) Color(0xFF0369A1) else Color(0xFFE2E8F0),
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .clickable(enabled = dailyDoseCount > 1) {
                                                if (dailyDoseCount > 1) {
                                                    dailyDoseCount--
                                                    timesList = ScheduleCalculator.generateDailyTimes(startTime, dailyDoseCount).toMutableList()
                                                }
                                            }
                                            .testTag("period_count_decrease")
                                    ) {
                                        Box(
                                            modifier = Modifier.size(48.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Remove,
                                                contentDescription = "Periyodu azalt",
                                                tint = if (dailyDoseCount > 1) Color.White else Color(0xFF94A3B8)
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "Günde $dailyDoseCount Kez",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF0369A1),
                                            modifier = Modifier.testTag("period_count_value")
                                        )
                                        Text(
                                            text = "$startTime başlangıçlı",
                                            fontSize = 12.sp,
                                            color = Color(0xFF0284C7)
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (dailyDoseCount < 12) Color(0xFF0369A1) else Color(0xFFE2E8F0),
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .clickable(enabled = dailyDoseCount < 12) {
                                                if (dailyDoseCount < 12) {
                                                    dailyDoseCount++
                                                    timesList = ScheduleCalculator.generateDailyTimes(startTime, dailyDoseCount).toMutableList()
                                                }
                                            }
                                            .testTag("period_count_increase")
                                    ) {
                                        Box(
                                            modifier = Modifier.size(48.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Periyodu artır",
                                                tint = if (dailyDoseCount < 12) Color.White else Color(0xFF94A3B8)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(1, 2, 3, 4, 5, 6).forEach { count ->
                                        val isPicked = dailyDoseCount == count
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isPicked) Color(0xFF0369A1) else Color.White,
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (isPicked) Color(0xFF0369A1) else Color(0xFFCBD5E1)
                                            ),
                                            modifier = Modifier.clickable {
                                                dailyDoseCount = count
                                                timesList = ScheduleCalculator.generateDailyTimes(startTime, count).toMutableList()
                                            }
                                        ) {
                                            Text(
                                                text = "Günde $count Kez",
                                                fontSize = 12.sp,
                                                fontWeight = if (isPicked) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isPicked) Color.White else Color(0xFF334155),
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Dynamic Hour Interval Stepper (Her X Saatte 1)
                        Surface(
                            color = Color(0xFFF0F9FF),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBAE6FD)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (intervalHours > 1) Color(0xFF0369A1) else Color(0xFFE2E8F0),
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .clickable(enabled = intervalHours > 1) {
                                                if (intervalHours > 1) {
                                                    intervalHours--
                                                    timesList = ScheduleCalculator.generateIntervalHourTimes(startTime, intervalHours).toMutableList()
                                                }
                                            }
                                            .testTag("period_hours_decrease")
                                    ) {
                                        Box(
                                            modifier = Modifier.size(48.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Remove,
                                                contentDescription = "Saat aralığını azalt",
                                                tint = if (intervalHours > 1) Color.White else Color(0xFF94A3B8)
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "$intervalHours Saatte 1",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF0369A1)
                                        )
                                        Text(
                                            text = "$startTime başlangıçlı (24 sa)",
                                            fontSize = 12.sp,
                                            color = Color(0xFF0284C7)
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (intervalHours < 24) Color(0xFF0369A1) else Color(0xFFE2E8F0),
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .clickable(enabled = intervalHours < 24) {
                                                if (intervalHours < 24) {
                                                    intervalHours++
                                                    timesList = ScheduleCalculator.generateIntervalHourTimes(startTime, intervalHours).toMutableList()
                                                }
                                            }
                                            .testTag("period_hours_increase")
                                    ) {
                                        Box(
                                            modifier = Modifier.size(48.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Saat aralığını artır",
                                                tint = if (intervalHours < 24) Color.White else Color(0xFF94A3B8)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(4, 6, 8, 12).forEach { hrs ->
                                        val isPicked = intervalHours == hrs
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isPicked) Color(0xFF0369A1) else Color.White,
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (isPicked) Color(0xFF0369A1) else Color(0xFFCBD5E1)
                                            ),
                                            modifier = Modifier.clickable {
                                                intervalHours = hrs
                                                timesList = ScheduleCalculator.generateIntervalHourTimes(startTime, hrs).toMutableList()
                                            }
                                        ) {
                                            Text(
                                                text = "$hrs Saatte 1",
                                                fontSize = 12.sp,
                                                fontWeight = if (isPicked) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isPicked) Color.White else Color(0xFF334155),
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Active Scheduled Hours (Directly Editable Chips)
                    Text(
                        text = "Alarm Saatleri (Düzenlemek için saate dokunun):",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF334155)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        timesList.forEachIndexed { index, time ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF0F172A),
                                modifier = Modifier
                                    .clickable {
                                        timePickerTarget = TimePickerTarget.EditChip(index)
                                    }
                                    .testTag("alarm_time_chip_$index")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = time,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Saati Düzenle",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    if (timesList.size > 1) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Saati Sil",
                                            tint = Color(0xFFF87171),
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clickable {
                                                    val updated = timesList.filterIndexed { i, _ -> i != index }
                                                    timesList = updated.toMutableList()
                                                    dailyDoseCount = updated.size.coerceIn(1, 24)
                                                    if (updated.isNotEmpty()) {
                                                        startTime = updated.first()
                                                    }
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { timePickerTarget = TimePickerTarget.AddNew },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Farklı Bir Saat Ekle", fontSize = 14.sp)
                    }
                }
            }

            // 4. Doz & Yemek (Opsiyonel Bilgi)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "4. DOZ & YEMEK DURUMU",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0284C7)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Doz Miktarı:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF475569)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        dosageOptions.forEach { opt ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (dosage == opt) Color(0xFF16A34A) else Color(0xFFF1F5F9),
                                modifier = Modifier.clickable { dosage = opt }
                            ) {
                                Text(
                                    text = opt,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (dosage == opt) Color.White else Color(0xFF334155),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Yemek Durumu:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF475569)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        mealOptions.forEach { meal ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (mealTiming == meal) Color(0xFF0369A1) else Color(0xFFF1F5F9),
                                modifier = Modifier.clickable { mealTiming = meal }
                            ) {
                                Text(
                                    text = meal,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (mealTiming == meal) Color.White else Color(0xFF334155),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 5. Özel Ses Dosyası / Ses Kaydı
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "5. SESLİ UYARI / SES DOSYASI",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0284C7)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "İsteğe bağlı olarak bu ilaç için özel ses kaydı veya mp3 ses dosyası ekleyebilirsiniz.",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (customAudioPath != null) {
                        val fileName = File(customAudioPath!!).name
                        Surface(
                            color = Color(0xFFF0FDF4),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF16A34A)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = fileName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF166534)
                                )
                                Row {
                                    IconButton(onClick = { viewModel.playRecordedVoice(customAudioPath!!) }) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Dinle", tint = Color(0xFF16A34A))
                                    }
                                    IconButton(onClick = { customAudioPath = null }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Sil", tint = Color(0xFFDC2626))
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { audioPickerLauncher.launch("audio/*") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                        ) {
                            Icon(Icons.Default.Audiotrack, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ses Dosyası Seç", fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                if (isRecording) {
                                    val saved = viewModel.stopVoiceRecording()
                                    if (saved != null) customAudioPath = saved
                                } else {
                                    val hasMicPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasMicPermission) {
                                        viewModel.startVoiceRecording()
                                    } else {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (isRecording) Color(0xFFDC2626) else Color(0xFF0F172A)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (isRecording) "Durdur" else "Ses Kaydet",
                                fontSize = 13.sp,
                                color = if (isRecording) Color(0xFFDC2626) else Color(0xFF0F172A)
                            )
                        }
                    }
                }
            }

            // 6. İlaç Rengi
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "6. İLAÇ RENGİ",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0284C7)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        colorsList.forEach { col ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color(col), CircleShape)
                                    .border(
                                        width = if (selectedColor == col) 3.dp else 0.dp,
                                        color = if (selectedColor == col) Color(0xFF0F172A) else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColor = col },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectedColor == col) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Seçildi",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // SAVE BUTTON
            Button(
                onClick = {
                    if (name.trim().isNotEmpty()) {
                        val finalFrequencyType = when (frequencyMode) {
                            "EVERY_N_DAYS" -> "EVERY_${intervalDays}_DAYS"
                            "CUSTOM_DAYS" -> "CUSTOM_DAYS"
                            else -> "DAILY"
                        }
                        val medToSave = (initialMedication ?: Medication(name = name)).copy(
                            name = name.trim(),
                            dosage = dosage,
                            mealTiming = mealTiming,
                            colorHex = selectedColor,
                            frequencyType = finalFrequencyType,
                            timesOfDay = timesList.joinToString(","),
                            intervalHours = intervalHours,
                            daysOfWeek = daysOfWeekStr,
                            customAudioPath = customAudioPath,
                            notes = notes,
                            isActive = true
                        )
                        viewModel.saveMedication(medToSave) {
                            onBack()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .testTag("save_medication_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                enabled = name.trim().isNotEmpty()
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "İLACI KAYDET",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // Time picker dialog (Supports editing StartTime, editing any individual time chip directly, or adding a new time)
    timePickerTarget?.let { target ->
        val (initHour, initMinute) = when (target) {
            TimePickerTarget.StartTime -> ScheduleCalculator.parseTime(startTime)
            is TimePickerTarget.EditChip -> ScheduleCalculator.parseTime(
                timesList.getOrNull(target.index) ?: "08:00"
            )
            TimePickerTarget.AddNew -> 12 to 0
        }
        val dialogTitle = when (target) {
            TimePickerTarget.StartTime -> "Başlangıç Saatini Düzenle"
            is TimePickerTarget.EditChip -> "Alarm Saatini Düzenle"
            TimePickerTarget.AddNew -> "Yeni Alarm Saati Ekle"
        }
        val confirmLabel = when (target) {
            TimePickerTarget.AddNew -> "Ekle"
            else -> "Güncelle"
        }

        key(target) {
            val timePickerState = rememberTimePickerState(
                initialHour = initHour,
                initialMinute = initMinute,
                is24Hour = true
            )
            Dialog(onDismissRequest = { timePickerTarget = null }) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = dialogTitle,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TimePicker(state = timePickerState)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { timePickerTarget = null }) {
                                Text("İptal", fontSize = 15.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val pickedTime = ScheduleCalculator.formatTime(
                                        timePickerState.hour,
                                        timePickerState.minute
                                    )
                                    when (target) {
                                        TimePickerTarget.StartTime -> {
                                            startTime = pickedTime
                                            timesList = if (periodMode == "DAILY_COUNT") {
                                                ScheduleCalculator.generateDailyTimes(pickedTime, dailyDoseCount).toMutableList()
                                            } else {
                                                ScheduleCalculator.generateIntervalHourTimes(pickedTime, intervalHours).toMutableList()
                                            }
                                        }
                                        is TimePickerTarget.EditChip -> {
                                            val updated = timesList.toMutableList()
                                            if (target.index in updated.indices) {
                                                updated[target.index] = pickedTime
                                            }
                                            val distinctList = updated.distinct().toMutableList()
                                            timesList = distinctList
                                            dailyDoseCount = distinctList.size.coerceIn(1, 24)
                                            if (target.index == 0 && distinctList.isNotEmpty()) {
                                                startTime = distinctList.first()
                                            }
                                        }
                                        TimePickerTarget.AddNew -> {
                                            val distinctList = (timesList + pickedTime)
                                                .distinct()
                                                .toMutableList()
                                            timesList = distinctList
                                            dailyDoseCount = distinctList.size.coerceIn(1, 24)
                                            if (distinctList.isNotEmpty() && startTime.isBlank()) {
                                                startTime = distinctList.first()
                                            }
                                        }
                                    }
                                    timePickerTarget = null
                                }
                            ) {
                                Text(confirmLabel, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
