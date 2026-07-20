package com.loudnoisedetectionapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class NoiseEvent(
    val timestamp: Long,
    val db: Float,
    val freq1: Float,
    val freq2: Float,
    val durationSeconds: Float = 0f,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val audioPath: String? = null,
    val isSelfNoise: Boolean = false
) {
    val dateString: String
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
    
    val timeString: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

    fun getRecommendation(): String {
        return when {
            db >= 100 -> "EXTREME RISK: Double protection required (Earplugs + Muffs)."
            freq1 > 2000 -> "High Frequency Noise: Foam earplugs recommended for maximum attenuation."
            freq1 < 500 -> "Low Frequency Noise: Earmuffs or Active Noise Cancelling (ANC) recommended."
            else -> "Standard Protection: High-quality earplugs or earmuffs recommended."
        }
    }
}

data class DoseSample(
    val timestamp: Long,
    val dose: Float
) {
    val timeString: String
        get() = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}

data class DailyTotal(
    val dateString: String,
    val dose: Float
)

data class HistoryInsights(
    val avgDb: Float,
    val maxDb: Float,
    val mostFrequentHz: Float,
    val totalEvents: Int,
    val trend: String
)

class HistoryManager(context: Context) {
    private val prefs = context.getSharedPreferences("noise_history", Context.MODE_PRIVATE)

    fun addEvent(db: Float, f1: Float, f2: Float, duration: Float, lat: Double? = null, lon: Double? = null, audio: String? = null, isSelfNoise: Boolean = false) {
        val event = NoiseEvent(System.currentTimeMillis() - (duration * 1000).toLong(), db, f1, f2, duration, lat, lon, audio, isSelfNoise)
        val events = getAllEvents().toMutableList()
        events.add(event)
        saveEvents(events)
    }

    fun getAllEvents(): List<NoiseEvent> {
        val jsonString = prefs.getString("events", "[]") ?: "[]"
        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<NoiseEvent>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(NoiseEvent(
                    obj.getLong("t"),
                    obj.getDouble("d").toFloat(),
                    obj.getDouble("f1").toFloat(),
                    obj.getDouble("f2").toFloat(),
                    obj.optDouble("dur", 0.0).toFloat(),
                    if (obj.has("lat")) obj.getDouble("lat") else null,
                    if (obj.has("lon")) obj.getDouble("lon") else null,
                    if (obj.has("path")) obj.getString("path") else null,
                    obj.optBoolean("self", false)
                ))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addDoseSample(dose: Float) {
        val samples = getDoseSamples().toMutableList()
        samples.add(DoseSample(System.currentTimeMillis(), dose))
        
        // Keep only last 200 samples
        val start = if (samples.size > 200) samples.size - 200 else 0
        val trimmed = samples.subList(start, samples.size)
        
        val jsonArray = JSONArray()
        trimmed.forEach { sample ->
            jsonArray.put(JSONObject().apply {
                put("t", sample.timestamp)
                put("v", sample.dose.toDouble())
            })
        }
        prefs.edit().putString("dose_samples", jsonArray.toString()).apply()
    }

    fun getDoseSamples(): List<DoseSample> {
        val jsonString = prefs.getString("dose_samples", "[]") ?: "[]"
        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<DoseSample>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(DoseSample(obj.getLong("t"), obj.getDouble("v").toFloat()))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getDoseSamplesForToday(): List<DoseSample> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return getDoseSamples().filter { 
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.timestamp)) == today
        }
    }

    fun saveDailyTotal(date: String, dose: Float) {
        val totals = getDailyTotals().toMutableList()
        val index = totals.indexOfFirst { it.dateString == date }
        if (index != -1) {
            totals[index] = DailyTotal(date, dose)
        } else {
            totals.add(DailyTotal(date, dose))
        }

        val start = if (totals.size > 365) totals.size - 365 else 0
        val trimmed = totals.subList(start, totals.size)

        val jsonArray = JSONArray()
        trimmed.forEach { total ->
            jsonArray.put(JSONObject().apply {
                put("d", total.dateString)
                put("v", total.dose.toDouble())
            })
        }
        prefs.edit().putString("daily_totals", jsonArray.toString()).apply()
    }

    fun saveDoseBreakdown(date: String, breakdown: Map<String, Float>) {
        val allBreakdowns = prefs.getString("dose_breakdowns", "{}") ?: "{}"
        val root = JSONObject(allBreakdowns)
        val dayObj = JSONObject()
        breakdown.forEach { (range, value) ->
            dayObj.put(range, value.toDouble())
        }
        root.put(date, dayObj)
        prefs.edit().putString("dose_breakdowns", root.toString()).apply()
    }

    fun getDoseBreakdown(date: String): Map<String, Float> {
        val allBreakdowns = prefs.getString("dose_breakdowns", "{}") ?: "{}"
        return try {
            val root = JSONObject(allBreakdowns)
            val dayObj = root.optJSONObject(date) ?: return emptyMap()
            val map = mutableMapOf<String, Float>()
            val keys = dayObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = dayObj.getDouble(key).toFloat()
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun getDailyTotals(): List<DailyTotal> {
        val jsonString = prefs.getString("daily_totals", "[]") ?: "[]"
        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<DailyTotal>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(DailyTotal(obj.getString("d"), obj.getDouble("v").toFloat()))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getInsights(): HistoryInsights {
        val events = getAllEvents()
        if (events.isEmpty()) return HistoryInsights(0f, 0f, 0f, 0, "No data")

        val avgDb = events.map { it.db }.average().toFloat()
        val maxDb = events.maxOf { it.db }
        
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val eventsToday = events.filter { it.dateString == today }.size
        val eventsYesterday = events.filter { 
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -1)
            it.dateString == SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
        }.size

        val trend = when {
            eventsToday > eventsYesterday -> "Noise activity is increasing compared to yesterday."
            eventsToday < eventsYesterday -> "Noise activity is decreasing compared to yesterday."
            else -> "Noise activity is stable."
        }

        // Find most frequent primary frequency (excluding 0Hz/Invalid) rounded to nearest 50Hz
        val validEvents = events.filter { it.freq1 > 25f }
        val mostFrequentHz = if (validEvents.isNotEmpty()) {
            val freqMap = validEvents.groupBy { Math.round(it.freq1 / 50f) * 50 }
            freqMap.maxByOrNull { it.value.size }?.key?.toFloat() ?: 0f
        } else 0f

        return HistoryInsights(avgDb, maxDb, mostFrequentHz, events.size, trend)
    }

    fun getEventsGroupedByDay(): Map<String, List<NoiseEvent>> {
        return getAllEvents().groupBy { it.dateString }.toSortedMap(compareByDescending { it })
    }

    private fun saveEvents(events: List<NoiseEvent>) {
        val jsonArray = JSONArray()
        val start = if (events.size > 1000) events.size - 1000 else 0
        for (i in start until events.size) {
            val event = events[i]
            val obj = JSONObject().apply {
                put("t", event.timestamp)
                put("d", event.db.toDouble())
                put("f1", event.freq1.toDouble())
                put("f2", event.freq2.toDouble())
                put("dur", event.durationSeconds.toDouble())
                event.latitude?.let { put("lat", it) }
                event.longitude?.let { put("lon", it) }
                event.audioPath?.let { put("path", it) }
                if (event.isSelfNoise) put("self", true)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString("events", jsonArray.toString()).apply()
    }

    fun clearHistory() {
        prefs.edit().remove("events").remove("dose_samples").apply()
    }
}
