package com.loudnoisedetectionapp

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
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

                    VUMeter(
                        db = audioViewModel.currentDb,
                        maxDb = maxOf(100f, audioViewModel.maxDb),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    )

                    Spacer(Modifier.height(24.dp))

                    Text(
                        text = "Daily Dose: ${(audioViewModel.dailyDose * 100).toInt()}%",
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (audioViewModel.dailyDose >= 1.0f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )

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