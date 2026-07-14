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
    val durationSeconds: Float = 0f
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

data class HistoryInsights(
    val avgDb: Float,
    val maxDb: Float,
    val mostFrequentHz: Float,
    val totalEvents: Int,
    val trend: String
)

class HistoryManager(context: Context) {
    private val prefs = context.getSharedPreferences("noise_history", Context.MODE_PRIVATE)

    fun addEvent(db: Float, f1: Float, f2: Float, duration: Float) {
        val event = NoiseEvent(System.currentTimeMillis() - (duration * 1000).toLong(), db, f1, f2, duration)
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
                    obj.optDouble("dur", 0.0).toFloat()
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

    fun getInsights(): HistoryInsights {
        val events = getAllEvents()
        if (events.isEmpty()) return HistoryInsights(0f, 0f, 0f, 0, "No data")

        val avgDb = events.map { it.db }.average().toFloat()
        val maxDb = events.maxOf { it.db }
        
        // Find most frequent primary frequency (excluding 0Hz/Invalid) rounded to nearest 100Hz
        val freqMap = events.filter { it.freq1 > 20f }.groupBy { (it.freq1 / 100).toInt() * 100 }
        val mostFrequentHz = freqMap.maxByOrNull { it.value.size }?.key?.toFloat() ?: 0f

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

        return HistoryInsights(avgDb, maxDb, mostFrequentHz, events.size, trend)
    }

    fun getEventsGroupedByDay(): Map<String, List<NoiseEvent>> {
        return getAllEvents().groupBy { it.dateString }.toSortedMap(compareByDescending { it })
    }

    private fun saveEvents(events: List<NoiseEvent>) {
        val jsonArray = JSONArray()
        // Limit history to last 1000 events
        val start = if (events.size > 1000) events.size - 1000 else 0
        for (i in start until events.size) {
            val event = events[i]
            val obj = JSONObject().apply {
                put("t", event.timestamp)
                put("d", event.db.toDouble())
                put("f1", event.freq1.toDouble())
                put("f2", event.freq2.toDouble())
                put("dur", event.durationSeconds.toDouble())
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString("events", jsonArray.toString()).apply()
    }

    fun clearHistory() {
        prefs.edit().remove("events").remove("dose_samples").apply()
    }
}
