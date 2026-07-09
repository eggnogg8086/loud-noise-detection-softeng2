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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoudNoiseDetectorScreen(
    modifier: Modifier = Modifier,
    audioViewModel: AudioViewModel = AudioViewModel()
) {
    var hasPermission by remember { mutableStateOf(false) }
    var showBatteryPrompt by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

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
                title = { Text("Noise Detector") },
                actions = {
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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (hasPermission) {
                VUMeter(
                    db = audioViewModel.currentDb,
                    maxDb = maxOf(100f, audioViewModel.maxDb),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

                Spectrogram(
                    state = audioViewModel.spectroState,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    "Microphone permission required",
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}