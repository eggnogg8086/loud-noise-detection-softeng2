package com.loudnoisedetectionapp

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager }
    val devices = remember { audioManager.getDevices(android.media.AudioManager.GET_DEVICES_INPUTS) }
    val currentMicId = settingsManager.selectedMicId
    val activeMicName = remember(currentMicId) {
        if (currentMicId == -1) "Default / Auto"
        else devices.find { it.id == currentMicId }?.productName?.toString() ?: "Unknown Mic"
    }

    var offset by remember(currentMicId) { mutableStateOf(settingsManager.getCalibrationOffset(currentMicId)) }
    var referenceLevel by rememberSaveable { mutableStateOf("") }
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
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Calibration ensures the app matches a professional meter.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Active Profile: $activeMicName",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            // Current Reading Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color.Red, shape = androidx.compose.foundation.shape.CircleShape)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Live Reading", style = MaterialTheme.typography.labelMedium)
                        }
                        Text(
                            "${audioViewModel.currentDb.toInt()} dB",
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Auto-Calibration Section
            item {
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
                                    val currentOffset = settingsManager.getCalibrationOffset(currentMicId)
                                    val rawValue = audioViewModel.currentDb - currentOffset
                                    val newOffset = ref - rawValue
                                    offset = newOffset
                                    settingsManager.setCalibrationOffset(currentMicId, newOffset)
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
            }

            // Manual Slider Section
            item {
                Column {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Manual Offset", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${if (offset >= 0) "+" else ""}${String.format("%.1f", offset)} dB",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = offset,
                        onValueChange = { 
                            offset = it
                            settingsManager.setCalibrationOffset(currentMicId, it)
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
            }

            item {
                OutlinedButton(
                    onClick = {
                        offset = 0f
                        settingsManager.setCalibrationOffset(currentMicId, 0f)
                        notifyService(context)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset to Factory Default")
                }
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
