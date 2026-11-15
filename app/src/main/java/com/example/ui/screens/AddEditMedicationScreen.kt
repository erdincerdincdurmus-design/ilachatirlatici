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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.example.ui.MedicationViewModel
import java.io.File

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
    var frequencyType by remember { mutableStateOf(initialMedication?.frequencyType ?: "DAILY") }
    var timesList by remember {
        mutableStateOf(
            initialMedication?.parseTimesList()?.toMutableList() ?: mutableListOf("08:00")
        )
    }
    var intervalHours by remember { mutableStateOf(initialMedication?.intervalHours ?: 8) }
    var daysOfWeekStr by remember { mutableStateOf(initialMedication?.daysOfWeek ?: "1,2,3,4,5,6,7") }
    var customAudioPath by remember { mutableStateOf(initialMedication?.customAudioPath) }
    var notes by remember { mutableStateOf(initialMedication?.notes ?: "") }

    val isRecording by viewModel.isRecording.collectAsState()

    // Time picker dialog state
    var showTimePickerDialog by remember { mutableStateOf(false) }

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

            // 2. Alım Sıklığı ve Gün Seçenekleri (TAM SEÇİM ÖZGÜRLÜĞÜ)
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
                        text = "İlacın hangi aralıklarla veya hangi günlerde alınacağını seçin:",
                        fontSize = 14.sp,
                        color = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Frequency Mode Pills (Her Gün, 2 Günde 1, 3 Günde 1, Haftanın Belirli Günleri)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val frequencyOptions = listOf(
                            "DAILY" to "Her Gün",
                            "EVERY_2_DAYS" to "2 Günde 1",
                            "EVERY_3_DAYS" to "3 Günde 1",
                            "CUSTOM_DAYS" to "Belirli Günler (Haftalık)"
                        )

                        frequencyOptions.forEach { (type, label) ->
                            val isSelected = frequencyType == type
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) Color(0xFF0284C7) else Color(0xFFF1F5F9),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) Color(0xFF0284C7) else Color(0xFFCBD5E1)
                                ),
                                modifier = Modifier.clickable {
                                    frequencyType = type
                                }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color(0xFF334155),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    // Interactive 7-Day Selector if CUSTOM_DAYS is selected
                    if (frequencyType == "CUSTOM_DAYS" || frequencyType == "WEEKLY_DAYS") {
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

                        val selectedCount = selectedDaysSet.size
                        Text(
                            text = "Haftada $selectedCount gün seçildi.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF166534),
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Daily Dose Count / Times Presets
                    Text(
                        text = "Günde Kaç Kez Alınacak?",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val timesPresets = listOf(
                            "Günde 1 Kez (08:00)" to listOf("08:00"),
                            "Günde 2 Kez (08:00, 20:00)" to listOf("08:00", "20:00"),
                            "Günde 3 Kez (08:00, 13:00, 20:00)" to listOf("08:00", "13:00", "20:00"),
                            "Günde 4 Kez (08:00, 13:00, 19:00, 23:00)" to listOf("08:00", "13:00", "19:00", "23:00")
                        )

                        timesPresets.forEach { (label, presetTimes) ->
                            val isPresetMatch = timesList == presetTimes
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isPresetMatch) Color(0xFF0369A1) else Color(0xFFF1F5F9),
                                modifier = Modifier.clickable {
                                    timesList = presetTimes.toMutableList()
                                }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 13.sp,
                                    fontWeight = if (isPresetMatch) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isPresetMatch) Color.White else Color(0xFF334155),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Active Scheduled Hours
                    Text(
                        text = "Alarm Saatleri:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF334155)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        timesList.forEach { time ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF0F172A),
                                modifier = Modifier.padding(bottom = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
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
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (timesList.size > 1) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Sil",
                                            tint = Color(0xFFF87171),
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable {
                                                    timesList = timesList.filter { it != time }.toMutableList()
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { showTimePickerDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Farklı Bir Saat Ekle", fontSize = 14.sp)
                    }
                }
            }

            // 3. Doz & Yemek (Opsiyonel Bilgi)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "3. DOZ & YEMEK DURUMU",
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

            // 4. Özel Ses Dosyası / Ses Kaydı
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "4. SESLİ UYARI / SES DOSYASI",
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

            // 5. İlaç Rengi
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "5. İLAÇ RENGİ",
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
                        val medToSave = (initialMedication ?: Medication(name = name)).copy(
                            name = name.trim(),
                            dosage = dosage,
                            mealTiming = mealTiming,
                            colorHex = selectedColor,
                            frequencyType = frequencyType,
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

    // Time picker dialog
    if (showTimePickerDialog) {
        val timePickerState = rememberTimePickerState(initialHour = 12, initialMinute = 0, is24Hour = true)
        Dialog(onDismissRequest = { showTimePickerDialog = false }) {
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
                        text = "Alarm Saati Seçin",
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
                        TextButton(onClick = { showTimePickerDialog = false }) {
                            Text("İptal", fontSize = 15.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val hourStr = timePickerState.hour.toString().padStart(2, '0')
                                val minStr = timePickerState.minute.toString().padStart(2, '0')
                                val newTime = "$hourStr:$minStr"
                                if (!timesList.contains(newTime)) {
                                    timesList = (timesList + newTime).sorted().toMutableList()
                                }
                                showTimePickerDialog = false
                            }
                        ) {
                            Text("Ekle", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
