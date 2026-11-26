package com.example.alarm

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

data class AlarmPermissionStatus(
    val hasNotificationPermission: Boolean = true,
    val isChannelEnabled: Boolean = true,
    val channelImportance: Int = NotificationManager.IMPORTANCE_HIGH,
    val canScheduleExactAlarms: Boolean = true,
    val canUseFullScreenIntent: Boolean = true,
    val isIgnoringBatteryOptimizations: Boolean = true
) {
    val allGranted: Boolean
        get() = hasNotificationPermission &&
            isChannelEnabled &&
            canScheduleExactAlarms &&
            canUseFullScreenIntent &&
            isIgnoringBatteryOptimizations

    val missingCount: Int
        get() = listOf(
            hasNotificationPermission,
            isChannelEnabled,
            canScheduleExactAlarms,
            canUseFullScreenIntent,
            isIgnoringBatteryOptimizations
        ).count { !it }
}

object AlarmPermissionHelper {

    fun checkStatus(context: Context): AlarmPermissionStatus {
        AlarmReceiver.createNotificationChannel(context)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

        val postGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val hasNotificationPermission = postGranted && notificationsEnabled

        var channelEnabled = true
        var channelImportance = NotificationManager.IMPORTANCE_HIGH
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = nm.getNotificationChannel(AlarmReceiver.CHANNEL_ID)
            channelImportance = channel?.importance ?: NotificationManager.IMPORTANCE_NONE
            channelEnabled = channel != null && channel.importance >= NotificationManager.IMPORTANCE_HIGH
        }

        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                alarmManager.canScheduleExactAlarms()
            } catch (_: Throwable) {
                true
            }
        } else {
            true
        }

        val canFullScreen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                nm.canUseFullScreenIntent()
            } catch (_: Throwable) {
                true
            }
        } else {
            true
        }

        val ignoringBattery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
            } catch (_: Throwable) {
                true
            }
        } else {
            true
        }

        return AlarmPermissionStatus(
            hasNotificationPermission = hasNotificationPermission,
            isChannelEnabled = channelEnabled,
            channelImportance = channelImportance,
            canScheduleExactAlarms = canExact,
            canUseFullScreenIntent = canFullScreen,
            isIgnoringBatteryOptimizations = ignoringBattery
        )
    }

    fun openNotificationSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            appDetailsIntent(context)
        }
        startSafely(context, intent, appDetailsIntent(context))
    }

    fun openChannelSettings(context: Context) {
        AlarmReceiver.createNotificationChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, AlarmReceiver.CHANNEL_ID)
            }
            startSafely(context, intent, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            })
        } else {
            openNotificationSettings(context)
        }
    }

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            startSafely(context, intent, appDetailsIntent(context))
        } else {
            startSafely(context, appDetailsIntent(context))
        }
    }

    fun openFullScreenIntentSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            startSafely(context, intent, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            })
        } else {
            openNotificationSettings(context)
        }
    }

    @SuppressLint("BatteryLife")
    fun openBatteryOptimizationSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val directIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            val listIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startSafely(context, directIntent, listIntent)
        } else {
            startSafely(context, appDetailsIntent(context))
        }
    }

    fun openNextMissingSetting(
        context: Context,
        status: AlarmPermissionStatus,
        onNeedRuntimeNotificationPermission: (() -> Unit)? = null
    ) {
        when {
            !status.hasNotificationPermission -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED &&
                    onNeedRuntimeNotificationPermission != null
                ) {
                    onNeedRuntimeNotificationPermission()
                } else {
                    openNotificationSettings(context)
                }
            }
            !status.isChannelEnabled -> openChannelSettings(context)
            !status.canScheduleExactAlarms -> openExactAlarmSettings(context)
            !status.canUseFullScreenIntent -> openFullScreenIntentSettings(context)
            !status.isIgnoringBatteryOptimizations -> openBatteryOptimizationSettings(context)
        }
    }

    private fun appDetailsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    private fun startSafely(context: Context, primary: Intent, fallback: Intent? = null) {
        try {
            primary.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(primary)
        } catch (_: Throwable) {
            if (fallback != null) {
                try {
                    fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(fallback)
                } catch (_: Throwable) {
                    try {
                        val details = appDetailsIntent(context).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(details)
                    } catch (_: Throwable) {
                        // ignore
                    }
                }
            }
        }
    }
}
