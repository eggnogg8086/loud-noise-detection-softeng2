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

        private const val KEY_NIOSH_ENABLED = "niosh_enabled"
        private const val KEY_NIOSH_RATIO = "niosh_ratio"
        private const val KEY_DAILY_DOSE = "daily_dose"
        private const val KEY_LAST_DOSE_UPDATE = "last_dose_update"
        private const val KEY_DOSE_NOTIFIED_TODAY = "dose_notified_today"
        private const val KEY_USE_A_WEIGHTING = "use_a_weighting"
        private const val KEY_SPEAKER_COMPENSATION = "speaker_compensation"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_INTELLIGENT_NOISE_REJECTION = "intelligent_noise_rejection"
        private const val KEY_MIC_SETUP_COMPLETED = "mic_setup_completed"
        private const val KEY_NOISE_FLOOR_DB = "noise_floor_db"
        private const val KEY_CALIBRATION_OFFSET = "calibration_offset"
        private const val KEY_INTEGRATION_TIME = "integration_time"
        private const val KEY_ALLOW_NOTIFS_ON_HEADPHONES = "allow_notifs_on_headphones"
        private const val KEY_IS_STEREO_HW = "is_stereo_hw"
        private const val KEY_DOSE_CRITICAL_NOTIFIED = "dose_critical_notified"
        private const val KEY_STORAGE_LIMIT_DAYS = "storage_limit_days"
        private const val KEY_AUTO_CLEANUP_ENABLED = "auto_cleanup_enabled"
        
        // Default to -1 (Auto/Default)
        const val MIC_ID_AUTO = -1
        // Default to UNPROCESSED (corresponds to Oboe/Android constant)
        const val PRESET_UNPROCESSED = 9 // android.media.MediaRecorder.AudioSource.UNPROCESSED
        const val PRESET_VOICE_RECOGNITION = 6 // android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION
        const val PRESET_CAMCORDER = 5 // android.media.MediaRecorder.AudioSource.CAMCORDER

        const val INTEGRATION_FAST = 0 // 125ms
        const val INTEGRATION_SLOW = 1 // 1000ms

        const val DEFAULT_THRESHOLD = 90f
        const val DEFAULT_DURATION = 2f // 2 seconds
        const val DEFAULT_NIOSH_RATIO = 0.8f
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

    var calibrationOffset: Float
        get() = getCalibrationOffset(selectedMicId)
        set(value) = setCalibrationOffset(selectedMicId, value)

    fun getCalibrationOffset(deviceId: Int): Float {
        val key = if (deviceId == MIC_ID_AUTO) KEY_CALIBRATION_OFFSET else "${KEY_CALIBRATION_OFFSET}_$deviceId"
        return prefs.getFloat(key, 0f)
    }

    fun setCalibrationOffset(deviceId: Int, offset: Float) {
        val key = if (deviceId == MIC_ID_AUTO) KEY_CALIBRATION_OFFSET else "${KEY_CALIBRATION_OFFSET}_$deviceId"
        prefs.edit().putFloat(key, offset).apply()
    }

    var integrationTime: Int
        get() = prefs.getInt(KEY_INTEGRATION_TIME, INTEGRATION_SLOW)
        set(value) = prefs.edit().putInt(KEY_INTEGRATION_TIME, value).apply()

    var nioshEnabled: Boolean
        get() = prefs.getBoolean(KEY_NIOSH_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_NIOSH_ENABLED, value).apply()

    var nioshRatio: Float
        get() = prefs.getFloat(KEY_NIOSH_RATIO, DEFAULT_NIOSH_RATIO)
        set(value) = prefs.edit().putFloat(KEY_NIOSH_RATIO, value).apply()

    var dailyDose: Float
        get() = prefs.getFloat(KEY_DAILY_DOSE, 0f)
        set(value) = prefs.edit().putFloat(KEY_DAILY_DOSE, value).apply()

    var lastDoseUpdate: Long
        get() = prefs.getLong(KEY_LAST_DOSE_UPDATE, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_DOSE_UPDATE, value).apply()

    var doseNotifiedToday: Boolean
        get() = prefs.getBoolean(KEY_DOSE_NOTIFIED_TODAY, false)
        set(value) = prefs.edit().putBoolean(KEY_DOSE_NOTIFIED_TODAY, value).apply()

    var doseCriticalNotifiedToday: Boolean
        get() = prefs.getBoolean(KEY_DOSE_CRITICAL_NOTIFIED, false)
        set(value) = prefs.edit().putBoolean(KEY_DOSE_CRITICAL_NOTIFIED, value).apply()

    var useAWeighting: Boolean
        get() = prefs.getBoolean(KEY_USE_A_WEIGHTING, true) // Default to true for dBA
        set(value) = prefs.edit().putBoolean(KEY_USE_A_WEIGHTING, value).apply()

    var speakerCompensationEnabled: Boolean
        get() = prefs.getBoolean(KEY_SPEAKER_COMPENSATION, true)
        set(value) = prefs.edit().putBoolean(KEY_SPEAKER_COMPENSATION, value).apply()

    var onboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, value).apply()

    var intelligentNoiseRejection: Boolean
        get() = prefs.getBoolean(KEY_INTELLIGENT_NOISE_REJECTION, true)
        set(value) = prefs.edit().putBoolean(KEY_INTELLIGENT_NOISE_REJECTION, value).apply()

    var micSetupCompleted: Boolean
        get() = prefs.getBoolean(KEY_MIC_SETUP_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_MIC_SETUP_COMPLETED, value).apply()

    var noiseFloorDb: Float
        get() = prefs.getFloat(KEY_NOISE_FLOOR_DB, 30f)
        set(value) = prefs.edit().putFloat(KEY_NOISE_FLOOR_DB, value).apply()

    var allowNotifsOnHeadphones: Boolean
        get() = prefs.getBoolean(KEY_ALLOW_NOTIFS_ON_HEADPHONES, false)
        set(value) = prefs.edit().putBoolean(KEY_ALLOW_NOTIFS_ON_HEADPHONES, value).apply()

    var isStereoHardware: Boolean
        get() = prefs.getBoolean(KEY_IS_STEREO_HW, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_STEREO_HW, value).apply()

    var storageLimitDays: Int
        get() = prefs.getInt(KEY_STORAGE_LIMIT_DAYS, 7)
        set(value) = prefs.edit().putInt(KEY_STORAGE_LIMIT_DAYS, value).apply()

    var autoCleanupEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CLEANUP_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CLEANUP_ENABLED, value).apply()

    fun clearDailyDose() {
        prefs.edit()
            .putFloat(KEY_DAILY_DOSE, 0f)
            .putBoolean(KEY_DOSE_NOTIFIED_TODAY, false)
            .apply()
    }
}
