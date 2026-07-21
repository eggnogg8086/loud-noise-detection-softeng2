package com.loudnoisedetectionapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoiseHistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val historyManager = remember { HistoryManager(context) }
    val settingsManager = remember { SettingsManager(context) }
    
    var groupedEvents by remember { mutableStateOf(historyManager.getEventsGroupedByDay()) }
    var doseSamples by remember { mutableStateOf(historyManager.getDoseSamplesForToday()) }
    var dailyTotals by remember { mutableStateOf(historyManager.getDailyTotals()) }

    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    var doseBreakdown by remember { mutableStateOf(historyManager.getDoseBreakdown(today)) }
    
    val insights = remember(groupedEvents) { historyManager.getInsights() }

    // Audio Playback State
    var playingAudioPath by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }

    val exoPlayer = remember { 
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                    if (playing) {
                        totalDuration = duration.coerceAtLeast(0)
                    }
                }
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) {
                        isPlaying = false
                        currentPosition = 0
                    }
                }
            })
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isPlaying) {
                currentPosition = exoPlayer.currentPosition
                delay(200)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Noise History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        historyManager.clearHistory()
                        settingsManager.clearDailyDose()
                        groupedEvents = emptyMap()
                        doseSamples = emptyList()
                        dailyTotals = emptyList()
                        doseBreakdown = emptyMap()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear History")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (groupedEvents.isEmpty() && doseSamples.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("No history recorded yet.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (doseSamples.isNotEmpty()) {
                    item {
                        DoseChart(samples = doseSamples)
                    }
                }

                if (doseBreakdown.isNotEmpty()) {
                    item {
                        DoseHeatmapChart(breakdown = doseBreakdown)
                    }
                }

                if (dailyTotals.size > 1) {
                    item {
                        TrendChart(
                            title = "Weekly Trend (Daily Totals %)",
                            totals = dailyTotals,
                            days = 7
                        )
                    }
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Insights", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(8.dp))
                            Text("Average Level: ${"%.1f".format(insights.avgDb)} dB")
                            Text("Peak Detected: ${"%.1f".format(insights.maxDb)} dB")
                            Text("Typical Frequency: ${insights.mostFrequentHz.toInt()} Hz")
                            Spacer(Modifier.height(8.dp))
                            Text(insights.trend, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                groupedEvents.forEach { (date, events) ->
                    item(key = date) {
                        Text(
                            text = date,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(events, key = { it.timestamp }) { event ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (event.isDay) Icons.Default.WbSunny else Icons.Default.NightsStay,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = if (event.isDay) Color(0xFFFFB300) else Color(0xFF9FA8DA)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "${event.timeString} (${event.durationSeconds.toInt()}s)",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    Text(
                                        text = "${event.db.toInt()} dB",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                if (event.isSelfNoise) {
                                    Text(
                                        text = "⚠ Internal Speaker Active",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "1st Dominant",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                        Text(
                                            text = "${event.freq1.toInt()} Hz",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "2nd Dominant",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                        Text(
                                            text = "${event.freq2.toInt()} Hz",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Recommendation:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = event.getRecommendation(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (event.latitude != null && event.longitude != null) {
                                    val lat = event.latitude
                                    val lon = event.longitude
                                    Spacer(Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        TextButton(
                                            onClick = {
                                                val uri = "geo:$lat,$lon?q=$lat,$lon(Loud Noise)"
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                                                context.startActivity(intent)
                                            },
                                            contentPadding = PaddingValues(0.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("View on Map", style = MaterialTheme.typography.labelMedium)
                                        }
                                        
                                        if (event.audioPath != null) {
                                            Spacer(Modifier.weight(1f))
                                            IconButton(
                                                onClick = {
                                                    if (playingAudioPath == event.audioPath) {
                                                        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                                    } else {
                                                        playingAudioPath = event.audioPath
                                                        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(java.io.File(event.audioPath))))
                                                        exoPlayer.prepare()
                                                        exoPlayer.play()
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (playingAudioPath == event.audioPath && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    contentDescription = if (isPlaying) "Pause Audio" else "Play Audio"
                                                )
                                            }
                                        }
                                    }

                                    if (playingAudioPath == event.audioPath && event.audioPath != null) {
                                        Column(modifier = Modifier.padding(top = 8.dp)) {
                                            Slider(
                                                value = currentPosition.toFloat(),
                                                onValueChange = { 
                                                    currentPosition = it.toLong()
                                                    exoPlayer.seekTo(it.toLong())
                                                },
                                                valueRange = 0f..(totalDuration.toFloat().coerceAtLeast(1f)),
                                                modifier = Modifier.fillMaxWidth().height(24.dp)
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = formatDuration(currentPosition),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                                Text(
                                                    text = formatDuration(totalDuration),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                        }
                                    }
                                } else if (event.audioPath != null) {
                                    Spacer(Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                if (playingAudioPath == event.audioPath) {
                                                    if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                                } else {
                                                    playingAudioPath = event.audioPath
                                                    exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(java.io.File(event.audioPath))))
                                                    exoPlayer.prepare()
                                                    exoPlayer.play()
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (playingAudioPath == event.audioPath && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = if (isPlaying) "Pause Audio" else "Play Audio"
                                            )
                                        }

                                        if (playingAudioPath == event.audioPath) {
                                            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                                Slider(
                                                    value = currentPosition.toFloat(),
                                                    onValueChange = { 
                                                        currentPosition = it.toLong()
                                                        exoPlayer.seekTo(it.toLong())
                                                    },
                                                    valueRange = 0f..(totalDuration.toFloat().coerceAtLeast(1f)),
                                                    modifier = Modifier.fillMaxWidth().height(24.dp)
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = formatDuration(currentPosition),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.outline
                                                    )
                                                    Text(
                                                        text = formatDuration(totalDuration),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.outline
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DoseHeatmapChart(breakdown: Map<String, Float>) {
    val totalDose = breakdown.values.sum()
    if (totalDose == 0f) return

    val ranges = listOf("< 85 dB", "85-90 dB", "90-95 dB", "95-100 dB", "> 100 dB")
    val colors = listOf(
        Color(0xFF4CAF50), // Green
        Color(0xFFFFC107), // Amber
        Color(0xFFFF9800), // Orange
        Color(0xFFF44336), // Red
        Color(0xFFB71C1C)  // Dark Red
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Dose Contribution Heatmap", style = MaterialTheme.typography.labelLarge)
            Text("Which volume levels are draining your budget?", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))

            ranges.forEachIndexed { index, range ->
                val value = breakdown[range] ?: 0f
                val percentage = (value / totalDose)

                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = range,
                        modifier = Modifier.width(80.dp),
                        style = MaterialTheme.typography.labelSmall
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(12.dp)
                            .padding(horizontal = 8.dp)
                    ) {
                        // Background
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = Color.LightGray.copy(alpha = 0.3f),
                            shape = MaterialTheme.shapes.extraSmall
                        ) {}

                        // Percentage Bar
                        if (percentage > 0) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth(percentage)
                                    .fillMaxHeight(),
                                color = colors[index],
                                shape = MaterialTheme.shapes.extraSmall
                            ) {}
                        }
                    }

                    Text(
                        text = "${(percentage * 100).toInt()}%",
                        modifier = Modifier.width(35.dp),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                }
            }
        }
    }
}

@Composable
fun TrendChart(title: String, totals: List<DailyTotal>, days: Int) {
    val primaryColor = MaterialTheme.colorScheme.tertiary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    val displayTotals = remember(totals) {
        val lastN = totals.takeLast(days)
        // If we have fewer than 'days', pad with empty totals for visual consistency
        if (lastN.size < days) {
            List(days - lastN.size) { DailyTotal("", 0f) } + lastN
        } else {
            lastN
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val chartWidth = maxWidth - 50.dp
                val chartHeight = maxHeight - 20.dp

                Canvas(modifier = Modifier
                    .width(chartWidth)
                    .height(chartHeight)) {
                    val width = size.width
                    val height = size.height
                    val maxDose = (displayTotals.maxOfOrNull { it.dose } ?: 1f).coerceAtLeast(1f)

                    // Draw 100% limit line
                    val limitY = height - (1.0f / maxDose) * height
                    if (limitY in 0f..height) {
                        drawLine(
                            color = Color.Red.copy(alpha = 0.5f),
                            start = Offset(0f, limitY),
                            end = Offset(width, limitY),
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    if (displayTotals.isNotEmpty()) {
                        val barWidth = width / days
                        displayTotals.forEachIndexed { index, total ->
                            if (total.dateString.isNotEmpty()) {
                                val barHeight = (total.dose / maxDose) * height
                                drawRect(
                                    color = if (total.dose >= 1.0f) Color.Red else primaryColor,
                                    topLeft = Offset(index * barWidth + 2.dp.toPx(), height - barHeight),
                                    size = Size(barWidth - 4.dp.toPx(), barHeight)
                                )
                            }
                        }
                    }
                }

                // Limit Label
                val maxDose = (displayTotals.maxOfOrNull { it.dose } ?: 1f).coerceAtLeast(1f)
                val limitYPercent = 1.0f / maxDose
                if (limitYPercent <= 1.0f) {
                    Text(
                        "100% Limit",
                        color = Color.Red.copy(alpha = 0.7f),
                        fontSize = 9.sp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(y = chartHeight * (1f - limitYPercent) - 6.dp)
                    )
                }

                // Labels
                Row(
                    modifier = Modifier
                        .width(chartWidth)
                        .align(Alignment.BottomStart),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val validTotals = displayTotals.filter { it.dateString.isNotEmpty() }
                    if (validTotals.isNotEmpty()) {
                        Text(validTotals.first().dateString.substring(5), fontSize = 10.sp, color = labelColor)
                        Text(validTotals.last().dateString.substring(5), fontSize = 10.sp, color = labelColor)
                    }
                }
            }
        }
    }
}

@Composable
fun DoseChart(samples: List<DoseSample>) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Daily Dose Progression (%)", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val chartWidth = maxWidth - 40.dp
                val chartHeight = maxHeight - 24.dp

                Canvas(modifier = Modifier
                    .width(chartWidth)
                    .height(chartHeight)) {
                    val width = size.width
                    val height = size.height
                    val maxDose = (samples.maxOfOrNull { it.dose } ?: 1f).coerceAtLeast(1f)

                    // Draw horizontal grid lines
                    val levels = listOf(0.25f, 0.5f, 0.75f, 1.0f)
                    levels.forEach { level ->
                        val y = height - (level / maxDose) * height
                        if (y in 0f..height) {
                            drawLine(
                                color = labelColor.copy(alpha = 0.2f),
                                start = Offset(0f, y),
                                end = Offset(width, y),
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    }

                    // Draw vertical notches
                    if (samples.size > 1) {
                        val notchCount = 4
                        for (i in 0..notchCount) {
                            val x = (i.toFloat() / notchCount) * width
                            drawLine(
                                color = labelColor.copy(alpha = 0.1f),
                                start = Offset(x, 0f),
                                end = Offset(x, height),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    }

                    if (samples.size > 1) {
                        val path = Path()
                        samples.forEachIndexed { index, sample ->
                            val x = (index.toFloat() / (samples.size - 1)) * width
                            val y = height - (sample.dose / maxDose) * height
                            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, primaryColor, style = Stroke(width = 2.dp.toPx()))

                        // Fill area under path
                        val fillPath = Path()
                        samples.forEachIndexed { index, sample ->
                            val x = (index.toFloat() / (samples.size - 1)) * width
                            val y = height - (sample.dose / maxDose) * height
                            if (index == 0) fillPath.moveTo(x, y) else fillPath.lineTo(x, y)
                        }
                        fillPath.lineTo(width, height)
                        fillPath.lineTo(0f, height)
                        fillPath.close()
                        drawPath(fillPath, primaryColor.copy(alpha = 0.1f))
                    }
                }

                // Y-axis Labels (Right side)
                val maxDose = (samples.maxOfOrNull { it.dose } ?: 1f).coerceAtLeast(1f)
                val levels = listOf(0.25f, 0.5f, 0.75f, 1.0f)
                levels.forEach { level ->
                    val yPercent = level / maxDose
                    if (yPercent <= 1.0f) {
                        Text(
                            text = "${(level * 100).toInt()}%",
                            fontSize = 9.sp,
                            color = labelColor.copy(alpha = 0.6f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(y = chartHeight * (1f - yPercent) - 6.dp)
                        )
                    }
                }

                // Time labels (X-axis)
                Row(
                    modifier = Modifier
                        .width(chartWidth)
                        .align(Alignment.BottomStart)
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (samples.isNotEmpty()) {
                        Text(samples.first().timeString, fontSize = 9.sp, color = labelColor)
                        if (samples.size > 2) {
                            Text(samples[samples.size / 2].timeString, fontSize = 9.sp, color = labelColor)
                        }
                        Text(samples.last().timeString, fontSize = 9.sp, color = labelColor)
                    }
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
