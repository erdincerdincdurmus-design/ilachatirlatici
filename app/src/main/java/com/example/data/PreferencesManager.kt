package com.example.data

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("elderly_medication_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SNOOZE_MINUTES = "key_snooze_minutes"
        private const val KEY_VOICE_ENABLED = "key_voice_enabled"
        private const val KEY_VIBRATION_ENABLED = "key_vibration_enabled"
        private const val KEY_REPEAT_UNTIL_TAKEN = "key_repeat_until_taken"
        private const val KEY_FONT_SCALE = "key_font_scale"
        private const val KEY_DEFAULT_VOICE_PATH = "key_default_voice_path"
        private const val KEY_PLAY_ALARM_ONCE = "key_play_alarm_once"
    }

    var snoozeMinutes: Int
        get() = prefs.getInt(KEY_SNOOZE_MINUTES, 5)
        set(value) = prefs.edit().putInt(KEY_SNOOZE_MINUTES, value).apply()

    var isVoiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_VOICE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_VOICE_ENABLED, value).apply()

    var isVibrationEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATION_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, value).apply()

    var repeatUntilTaken: Boolean
        get() = prefs.getBoolean(KEY_REPEAT_UNTIL_TAKEN, true)
        set(value) = prefs.edit().putBoolean(KEY_REPEAT_UNTIL_TAKEN, value).apply()

    var fontScale: String
        get() = prefs.getString(KEY_FONT_SCALE, "EXTRA_LARGE") ?: "EXTRA_LARGE"
        set(value) = prefs.edit().putString(KEY_FONT_SCALE, value).apply()

    var defaultCustomVoicePath: String?
        get() = prefs.getString(KEY_DEFAULT_VOICE_PATH, null)
        set(value) = prefs.edit().putString(KEY_DEFAULT_VOICE_PATH, value).apply()

    // Single-play alarm setting: plays once and stops, rather than looping infinitely
    var playAlarmOnce: Boolean
        get() = prefs.getBoolean(KEY_PLAY_ALARM_ONCE, true)
        set(value) = prefs.edit().putBoolean(KEY_PLAY_ALARM_ONCE, value).apply()

    // Tracking for pending due alarm that hasn't been confirmed yet
    var isPendingDueAlarm: Boolean
        get() = prefs.getBoolean("key_is_pending_due_alarm", false)
        set(value) = prefs.edit().putBoolean("key_is_pending_due_alarm", value).apply()

    var pendingDueMedicationId: Long
        get() = prefs.getLong("key_pending_med_id", -1L)
        set(value) = prefs.edit().putLong("key_pending_med_id", value).apply()

    var pendingDueMedicationName: String
        get() = prefs.getString("key_pending_med_name", "İlaç") ?: "İlaç"
        set(value) = prefs.edit().putString("key_pending_med_name", value).apply()

    var pendingDueScheduledTime: String
        get() = prefs.getString("key_pending_sched_time", "") ?: ""
        set(value) = prefs.edit().putString("key_pending_sched_time", value).apply()

    var pendingDueColorHex: Long
        get() = prefs.getLong("key_pending_color_hex", 0xFF1976D2)
        set(value) = prefs.edit().putLong("key_pending_color_hex", value).apply()

    var pendingDueAudioPath: String?
        get() = prefs.getString("key_pending_audio_path", null)
        set(value) = prefs.edit().putString("key_pending_audio_path", value).apply()

    fun setPendingDueAlarm(id: Long, name: String, time: String, colorHex: Long, audioPath: String?) {
        prefs.edit()
            .putBoolean("key_is_pending_due_alarm", true)
            .putLong("key_pending_med_id", id)
            .putString("key_pending_med_name", name)
            .putString("key_pending_sched_time", time)
            .putLong("key_pending_color_hex", colorHex)
            .putString("key_pending_audio_path", audioPath)
            .apply()
    }

    fun clearPendingDueAlarm() {
        prefs.edit()
            .putBoolean("key_is_pending_due_alarm", false)
            .putLong("key_pending_med_id", -1L)
            .putString("key_pending_med_name", "")
            .putString("key_pending_sched_time", "")
            .apply()
    }
}
