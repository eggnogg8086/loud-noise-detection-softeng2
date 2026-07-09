package com.loudnoisedetectionapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.loudnoisedetectionapp.ui.theme.LoudNoiseDetectionAppTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LoudNoiseDetectionAppTheme {
                LoudNoiseDetectorScreen(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
