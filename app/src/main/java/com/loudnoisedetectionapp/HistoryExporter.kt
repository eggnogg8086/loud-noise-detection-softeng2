package com.loudnoisedetectionapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object HistoryExporter {
    fun exportToCsv(context: Context, events: List<NoiseEvent>) {
        if (events.isEmpty()) return

        val fileName = "noise_history_${System.currentTimeMillis()}.csv"
        val file = File(context.cacheDir, fileName)
        
        val header = "Timestamp,Date,Time,Decibels,Frequency 1 (Hz),Frequency 2 (Hz),Duration (s),Latitude,Longitude,Internal Speaker Active\n"
        
        try {
            file.bufferedWriter().use { out ->
                out.write(header)
                events.forEach { event ->
                    val row = StringBuilder()
                    row.append("${event.timestamp},")
                    row.append("${event.dateString},")
                    row.append("${event.timeString.replace(",", "")},")
                    row.append("${event.db},")
                    row.append("${event.freq1},")
                    row.append("${event.freq2},")
                    row.append("${event.durationSeconds},")
                    row.append("${event.latitude ?: ""},")
                    row.append("${event.longitude ?: ""},")
                    row.append("${event.isSelfNoise}\n")
                    out.write(row.toString())
                }
            }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "Loud Noise Detection History")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            context.startActivity(Intent.createChooser(intent, "Export History"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
