package com.loudnoisedetectionapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoiseHistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val historyManager = remember { HistoryManager(context) }
    var groupedEvents by remember { mutableStateOf(historyManager.getEventsGroupedByDay()) }
    val insights = remember(groupedEvents) { historyManager.getInsights() }
    val doseSamples = remember { historyManager.getDoseSamplesForToday() }

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
                        groupedEvents = emptyMap()
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
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${event.timeString} (${event.durationSeconds.toInt()}s)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = "${event.db.toInt()} dB",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.error
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
                            }
                        }
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
            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(modifier = Modifier.fillMaxSize().padding(bottom = 20.dp)) {
                    val width = size.width
                    val height = size.height
                    val maxDose = (samples.maxOfOrNull { it.dose } ?: 1f).coerceAtLeast(1f)
                    
                    // Draw grid lines
                    drawLine(labelColor.copy(0.2f), Offset(0f, 0f), Offset(width, 0f))
                    drawLine(labelColor.copy(0.2f), Offset(0f, height), Offset(width, height))
                    
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
                
                // Time labels (X-axis)
                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (samples.isNotEmpty()) {
                        Text(samples.first().timeString, fontSize = 10.sp, color = labelColor)
                        if (samples.size > 2) {
                            Text(samples[samples.size / 2].timeString, fontSize = 10.sp, color = labelColor)
                        }
                        Text(samples.last().timeString, fontSize = 10.sp, color = labelColor)
                    }
                }
            }
        }
    }
}
