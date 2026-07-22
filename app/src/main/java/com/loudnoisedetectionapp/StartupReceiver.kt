package com.loudnoisedetectionapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

class StartupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("StartupReceiver", "Received broadcast: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val settings = SettingsManager(context)
            
            // Only start if the user has actually finished the setup process
            if (settings.micSetupCompleted) {
                val serviceIntent = Intent(context, AudioMonitorService::class.java)
                try {
                    ContextCompat.startForegroundService(context, serviceIntent)
                    Log.i("StartupReceiver", "AudioMonitorService restarted successfully after $action")
                } catch (e: Exception) {
                    Log.e("StartupReceiver", "Failed to restart service after update/boot", e)
                }
            } else {
                Log.d("StartupReceiver", "Setup not complete, skipping auto-start")
            }
        }
    }
}
