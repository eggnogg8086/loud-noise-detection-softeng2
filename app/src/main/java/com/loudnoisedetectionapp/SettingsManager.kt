package com.loudnoisedetectionapp

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioDeviceInfo
import android.media.MediaRecorder

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_MIC_ID = "selected_mic_id"
        private const val KEY_AUDIO_PRESET = "audio_preset"
        private const val KEY_THRESHOLD_DB = "threshold_db"
        private const val KEY_DURATION_SEC = "duration_sec"
        
        // Default to -1 (Auto/Default)
        const val MIC_ID_AUTO = -1
        // Default to UNPROCESSED (corresponds to Oboe/Android constant)
        const val PRESET_UNPROCESSED = 9 // android.media.MediaRecorder.AudioSource.UNPROCESSED
        const val PRESET_VOICE_RECOGNITION = 6 // android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION
        const val PRESET_CAMCORDER = 5 // android.media.MediaRecorder.AudioSource.CAMCORDER

        const val DEFAULT_THRESHOLD = 82f
        const val DEFAULT_DURATION = 1f // 1 second
    }

    var thresholdDb: Float
        get() = prefs.getFloat(KEY_THRESHOLD_DB, DEFAULT_THRESHOLD)
        set(value) = prefs.edit().putFloat(KEY_THRESHOLD_DB, value).apply()

    var durationSeconds: Float
        get() = prefs.getFloat(KEY_DURATION_SEC, DEFAULT_DURATION)
        set(value) = prefs.edit().putFloat(KEY_DURATION_SEC, value).apply()

    var selectedMicId: Int
        get() = prefs.getInt(KEY_MIC_ID, MIC_ID_AUTO)
        set(value) = prefs.edit().putInt(KEY_MIC_ID, value).apply()

    var audioPreset: Int
        get() = prefs.getInt(KEY_AUDIO_PRESET, PRESET_UNPROCESSED)
        set(value) = prefs.edit().putInt(KEY_AUDIO_PRESET, value).apply()
}
