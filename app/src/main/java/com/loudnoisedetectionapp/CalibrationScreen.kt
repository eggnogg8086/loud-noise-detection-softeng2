package com.loudnoisedetectionapp

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(onBack: () -> Unit, audioViewModel: AudioViewModel = viewModel()) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    
    var offset by remember { mutableStateOf(settingsManager.calibrationOffset) }
    var referenceLevel by remember { mutableStateOf("") }
    var isCalibratingAuto by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manual Calibration") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                "Calibration ensures the app matches a professional meter.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Current Reading Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Current Reading", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "${audioViewModel.currentDb.toInt()} dB",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Auto-Calibration Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Auto Match", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Enter the level shown on your reference meter:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = referenceLevel,
                        onValueChange = { referenceLevel = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Reference dB") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            val ref = referenceLevel.toFloatOrNull()
                            if (ref != null) {
                                // Calculate new offset
                                // currentDb = raw + oldOffset -> raw = currentDb - oldOffset
                                // target = raw + newOffset -> target = (currentDb - oldOffset) + newOffset
                                // newOffset = target - (currentDb - oldOffset)
                                val currentOffset = settingsManager.calibrationOffset
                                val rawValue = audioViewModel.currentDb - currentOffset
                                val newOffset = ref - rawValue
                                offset = newOffset
                                settingsManager.calibrationOffset = newOffset
                                notifyService(context)
                            }
                        },
                        enabled = referenceLevel.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Calculate & Apply Offset")
                    }
                }
            }

            // Manual Slider Section
            Column {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Manual Offset", style = MaterialTheme.typography.titleMedium)
                    Text("${if (offset >= 0) "+" else ""}${String.format("%.1f", offset)} dB", fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = offset,
                    onValueChange = { 
                        offset = it
                        settingsManager.calibrationOffset = it
                    },
                    onValueChangeFinished = {
                        notifyService(context)
                    },
                    valueRange = -30f..30f
                )
                Text(
                    "Shift the entire scale up or down.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(Modifier.weight(1f))

            OutlinedButton(
                onClick = {
                    offset = 0f
                    settingsManager.calibrationOffset = 0f
                    notifyService(context)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reset to Factory Default")
            }
        }
    }
}

private fun notifyService(context: android.content.Context) {
    val intent = android.content.Intent(context, AudioMonitorService::class.java).apply {
        putExtra("restart", true)
    }
    context.startService(intent)
}
