package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.alarm.AlarmPermissionHelper
import com.example.alarm.AlarmPermissionStatus

@Composable
fun AlarmPermissionsDialog(
    status: AlarmPermissionStatus,
    onRequestNotificationPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("alarm_permissions_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFD97706),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Alarm İzinleri Eksik",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Alarmların zamanında çalması için aşağıdaki ${status.missingCount} ayarı açınız.",
                            fontSize = 13.sp,
                            color = Color(0xFF475569)
                        )
                    }
                }

                PermissionCheckRow(
                    title = "Bildirim İzni",
                    subtitle = if (status.hasNotificationPermission) "İzin açık" else "Bildirim göndermek için gerekli",
                    isGranted = status.hasNotificationPermission,
                    onFixClick = {
                        AlarmPermissionHelper.openNextMissingSetting(
                            context = context,
                            status = status.copy(hasNotificationPermission = false),
                            onNeedRuntimeNotificationPermission = onRequestNotificationPermission
                        )
                    }
                )

                PermissionCheckRow(
                    title = "Alarm Bildirim Kanalı",
                    subtitle = if (status.isChannelEnabled) "Yüksek öncelikli alarm kanalı aktif" else "Kanal kapatılmış veya sesi/önceliği düşürülmüş",
                    isGranted = status.isChannelEnabled,
                    onFixClick = { AlarmPermissionHelper.openChannelSettings(context) }
                )

                PermissionCheckRow(
                    title = "Tam Zamanlı Alarm (Exact Alarm)",
                    subtitle = if (status.canScheduleExactAlarms) "İzin açık" else "İlaç saatini şaşmadan çalmak için gerekli",
                    isGranted = status.canScheduleExactAlarms,
                    onFixClick = { AlarmPermissionHelper.openExactAlarmSettings(context) }
                )

                PermissionCheckRow(
                    title = "Tam Ekran Uyarı İzni",
                    subtitle = if (status.canUseFullScreenIntent) "İzin açık" else "Ekran kilitliyken tam ekran alarm için gerekli",
                    isGranted = status.canUseFullScreenIntent,
                    onFixClick = { AlarmPermissionHelper.openFullScreenIntentSettings(context) }
                )

                PermissionCheckRow(
                    title = "Pil Optimizasyonu Muafiyeti",
                    subtitle = if (status.isIgnoringBatteryOptimizations) "Muafiyet aktif" else "Arka planda alarmın kapanmaması için gerekli",
                    isGranted = status.isIgnoringBatteryOptimizations,
                    onFixClick = { AlarmPermissionHelper.openBatteryOptimizationSettings(context) }
                )

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {
                        AlarmPermissionHelper.openNextMissingSetting(
                            context = context,
                            status = status,
                            onNeedRuntimeNotificationPermission = onRequestNotificationPermission
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("open_next_missing_permission_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Eksik Ayara Git",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                ) {
                    Text(
                        text = "Şimdilik Geç",
                        fontSize = 14.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
        }
    }
}

@Composable
fun MissingPermissionsBanner(
    status: AlarmPermissionStatus,
    onClickFix: () -> Unit
) {
    if (status.allGranted) return

    Surface(
        color = Color(0xFFFEF3C7),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, Color(0xFFD97706)),
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable { onClickFix() }
            .testTag("missing_permissions_banner")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Eksik alarm izni",
                    tint = Color(0xFFB45309),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "${status.missingCount} Alarm Ayarı Eksik",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF78350F)
                    )
                    Text(
                        text = "Alarmların sorunsuz çalması için dokunup düzeltin",
                        fontSize = 12.sp,
                        color = Color(0xFF92400E)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = Color(0xFFD97706),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Ayarla",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun AlarmPermissionsSummaryCard(
    status: AlarmPermissionStatus,
    onRequestNotificationPermission: () -> Unit
) {
    val context = LocalContext.current

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_permissions_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = if (status.allGranted) Color(0xFF16A34A) else Color(0xFFD97706),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (status.allGranted) {
                        "Sistem ve Alarm İzinleri (Tamam)"
                    } else {
                        "Sistem ve Alarm İzinleri (${status.missingCount} Eksik)"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
            }

            PermissionCheckRow(
                title = "Bildirim İzni",
                subtitle = if (status.hasNotificationPermission) "Açık" else "Kapalı",
                isGranted = status.hasNotificationPermission,
                onFixClick = {
                    AlarmPermissionHelper.openNextMissingSetting(
                        context = context,
                        status = status.copy(hasNotificationPermission = false),
                        onNeedRuntimeNotificationPermission = onRequestNotificationPermission
                    )
                }
            )
            PermissionCheckRow(
                title = "Bildirim Kanalı Durumu",
                subtitle = if (status.isChannelEnabled) "Yüksek Öncelikli Alarm Kanalı Aktif" else "Kapalı / Düşük Öncelikli",
                isGranted = status.isChannelEnabled,
                onFixClick = { AlarmPermissionHelper.openChannelSettings(context) }
            )
            PermissionCheckRow(
                title = "Tam Zamanlı Alarm İzni",
                subtitle = if (status.canScheduleExactAlarms) "Açık" else "Kapalı",
                isGranted = status.canScheduleExactAlarms,
                onFixClick = { AlarmPermissionHelper.openExactAlarmSettings(context) }
            )
            PermissionCheckRow(
                title = "Tam Ekran Uyarı İzni",
                subtitle = if (status.canUseFullScreenIntent) "Açık" else "Kapalı",
                isGranted = status.canUseFullScreenIntent,
                onFixClick = { AlarmPermissionHelper.openFullScreenIntentSettings(context) }
            )
            PermissionCheckRow(
                title = "Pil Optimizasyonu Muafiyeti",
                subtitle = if (status.isIgnoringBatteryOptimizations) "Muafiyet Açık" else "Kısıtlı (Optimizasyon Açık)",
                isGranted = status.isIgnoringBatteryOptimizations,
                onFixClick = { AlarmPermissionHelper.openBatteryOptimizationSettings(context) }
            )
        }
    }
}

@Composable
private fun PermissionCheckRow(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onFixClick: () -> Unit
) {
    Surface(
        color = if (isGranted) Color(0xFFF0FDF4) else Color(0xFFFEF2F2),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            1.dp,
            if (isGranted) Color(0xFF86EFAC) else Color(0xFFFCA5A5)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = if (isGranted) "Açık" else "Eksik",
                    tint = if (isGranted) Color(0xFF16A34A) else Color(0xFFDC2626),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = if (isGranted) Color(0xFF166534) else Color(0xFF991B1B)
                    )
                }
            }

            if (!isGranted) {
                Spacer(modifier = Modifier.width(6.dp))
                OutlinedButton(
                    onClick = onFixClick,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    border = BorderStroke(1.dp, Color(0xFFDC2626)),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(
                        text = "Ayarla",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFDC2626)
                    )
                }
            }
        }
    }
}
