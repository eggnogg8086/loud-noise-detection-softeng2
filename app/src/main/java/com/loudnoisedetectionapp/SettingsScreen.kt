package com.loudnoisedetectionapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MicrophoneInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.PermissionChecker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    audioViewModel: AudioViewModel,
    onNavigateToCalibration: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val inputDevices = remember { audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS) }
    val microphones = remember { audioManager.microphones }

    var selectedMicId by remember { mutableStateOf(settingsManager.selectedMicId) }
    var selectedPreset by remember { mutableStateOf(settingsManager.audioPreset) }
    var threshold by remember { mutableStateOf(settingsManager.thresholdDb) }
    var duration by remember { mutableStateOf(settingsManager.durationSeconds) }
    var nioshEnabled by remember { mutableStateOf(settingsManager.nioshEnabled) }
    var nioshRatio by remember { mutableStateOf(settingsManager.nioshRatio) }
    var useAWeighting by remember { mutableStateOf(settingsManager.useAWeighting) }
    var speakerCompensation by remember { mutableStateOf(settingsManager.speakerCompensationEnabled) }
    var noiseRejection by remember { mutableStateOf(settingsManager.intelligentNoiseRejection) }
    var integrationTime by remember { mutableStateOf(settingsManager.integrationTime) }
    var allowNotifsOnHeadphones by remember { mutableStateOf(settingsManager.allowNotifsOnHeadphones) }
    
    var storageLimitDays by remember { mutableStateOf(settingsManager.storageLimitDays.toFloat()) }
    var autoCleanupEnabled by remember { mutableStateOf(settingsManager.autoCleanupEnabled) }
    
    var locationEnabled by remember { 
        mutableStateOf(
            PermissionChecker.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PermissionChecker.PERMISSION_GRANTED
        ) 
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        locationEnabled = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
    }

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
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onNavigateToCalibration,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Manual Calibration (Offset)")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { audioViewModel.resetCalibration() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("Reset Session Max dB")
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            }

            item {
                Text("Integration Time", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Affects how quickly the meter reacts to noise.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = integrationTime == SettingsManager.INTEGRATION_FAST,
                        onClick = {
                            integrationTime = SettingsManager.INTEGRATION_FAST
                            settingsManager.integrationTime = SettingsManager.INTEGRATION_FAST
                            notifyService(context)
                        },
                        label = { Text("Fast (125ms)") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = integrationTime == SettingsManager.INTEGRATION_SLOW,
                        onClick = {
                            integrationTime = SettingsManager.INTEGRATION_SLOW
                            settingsManager.integrationTime = SettingsManager.INTEGRATION_SLOW
                            notifyService(context)
                        },
                        label = { Text("Slow (1s)") },
                        modifier = Modifier.weight(1f)
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            }

            item {
                Text("Features", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Noise Mapping (GPS)")
                        Text(
                            "Tag loud noise events with your location for history analysis.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = locationEnabled,
                        onCheckedChange = {
                            if (it) {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            } else {
                                // We can't really "revoke" permission, but we can stop using it logic-wise if needed.
                                // For now, the system handles the actual permission state.
                                locationEnabled = false
                            }
                        }
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            }

            item {
                Text("NIOSH Exposure Tracking", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Exposure Notifications")
                    Switch(
                        checked = nioshEnabled,
                        onCheckedChange = {
                            nioshEnabled = it
                            settingsManager.nioshEnabled = it
                        }
                    )
                }
                if (nioshEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Notify at Dose: ${(nioshRatio * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = nioshRatio,
                        onValueChange = {
                            nioshRatio = it
                            settingsManager.nioshRatio = it
                        },
                        valueRange = 0.1f..1.0f,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text("Notify when you reach this ratio of your daily limit.", style = MaterialTheme.typography.bodySmall)
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Alerts during Headphone Media")
                        Text(
                            "Allow safety notifications even when listening to music with headphones.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = allowNotifsOnHeadphones,
                        onCheckedChange = {
                            allowNotifsOnHeadphones = it
                            settingsManager.allowNotifsOnHeadphones = it
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            }

            item {
                Text("Audio Accuracy", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Use A-Weighting (dBA)")
                        Text(
                            "Adjusts dB for human ear sensitivity (NIOSH standard).",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = useAWeighting,
                        onCheckedChange = {
                            useAWeighting = it
                            settingsManager.useAWeighting = it
                            notifyService(context)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Speaker Compensation")
                        Text(
                            "Reduces false alerts from the phone's own speakers.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = speakerCompensation,
                        onCheckedChange = {
                            speakerCompensation = it
                            settingsManager.speakerCompensationEnabled = it
                            notifyService(context)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Intelligent Noise Rejection")
                        Text(
                            "Uses stereo microphones to cancel out wind and handling noise.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = noiseRejection,
                        onCheckedChange = {
                            noiseRejection = it
                            settingsManager.intelligentNoiseRejection = it
                            notifyService(context)
                        }
                    )
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
                    val micInfo = microphones.find { it.id == device.id }
                    val locStr = micInfo?.let { getMicLocationString(it.location) } ?: ""
                    val infoSuffix = micInfo?.let { 
                        " ($locStr, ${it.sensitivity.toInt()}dBFS)"
                    } ?: ""
                    
                    MicRadioButton(
                        label = "${device.productName}$infoSuffix",
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
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            }

            item {
                Text("Storage Management", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Cleanup Old Audio")
                        Text(
                            "Automatically delete large audio files after a certain time.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = autoCleanupEnabled,
                        onCheckedChange = {
                            autoCleanupEnabled = it
                            settingsManager.autoCleanupEnabled = it
                        }
                    )
                }

                if (autoCleanupEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    val daysOptions = listOf(1f, 3f, 7f, 30f)
                    Text("Keep Recordings For: ${storageLimitDays.toInt()} Days", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = storageLimitDays,
                        onValueChange = { newValue ->
                            // Snap to nearest option
                            val snapped = daysOptions.minBy { Math.abs(it - newValue) }
                            storageLimitDays = snapped
                            settingsManager.storageLimitDays = snapped.toInt()
                        },
                        valueRange = 1f..30f,
                        steps = 28,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { 
                        val history = HistoryManager(context)
                        history.deleteAllAudioFiles()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete All Audio Recordings")
                }
                Text(
                    "This only deletes the large audio files. Your decibel history log will remain.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp)
                )
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

private fun getMicLocationString(location: Int): String {
    return when (location) {
        1 -> "Main Body"
        2 -> "Front"
        3 -> "Back"
        4 -> "External"
        else -> "Unknown"
    }
}
