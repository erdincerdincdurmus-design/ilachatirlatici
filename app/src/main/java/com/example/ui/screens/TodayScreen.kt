package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MedicationViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TodayScreen(
    viewModel: MedicationViewModel
) {
    val doses by viewModel.todayDoses.collectAsState()
    val allMeds by viewModel.allMedications.collectAsState()

    // Formatted date string in Turkish
    val todayDateFormatted = remember {
        val sdf = SimpleDateFormat("d MMMM EEEE", Locale("tr", "TR"))
        sdf.format(Date()).uppercase()
    }

    val takenDoses = doses.filter { it.isTaken }
    val remainingDoses = doses.filter { !it.isTaken }

    // Screen is fixed and fits within view - non-scrolling split layout
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .testTag("today_screen_root"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Simple Top Header: Date
        Surface(
            color = Color(0xFF0F172A),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = todayDateFormatted,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }

        // ==================== ÜST KISIM (1. YARI): ALINAN İLAÇLAR (PEMBE ARKA PLAN) ====================
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(2.dp, Color(0xFFEC4899), RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFCE7F3)), // Soft Pink Background
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Section Title (Deep Rose Pink)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Alınan İlaçlar",
                        tint = Color(0xFFDB2777),
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ALINAN İLAÇLAR",
                        color = Color(0xFF831843), // High contrast berry pink
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (takenDoses.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (allMeds.isEmpty()) "Henüz kayıtlı ilaç bulunmuyor." else "Bugün henüz alınan ilaç yok.",
                            color = Color(0xFF9D174D),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // Simple list showing ONLY medication name and time
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        takenDoses.forEach { dose ->
                            Surface(
                                color = Color.White.copy(alpha = 0.95f),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFBCFE8)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = dose.medicationName,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF831843)
                                    )
                                    Text(
                                        text = "Saat ${dose.scheduledTime}",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFBE185D)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==================== ALT KISIM (2. YARI): ALINACAK İLAÇLAR (YEŞİL ARKA PLAN) ====================
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.2f)
                .border(2.dp, Color(0xFF16A34A), RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7)), // Soft Green Background
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                if (remainingDoses.isNotEmpty()) {
                    // We have medicines to take!
                    val nextTime = remainingDoses.first().scheduledTime
                    val nextDosesAtSameTime = remainingDoses.filter { it.scheduledTime == nextTime }

                    Column(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Alarm,
                                contentDescription = "Alınacak İlaç",
                                tint = Color(0xFF15803D),
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ALINACAK İLAÇ (Saat: $nextTime)",
                                color = Color(0xFF14532D),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        // Medication Names - Clean, Simple & Large!
                        nextDosesAtSameTime.forEach { dose ->
                            Text(
                                text = "• ${dose.medicationName}",
                                fontSize = 32.sp,
                                lineHeight = 38.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF0F172A),
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }

                        if (remainingDoses.size > nextDosesAtSameTime.size) {
                            val laterCount = remainingDoses.size - nextDosesAtSameTime.size
                            Text(
                                text = "+ Daha sonraki saatlerde $laterCount ilaç daha var",
                                fontSize = 14.sp,
                                color = Color(0xFF166534),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Status Banner: Clearly confirms the alarm is armed
                        Surface(
                            color = Color.White.copy(alpha = 0.95f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF86EFAC)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Alarm,
                                    contentDescription = null,
                                    tint = Color(0xFF16A34A),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Alarm Hazır (Saat: $nextTime)",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF14532D)
                                    )
                                    Text(
                                        text = "Vakti geldiğinde telefon sesli ve tam ekran uyaracaktır.",
                                        fontSize = 12.sp,
                                        color = Color(0xFF166534)
                                    )
                                }
                            }
                        }
                    }

                    // Optional Button: Only for taking the medicine before the alarm rings
                    OutlinedButton(
                        onClick = {
                            viewModel.markAllDueDosesTaken(nextTime)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("today_take_medicine_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF14532D)),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF16A34A))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF16A34A),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Vaktinden Önce Aldım",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF14532D)
                            )
                        }
                    }

                } else if (allMeds.isEmpty()) {
                    // Empty state when no medications are added yet
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = "İlaç Yok",
                            tint = Color(0xFF16A34A),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Kayıtlı İlaç Yok",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF14532D),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "İlaç eklemek için aşağıdaki 'İlaçlarım' sekmesine dokununuz.",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF166534),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // Reassuring Banner when all medicines for today are taken
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Bitti",
                            tint = Color(0xFF16A34A),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "✓ Bugün alınacak ilaç kalmadı",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF14532D),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tüm ilaçlarınızı aldınız. Lütfen tekrar ilaç almayınız.",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF166534),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
