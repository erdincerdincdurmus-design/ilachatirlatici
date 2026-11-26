package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AlarmEventLogger
import com.example.ui.MedicationViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(viewModel: MedicationViewModel) {
    val logs by viewModel.allLogs.collectAsState()
    val alarmLogs by viewModel.allAlarmLogs.collectAsState()
    val selectedSubTab by viewModel.historySubTab.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("history_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (selectedSubTab == 0) "İlaç Alım Geçmişi" else "Alarm Kayıtları",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (selectedSubTab == 0) {
                                "Alınan tüm ilaçların saat kayıtları"
                            } else {
                                "Tetiklenen alarmlar, bildirimler ve hata/iptal kayıtları"
                            },
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Segmented toggle: İlaç Geçmişi vs Alarm Kayıtları
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val tabs = listOf(
                    0 to "İlaç Alımı (${logs.size})",
                    1 to "Alarm Kayıtları (${alarmLogs.size})"
                )
                tabs.forEach { (tabIndex, label) ->
                    val isSelected = selectedSubTab == tabIndex
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) Color(0xFF0284C7) else Color.White,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) Color(0xFF0284C7) else Color(0xFFCBD5E1)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .clickable { viewModel.historySubTab.value = tabIndex }
                            .testTag(if (tabIndex == 0) "history_tab_intake" else "history_tab_alarms")
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else Color(0xFF334155)
                            )
                        }
                    }
                }
            }
        }

        if (selectedSubTab == 0) {
            if (logs.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Henüz kaydedilmiş bir alım geçmişi yok.",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }
            } else {
                items(logs, key = { "intake_${it.id}" }) { log ->
                    val dateFormatted = remember(log.takenTimestamp) {
                        SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale("tr", "TR"))
                            .format(Date(log.takenTimestamp))
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF16A34A), RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Alındı",
                                tint = Color(0xFF16A34A),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = log.medicationName,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF14532D)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Alındı: $dateFormatted",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF166534)
                                )
                                Text(
                                    text = "Planlanan Saat: ${log.scheduledTime}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF475569)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            if (alarmLogs.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.clearAlarmLogs() },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("clear_alarm_logs_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Kayıtları Temizle",
                                fontSize = 13.sp,
                                color = Color(0xFFDC2626)
                            )
                        }
                    }
                }
            }

            if (alarmLogs.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Henüz kaydedilmiş alarm olayı yok.",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }
            } else {
                items(alarmLogs, key = { "alarm_${it.id}" }) { event ->
                    val timeFormatted = remember(event.timestamp) {
                        SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale("tr", "TR"))
                            .format(Date(event.timestamp))
                    }

                    val (bgColor, borderColor, badgeColor, iconVector) = when (event.eventType) {
                        AlarmEventLogger.TYPE_NOTIFICATION_SHOWN -> listOf(
                            Color(0xFFDCFCE7), Color(0xFF16A34A), Color(0xFF15803D), Icons.Default.NotificationsActive
                        )
                        AlarmEventLogger.TYPE_TRIGGERED -> listOf(
                            Color(0xFFE0F2FE), Color(0xFF0284C7), Color(0xFF0369A1), Icons.Default.Alarm
                        )
                        AlarmEventLogger.TYPE_NEXT_SCHEDULED, AlarmEventLogger.TYPE_RESCHEDULED -> listOf(
                            Color(0xFFCCFBF1), Color(0xFF0D9488), Color(0xFF0F766E), Icons.Default.Refresh
                        )
                        AlarmEventLogger.TYPE_SKIPPED -> listOf(
                            Color(0xFFFEF3C7), Color(0xFFD97706), Color(0xFFB45309), Icons.Default.WarningIconFallback
                        )
                        else -> listOf(
                            Color(0xFFFEE2E2), Color(0xFFDC2626), Color(0xFFB91C1C), Icons.Default.ErrorOutline
                        )
                    }

                    val cardBg = bgColor as Color
                    val cardBorder = borderColor as Color
                    val badge = badgeColor as Color
                    val icon = iconVector as androidx.compose.ui.graphics.vector.ImageVector

                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, cardBorder, RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = event.eventType,
                                tint = badge,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = badge,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = event.eventType,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = timeFormatted,
                                        fontSize = 11.sp,
                                        color = Color(0xFF475569)
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                val titleLine = buildString {
                                    if (event.medicationName.isNotBlank()) append(event.medicationName)
                                    if (event.scheduledTime.isNotBlank()) {
                                        if (isNotEmpty()) append(" • ")
                                        append("Saat: ${event.scheduledTime}")
                                    }
                                }
                                if (titleLine.isNotEmpty()) {
                                    Text(
                                        text = titleLine,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                }

                                Text(
                                    text = event.details,
                                    fontSize = 13.sp,
                                    color = Color(0xFF1E293B)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private val Icons.Filled.WarningIconFallback: androidx.compose.ui.graphics.vector.ImageVector
    get() = Icons.Default.ErrorOutline
