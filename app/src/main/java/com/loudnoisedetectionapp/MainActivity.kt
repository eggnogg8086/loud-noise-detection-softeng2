package com.loudnoisedetectionapp

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.loudnoisedetectionapp.ui.theme.LoudNoiseDetectionAppTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val settingsManager = SettingsManager(this)

        enableEdgeToEdge()
        setContent {
            LoudNoiseDetectionAppTheme {
                var onboardingCompleted by remember { mutableStateOf(settingsManager.onboardingCompleted) }
                var micSetupCompleted by remember { mutableStateOf(settingsManager.micSetupCompleted) }
                
                var hasPermissions by remember { mutableStateOf(false) }

                val batteryLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) {
                    // Finally, finish onboarding sequence
                    onboardingCompleted = true
                    settingsManager.onboardingCompleted = true
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
                    hasPermissions = audioGranted
                    
                    // Move to the next step in the "cluster": Battery Optimization
                    val pm = getSystemService(POWER_SERVICE) as PowerManager
                    if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        batteryLauncher.launch(intent)
                    } else {
                        // Already unrestricted, finish onboarding
                        onboardingCompleted = true
                        settingsManager.onboardingCompleted = true
                    }
                }

                when {
                    !onboardingCompleted -> {
                        OnboardingScreen(onGetStarted = {
                            val permissions = mutableListOf(
                                Manifest.permission.RECORD_AUDIO,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            permissionLauncher.launch(permissions.toTypedArray())
                        })
                    }
                    !micSetupCompleted -> {
                        MicSetupScreen(onFinished = {
                            micSetupCompleted = true
                            settingsManager.micSetupCompleted = true
                            
                            // Start Service now that setup is done
                            ContextCompat.startForegroundService(
                                this,
                                Intent(this, AudioMonitorService::class.java)
                            )
                        })
                    }
                    else -> {
                        LoudNoiseDetectorScreen(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}
