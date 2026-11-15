package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.data.PreferencesManager
import java.io.File
import java.util.Locale

class VoiceManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var mediaPlayer: MediaPlayer? = null
    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null
    private val prefs = PreferencesManager(context)

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val turkishLocale = Locale("tr", "TR")
                val result = tts?.setLanguage(turkishLocale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.getDefault())
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    tts?.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                }
                tts?.setSpeechRate(0.85f)
                tts?.setPitch(1.0f)
                isTtsReady = true
            }
        }
    }

    /**
     * Speaks the medication reminder in Turkish using TTS with full volume on alarm stream.
     */
    fun speakMedicationReminder(medicationName: String, dosage: String = "", mealTiming: String = "") {
        if (!isTtsReady || tts == null) {
            initTts()
        }
        val instruction = if (mealTiming.isNotEmpty() && mealTiming != "Fark Etmez") "$mealTiming alınız." else ""
        val textToSpeak = "İlaç zamanı! $medicationName vakti geldi. $dosage. $instruction Lütfen ilacınızı alınız."

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM)
        }
        tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, params, "MedReminderTTS")
    }

    /**
     * Speaks a simple custom phrase.
     */
    fun speakPhrase(phrase: String) {
        tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "MedPhraseTTS")
    }

    /**
     * Plays a custom audio recording / chosen audio file, or falls back to bundled raw / TTS.
     * Guaranteed to play ONCE and never loop.
     */
    fun playCustomAudioOrSpeak(
        audioPath: String?,
        fallbackMedicationName: String,
        dosage: String = "",
        mealTiming: String = "",
        onCompleted: (() -> Unit)? = null
    ) {
        // 1. Check if medication has its own custom audio path
        val targetPath = if (!audioPath.isNullOrEmpty()) {
            audioPath
        } else {
            // 2. Otherwise check if global default audio file is configured in settings
            prefs.defaultCustomVoicePath
        }

        if (!targetPath.isNullOrEmpty()) {
            val audioFile = File(targetPath)
            if (audioFile.exists()) {
                try {
                    stopPlayback()
                    mediaPlayer = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        setDataSource(audioFile.absolutePath)
                        isLooping = false
                        setOnCompletionListener { mp ->
                            try { mp.release() } catch (e: Exception) {}
                            if (mediaPlayer == mp) mediaPlayer = null
                            onCompleted?.invoke()
                        }
                        setOnErrorListener { mp, _, _ ->
                            try { mp.release() } catch (e: Exception) {}
                            if (mediaPlayer == mp) mediaPlayer = null
                            onCompleted?.invoke()
                            true
                        }
                        prepare()
                        start()
                    }
                    return
                } catch (e: Exception) {
                    Log.e("VoiceManager", "Error playing custom audio file: ${e.message}")
                }
            } else if (targetPath.startsWith("content://")) {
                try {
                    stopPlayback()
                    mediaPlayer = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        setDataSource(context, Uri.parse(targetPath))
                        isLooping = false
                        setOnCompletionListener { mp ->
                            try { mp.release() } catch (e: Exception) {}
                            if (mediaPlayer == mp) mediaPlayer = null
                            onCompleted?.invoke()
                        }
                        setOnErrorListener { mp, _, _ ->
                            try { mp.release() } catch (e: Exception) {}
                            if (mediaPlayer == mp) mediaPlayer = null
                            onCompleted?.invoke()
                            true
                        }
                        prepare()
                        start()
                    }
                    return
                } catch (e: Exception) {
                    Log.e("VoiceManager", "Error playing URI: ${e.message}")
                }
            }
        }

        // 3. Fallback to TTS speech if voice is enabled in settings (plays once)
        if (prefs.isVoiceEnabled) {
            speakMedicationReminder(fallbackMedicationName, dosage, mealTiming)
        }
        onCompleted?.invoke()
    }

    /**
     * Copies any user-selected audio file (from device file picker / Uri) to app storage.
     */
    fun copyAudioUriToStorage(uri: Uri): String? {
        return try {
            val customDir = File(context.filesDir, "custom_sounds").apply { mkdirs() }
            val destFile = File(customDir, "custom_voice_${System.currentTimeMillis()}.mp3")
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e("VoiceManager", "Failed to copy audio from Uri: ${e.message}")
            null
        }
    }

    /**
     * Start recording custom voice note via microphone.
     */
    @Suppress("DEPRECATION")
    fun startRecording(fileNamePrefix: String = "med_voice"): File? {
        try {
            stopPlayback()
            val voiceDir = File(context.filesDir, "voice_recordings").apply { mkdirs() }
            val outputFile = File(voiceDir, "${fileNamePrefix}_${System.currentTimeMillis()}.m4a")

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            currentRecordingFile = outputFile
            return outputFile
        } catch (e: Exception) {
            Log.e("VoiceManager", "Failed to start recording: ${e.message}")
            return null
        }
    }

    /**
     * Stops the active audio recording.
     */
    fun stopRecording(): File? {
        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            val savedFile = currentRecordingFile
            currentRecordingFile = null
            savedFile
        } catch (e: Exception) {
            Log.e("VoiceManager", "Failed to stop recording: ${e.message}")
            null
        }
    }

    fun playFilePath(path: String) {
        val file = File(path)
        if (file.exists()) {
            try {
                stopPlayback()
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    setDataSource(file.absolutePath)
                    isLooping = false
                    setOnCompletionListener { mp ->
                        try { mp.release() } catch (e: Exception) {}
                        if (mediaPlayer == mp) mediaPlayer = null
                    }
                    setOnErrorListener { mp, _, _ ->
                        try { mp.release() } catch (e: Exception) {}
                        if (mediaPlayer == mp) mediaPlayer = null
                        true
                    }
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                Log.e("VoiceManager", "Error playing file: ${e.message}")
            }
        }
    }

    fun stopPlayback() {
        try {
            mediaPlayer?.let { player ->
                try {
                    player.stop()
                } catch (e: Exception) {
                    // ignore if already stopped or released
                }
                try {
                    player.release()
                } catch (e: Exception) {}
            }
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e("VoiceManager", "Error stopping player: ${e.message}")
        }
        try {
            if (tts?.isSpeaking == true) {
                tts?.stop()
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    fun release() {
        stopPlayback()
        try {
            mediaRecorder?.release()
            mediaRecorder = null
        } catch (e: Exception) {
            // ignore
        }
        try {
            tts?.shutdown()
            tts = null
        } catch (e: Exception) {
            // ignore
        }
    }
}
