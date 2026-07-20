package com.loudnoisedetectionapp

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.pow

class ExposureManager(context: Context) {
    private val settings = SettingsManager(context)
    private val history = HistoryManager(context)
    
    // In-memory cache to avoid frequent SharedPreferences reads/writes on audio thread
    private var cachedDose: Float = 0f
    private var cachedBreakdown = mutableMapOf<String, Float>()
    private var lastPersistTime: Long = 0L
    private val PERSIST_INTERVAL_MS = 5000L // Persist every 5 seconds
    private var lastUpdateDayOfYear: Int = -1

    init {
        cachedDose = settings.dailyDose
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        cachedBreakdown = history.getDoseBreakdown(todayStr).toMutableMap()
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
        
        // Update Breakdown
        val bin = when {
            db < 85f -> "< 85 dB"
            db < 90f -> "85-90 dB"
            db < 95f -> "90-95 dB"
            db < 100f -> "95-100 dB"
            else -> "> 100 dB"
        }
        cachedBreakdown[bin] = (cachedBreakdown[bin] ?: 0f) + deltaDose.toFloat()

        AudioBridge.currentDose = cachedDose
        
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

        // Also update the daily total for today in history
        val currentDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        history.saveDailyTotal(currentDateStr, cachedDose)
        history.saveDoseBreakdown(currentDateStr, cachedBreakdown)
    }

    private fun checkDailyReset() {
        val now = System.currentTimeMillis()
        val currentDay = getDayOfYear(now)

        if (lastUpdateDayOfYear != -1 && currentDay != lastUpdateDayOfYear) {
            // Save the final dose for the day that just ended
            val lastDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(settings.lastDoseUpdate))
            history.saveDailyTotal(lastDateStr, cachedDose)
            history.saveDoseBreakdown(lastDateStr, cachedBreakdown)

            cachedDose = 0f
            cachedBreakdown.clear()
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
