package com.loudnoisedetectionapp

import android.content.Context
import android.content.SharedPreferences

class CalibrationManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("calibration_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_MAX_DB = "max_recorded_db"
        private const val DEFAULT_MAX_DB = 100f
    }

    var maxRecordedDb: Float
        get() = prefs.getFloat(KEY_MAX_DB, DEFAULT_MAX_DB)
        set(value) {
            if (value > maxRecordedDb) {
                prefs.edit().putFloat(KEY_MAX_DB, value).apply()
            }
        }

    fun reset() {
        prefs.edit().putFloat(KEY_MAX_DB, DEFAULT_MAX_DB).apply()
    }
}
