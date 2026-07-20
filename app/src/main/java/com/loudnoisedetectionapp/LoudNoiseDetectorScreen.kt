package com.loudnoisedetectionapp

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoudNoiseDetectorScreen(
    modifier: Modifier = Modifier,
    audioViewModel: AudioViewModel = viewModel()
) {
    var hasPermission by remember { mutableStateOf(false) }
    var showBatteryPrompt by remember { mutableStateOf(false) }
    var showDoseInfo by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }

    val context = LocalContext.current

    if (showSettings) {
        BackHandler {
            showSettings = false
        }
        SettingsScreen(
            onBack = { showSettings = false },
            audioViewModel = audioViewModel
        )
        return
    }

    if (showHistory) {
        BackHandler {
            showHistory = false
        }
        NoiseHistoryScreen(onBack = { showHistory = false })
        return
    }

    LaunchedEffect(Unit) {
        audioViewModel.initCalibration(context)
    }

    val checkBatteryOptimization = {
        val pm = context.getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
            showBatteryPrompt = true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val locGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val notificationGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
        } else {
            true
        }

        hasPermission = audioGranted

        if (audioGranted) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AudioMonitorService::class.java)
            )
            checkBatteryOptimization()
        }
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

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
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
                title = { Text("Noise Monitor") },
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
        
        if (showBatteryPrompt) {
            AlertDialog(
                onDismissRequest = { showBatteryPrompt = false },
                title = { Text("Disable Battery Optimization") },
                text = { Text("To ensure reliable background monitoring, please set battery usage to 'Unrestricted' for this app.") },
                confirmButton = {
                    TextButton(onClick = {
                        showBatteryPrompt = false
                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        context.startActivity(intent)
                    }) {
                        Text("Open Settings")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBatteryPrompt = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (hasPermission) {
                // Top section (VU Meter + Dose + Insights)
                Column(
                    modifier = Modifier.weight(0.5f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = noiseDescription,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VUMeter(
                            db = audioViewModel.currentDb,
                            maxDb = maxOf(100f, audioViewModel.maxDb),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )
                        if (audioViewModel.isSelfNoiseActive) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = "Speaker Active",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    if (audioViewModel.stereoBalance != 0f) {
                        StereoBalanceMeter(
                            balance = audioViewModel.stereoBalance,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp, vertical = 8.dp)
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Daily Dose: ${(audioViewModel.dailyDose * 100).toInt()}%",
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (audioViewModel.dailyDose >= 1.0f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { showDoseInfo = true }) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "What is this?",
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = audioViewModel.currentNoiseType,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                // Spectrogram section (Half height)
                Box(modifier = Modifier.weight(0.5f)) {
                    Spectrogram(
                        state = audioViewModel.spectroState,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Text(
                    "Microphone permission required",
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
fun StereoBalanceMeter(balance: Float, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("L", style = MaterialTheme.typography.labelSmall)
            Text("Balance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Text("R", style = MaterialTheme.typography.labelSmall)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.extraSmall)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.1f)
                    .align(if (balance < 0) Alignment.CenterStart else Alignment.CenterEnd)
                    .offset(x = (balance * 50).dp) // Visualization simplification
                    .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall)
            )
        }
    }
}

@Composable
fun DoseInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What is Daily Dose?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
