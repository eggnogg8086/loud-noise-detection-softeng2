package com.loudnoisedetectionapp

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    audioViewModel: AudioViewModel
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val inputDevices = remember { audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS) }

    var selectedMicId by remember { mutableStateOf(settingsManager.selectedMicId) }
    var selectedPreset by remember { mutableStateOf(settingsManager.audioPreset) }
    var threshold by remember { mutableStateOf(settingsManager.thresholdDb) }
    var duration by remember { mutableStateOf(settingsManager.durationSeconds) }

    val presets = listOf(
        "Unprocessed" to 9,
        "Voice Recognition" to 6,
        "Camcorder" to 5
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Calibration", style = MaterialTheme.typography.titleMedium)
                Button(
                    onClick = { audioViewModel.resetCalibration() },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("Reset Calibration Max")
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            }

            item {
                Text("Alert Threshold: ${threshold.toInt()} dB", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = threshold,
                    onValueChange = { 
                        threshold = it
                        settingsManager.thresholdDb = it
                    },
                    valueRange = 40f..130f,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text("App will notify if noise exceeds this level.", style = MaterialTheme.typography.bodySmall)
            }

            item {
                Text("Required Duration: ${String.format("%.1f", duration)}s", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = duration,
                    onValueChange = { 
                        duration = it
                        settingsManager.durationSeconds = it
                    },
                    valueRange = 0.1f..10f,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text("Noise must persist for this long to trigger an alert.", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            }

            item {
                Text("Microphone Hardware", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                MicRadioButton(
                    label = "Default / Auto",
                    selected = selectedMicId == -1,
                    onClick = { 
                        selectedMicId = -1
                        settingsManager.selectedMicId = -1
                        notifyService(context)
                    }
                )

                inputDevices.forEach { device ->
                    MicRadioButton(
                        label = "${device.productName} (${getDeviceTypeName(device.type)})",
                        selected = selectedMicId == device.id,
                        onClick = {
                            selectedMicId = device.id
                            settingsManager.selectedMicId = device.id
                            notifyService(context)
                        }
                    )
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            }

            item {
                Text("Audio Processing Preset", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                presets.forEach { (label, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedPreset = value
                                settingsManager.audioPreset = value
                                notifyService(context)
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedPreset == value, onClick = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        }
    }
}

@Composable
fun MicRadioButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(label)
    }
}

private fun notifyService(context: Context) {
    val intent = Intent(context, AudioMonitorService::class.java).apply {
        putExtra("restart", true)
    }
    context.startService(intent)
}

private fun getDeviceTypeName(type: Int): String {
    return when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth"
        else -> "Other"
    }
}
