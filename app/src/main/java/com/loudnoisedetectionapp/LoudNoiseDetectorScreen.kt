package com.loudnoisedetectionapp

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.PermissionChecker
import androidx.lifecycle.viewmodel.compose.viewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoudNoiseDetectorScreen(
    modifier: Modifier = Modifier,
    audioViewModel: AudioViewModel = viewModel()
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    var hasPermission by remember { 
        mutableStateOf(
            PermissionChecker.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PermissionChecker.PERMISSION_GRANTED
        ) 
    }
    var showDoseInfo by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var showCalibration by rememberSaveable { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }

    if (showSettings) {
        BackHandler {
            showSettings = false
        }
        SettingsScreen(
            onBack = { showSettings = false },
            audioViewModel = audioViewModel,
            onNavigateToCalibration = {
                showSettings = false
                showCalibration = true
            }
        )
        return
    }

    if (showCalibration) {
        BackHandler {
            showCalibration = false
            showSettings = true
        }
        CalibrationScreen(
            onBack = {
                showCalibration = false
                showSettings = true
            },
            audioViewModel = audioViewModel
        )
        return
    }

    if (showHistory) {
        BackHandler {
            showHistory = false
        }
        NoiseHistoryScreen(
            onBack = { showHistory = false },
            audioViewModel = audioViewModel
        )
        return
    }

    LaunchedEffect(Unit) {
        audioViewModel.initCalibration(context)
    }

    val currentDb = audioViewModel.currentDb

    val noiseDescription = remember(currentDb) {
        when {
            currentDb < 40f -> "Very Quiet"
            currentDb < 55f -> "Quiet"
            currentDb < 70f -> "Moderate"
            currentDb < 85f -> "Loud"
            currentDb < 100f -> "Very Loud"
            else -> "Extremely Loud"
        }
    }

    DisposableEffect(Unit) {
        onDispose { 
            // We do not stop the recording here because the Service 
            // handles the audio lifecycle for background monitoring.
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Loud Noise Detection") },
                navigationIcon = {
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.Help, contentDescription = "Help")
                    }
                },
                actions = {
                    TextButton(onClick = { showHistory = true }) {
                        Text("History")
                    }
                    TextButton(onClick = { showSettings = true }) {
                        Text("Settings")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (showDoseInfo) {
            DoseInfoDialog(onDismiss = { showDoseInfo = false })
        }
        
        if (showHelpDialog) {
            HelpInfoDialog(onDismiss = { showHelpDialog = false })
        }

        BoxWithConstraints(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            val isLandscape = maxWidth > maxHeight
            val padding = 16.dp

            if (hasPermission) {
                if (isLandscape) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // Left side (VU Meter + Dose + Insights)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            MonitorTopSection(audioViewModel, noiseDescription, settingsManager, onShowDoseInfo = { showDoseInfo = true })
                        }

                        // Right side (Spectrogram)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            Spectrogram(
                                state = audioViewModel.spectroState,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top section (VU Meter + Dose + Insights)
                        Column(
                            modifier = Modifier.weight(0.5f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            MonitorTopSection(audioViewModel, noiseDescription, settingsManager, onShowDoseInfo = { showDoseInfo = true })
                        }

                        // Spectrogram section (Half height)
                        Box(modifier = Modifier.weight(0.5f)) {
                            Spectrogram(
                                state = audioViewModel.spectroState,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Microphone permission required")
                }
            }
        }
    }
}

@Composable
fun MonitorTopSection(
    audioViewModel: AudioViewModel,
    noiseDescription: String,
    settingsManager: SettingsManager,
    onShowDoseInfo: (() -> Unit)? = null
) {
    Text(
        text = noiseDescription,
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp)
    )
    
    if (Math.abs(audioViewModel.currentDb - audioViewModel.rawDb) > 1.0f) {
        Icon(
            Icons.Default.FilterAltOff,
            contentDescription = "Handling Noise Filtered",
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp).padding(bottom = 4.dp)
        )
    } else {
        Spacer(Modifier.height(18.dp))
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        // Fix: Be more defensive. Only show stereo if hardware IS stereo AND engine reports > 1 channel.
        if (settingsManager.isStereoHardware && audioViewModel.actualChannelCount > 1) {
            StereoVUMeter(
                leftDb = audioViewModel.leftDb,
                rightDb = audioViewModel.rightDb,
                isRejectionActive = audioViewModel.isRejectionActive,
                maxDb = maxOf(100f, audioViewModel.maxDb),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            )
        } else {
            VUMeter(
                db = audioViewModel.currentDb,
                maxDb = maxOf(100f, audioViewModel.maxDb),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )
        }
        
        if (audioViewModel.isSelfNoiseActive) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = "Speaker Active",
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(24.dp)
            )
        }
    }

    Spacer(Modifier.height(24.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Daily Dose: ${(audioViewModel.dailyDose * 100).toInt()}%",
            style = MaterialTheme.typography.headlineMedium,
            color = if (audioViewModel.dailyDose >= 1.0f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
        if (onShowDoseInfo != null) {
            IconButton(onClick = onShowDoseInfo) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "What is this?",
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }

    Spacer(Modifier.height(8.dp))

    Text(
        text = audioViewModel.currentNoiseType,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.secondary
    )
}

@Composable
fun DoseInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What is Daily Dose?") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "This tracks your cumulative noise exposure using the NIOSH standard.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "The budget is based on 85 dB for 8 hours being a 100% dose. For every 3 dB increase, the safe time is cut in half:",
                    style = MaterialTheme.typography.bodySmall
                )
                TableInfo()
                Text(
                    "Exceeding 100% regularly increases risk of permanent hearing damage.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        }
    )
}

@Composable
fun TableInfo() {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("85 dB", Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Text("8 Hours", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("88 dB", Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Text("4 Hours", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("91 dB", Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Text("2 Hours", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("100 dB", Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Text("15 Minutes", Modifier.weight(1f))
        }
    }
}

@Composable
fun HelpInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("App Information & Icons") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "This app monitors environmental noise to protect your hearing.",
                    style = MaterialTheme.typography.bodyMedium
                )

                HorizontalDivider()
                Text("Core Concepts", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                
                HelpSection(
                    title = "Daily Dose",
                    description = "Your cumulative exposure based on NIOSH safety standards. 85dB for 8 hours equals 100%. This is a scientific fixed standard."
                )
                
                HelpSection(
                    title = "Loud Events (History)",
                    description = "Recorded based on your 'Alert Threshold' slider in Settings. This allows you to track specific levels that matter to you."
                )
                
                HelpSection(
                    title = "Calibration",
                    description = "Adjust the dB level to match professional hardware for maximum accuracy across different phones."
                )

                HorizontalDivider()
                Text("Icon Meanings", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

                IconHelpRow(
                    icon = Icons.Default.FilterAltOff,
                    title = "Handling Filter",
                    description = "Appears when mechanical noise (touching the phone) is being removed from your reading.",
                    iconColor = MaterialTheme.colorScheme.outline
                )

                IconHelpRow(
                    icon = Icons.Default.MusicNote,
                    title = "Speaker Active",
                    description = "Indicates the phone is playing its own media, which can affect measurements.",
                    iconColor = MaterialTheme.colorScheme.tertiary
                )

                IconHelpRow(
                    icon = Icons.Default.WbSunny,
                    title = "Daytime Event",
                    description = "Noise recorded between 6:00 AM and 6:00 PM.",
                    iconColor = Color(0xFFFFB300)
                )

                IconHelpRow(
                    icon = Icons.Default.NightsStay,
                    title = "Nighttime Event",
                    description = "Noise recorded between 6:00 PM and 6:00 AM.",
                    iconColor = Color(0xFF9FA8DA)
                )

                IconHelpRow(
                    icon = Icons.Default.LocationOn,
                    title = "Location Tag",
                    description = "Allows you to view where a loud noise event occurred on a map.",
                    iconColor = MaterialTheme.colorScheme.primary
                )

                IconHelpRow(
                    icon = Icons.Default.PlayArrow,
                    title = "Audio Clip",
                    description = "Listen to a recording of the loud noise event in your history.",
                    iconColor = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun HelpSection(title: String, description: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun IconHelpRow(icon: ImageVector, title: String, description: String, iconColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
        HelpSection(title, description)
    }
}
