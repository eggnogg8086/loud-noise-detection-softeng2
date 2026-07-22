package com.loudnoisedetectionapp

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun MicSetupScreen(
    onFinished: () -> Unit
) {
    val viewModel: MicDiscoveryViewModel = viewModel()
    val context = LocalContext.current
    val state = viewModel.state

    LaunchedEffect(Unit) {
        viewModel.startDiscovery(context)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (state) {
                is SetupState.Idle, SetupState.Searching -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Searching for audio hardware...")
                }
                is SetupState.Testing -> {
                    HardwareDiagnosticUI(state)
                }
                is SetupState.ReadyForCalibration -> {
                    CalibrationPromptUI(state) { viewModel.startCalibration(context) }
                }
                SetupState.Calibrating -> {
                    CalibrationUI()
                }
                is SetupState.Finished -> {
                    SetupFinishedUI(state, onFinished)
                }
            }
        }
    }
}

@Composable
fun HardwareDiagnosticUI(state: SetupState.Testing) {
    Text(
        text = "Hardware Diagnostic",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Testing: ${state.micName}",
        color = MaterialTheme.colorScheme.secondary
    )
    Text(
        text = "Preset: ${state.preset}",
        style = MaterialTheme.typography.bodySmall
    )
    
    Spacer(modifier = Modifier.height(32.dp))
    
    LinearProgressIndicator(
        progress = { state.progress },
        modifier = Modifier.fillMaxWidth().height(8.dp)
    )
    
    Spacer(modifier = Modifier.height(16.dp))
    Text("Checking stereo separation...", style = MaterialTheme.typography.bodySmall)
}

@Composable
fun CalibrationPromptUI(state: SetupState.ReadyForCalibration, onStart: () -> Unit) {
    Icon(
        imageVector = if (state.isStereo) Icons.Default.CheckCircle else Icons.Default.Warning,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = if (state.isStereo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    )
    
    Spacer(modifier = Modifier.height(24.dp))
    
    Text(
        text = if (state.isStereo) "Hardware Ready (Stereo)" else "Hardware Ready (Mono)",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )
    
    if (!state.isStereo) {
        Text(
            text = "Stereo not detected. Noise rejection will be limited.",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp)
        )
    }

    Spacer(modifier = Modifier.height(32.dp))
    
    Text(
        text = "Next, we need to calibrate the noise floor. Please find a quiet environment, then press start.",
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    
    Spacer(modifier = Modifier.height(16.dp))
    
    Button(
        onClick = onStart,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Start Calibration")
    }
}

@Composable
fun CalibrationUI() {
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Icon(
        imageVector = Icons.Default.CheckCircle,
        contentDescription = null,
        modifier = Modifier.size(64.dp).scale(scale),
        tint = MaterialTheme.colorScheme.primary
    )
    
    Spacer(modifier = Modifier.height(24.dp))
    
    Text(
        text = "Calibrating Noise Floor",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )
    Text(
        text = "Please stay quiet for a few seconds...",
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
fun SetupFinishedUI(state: SetupState.Finished, onFinished: () -> Unit) {
    Icon(
        imageVector = Icons.Default.CheckCircle,
        contentDescription = null,
        modifier = Modifier.size(80.dp),
        tint = MaterialTheme.colorScheme.primary
    )
    
    Spacer(modifier = Modifier.height(24.dp))
    
    Text(
        text = "Setup Complete!",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold
    )
    
    Spacer(modifier = Modifier.height(16.dp))
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Hardware: ${state.micName}", style = MaterialTheme.typography.bodyMedium)
            Text("Mode: ${if (state.isStereo) "Stereo" else "Mono"}", style = MaterialTheme.typography.bodyMedium)
            Text("Noise Floor: ${state.noiseFloor.toInt()} dB", style = MaterialTheme.typography.bodyMedium)
        }
    }
    
    Spacer(modifier = Modifier.height(32.dp))
    
    Button(onClick = onFinished, modifier = Modifier.fillMaxWidth()) {
        Text("Continue to Monitor")
    }
}
