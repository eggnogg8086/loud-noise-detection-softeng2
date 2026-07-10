package com.loudnoisedetectionapp

import android.content.Context
import android.util.Log
import java.util.Calendar
import kotlin.math.pow

class ExposureManager(context: Context) {
    private val settings = SettingsManager(context)
    
    // In-memory cache to avoid frequent SharedPreferences reads/writes on audio thread
    private var cachedDose: Float = 0f
    private var lastPersistTime: Long = 0L
    private val PERSIST_INTERVAL_MS = 5000L // Persist every 5 seconds
    private var lastUpdateDayOfYear: Int = -1

    init {
        cachedDose = settings.dailyDose
        lastUpdateDayOfYear = getDayOfYear(settings.lastDoseUpdate)
        checkDailyReset()
    }

    /**
     * ΔDose = Δt * 2^((L - 85) / 3) / 28800
     */
    fun addExposure(db: Float, durationSeconds: Float) {
        if (!settings.nioshEnabled) return

        checkDailyReset()

        // Ti in seconds = 28800 / 2^((db - 85) / 3)
        // If db is very low, 2^((db-85)/3) is near 0, allowedSeconds is near Infinity.
        // We use Double for precision during accumulation.
        val exponent = (db.toDouble() - 85.0) / 3.0
        val allowedSeconds = 28800.0 / 2.0.pow(exponent)
        
        val deltaDose = durationSeconds.toDouble() / allowedSeconds
        
        cachedDose += deltaDose.toFloat()
        
        // Persist occasionally to avoid hammering disk
        val now = System.currentTimeMillis()
        if (now - lastPersistTime > PERSIST_INTERVAL_MS) {
            persist()
        }
    }

    fun getCurrentDose(): Float {
        checkDailyReset()
        return cachedDose
    }

    fun shouldNotify(): Boolean {
        if (!settings.nioshEnabled || settings.doseNotifiedToday) return false
        return cachedDose >= settings.nioshRatio
    }

    fun markNotified() {
        settings.doseNotifiedToday = true
        persist()
    }

    fun persist() {
        settings.dailyDose = cachedDose
        settings.lastDoseUpdate = System.currentTimeMillis()
        lastPersistTime = System.currentTimeMillis()
    }

    private fun checkDailyReset() {
        val now = System.currentTimeMillis()
        val currentDay = getDayOfYear(now)

        if (lastUpdateDayOfYear != -1 && currentDay != lastUpdateDayOfYear) {
            cachedDose = 0f
            settings.dailyDose = 0f
            settings.doseNotifiedToday = false
            settings.lastDoseUpdate = now
            lastUpdateDayOfYear = currentDay
            Log.d("ExposureManager", "Daily dose reset for new day")
        }
    }

    private fun getDayOfYear(time: Long): Int {
        if (time == 0L) return -1
        val cal = Calendar.getInstance().apply { timeInMillis = time }
        // Use year * 1000 + dayOfYear to handle year changes
        return cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
    }
}
