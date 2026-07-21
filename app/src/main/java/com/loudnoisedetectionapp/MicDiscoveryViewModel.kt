package com.loudnoisedetectionapp

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

sealed class SetupState {
    object Idle : SetupState()
    object Searching : SetupState()
    data class Testing(val micName: String, val preset: String, val progress: Float) : SetupState()
    data class ReadyForCalibration(
        val isStereo: Boolean,
        val micName: String,
        val presetName: String
    ) : SetupState()
    object Calibrating : SetupState()
    data class Finished(
        val isStereo: Boolean,
        val micName: String,
        val presetName: String,
        val noiseFloor: Float
    ) : SetupState()
}

class MicDiscoveryViewModel : ViewModel() {
    var state by mutableStateOf<SetupState>(SetupState.Idle)
        private set

    private data class TestResult(
        val hasAudio: Boolean,
        val isStereo: Boolean,
        val avgDb: Float
    )

    private var balanceSamples = mutableListOf<Float>()
    private var dbSamples = mutableListOf<Float>()

    private var discoveredMicId = -1
    private var discoveredPreset = SettingsManager.PRESET_UNPROCESSED
    private var discoveredMicName = ""
    private var discoveredIsStereo = false

    private val callback = object : SpectrumCallback {
        override fun onSpectrum(
            spectrum: FloatArray,
            db: Float,
            dbRaw: Float,
            dbAlert: Float,
            dbL: Float,
            dbR: Float,
            balance: Float,
            rejectionActive: Boolean,
            channelCount: Int
        ) {
            balanceSamples.add(balance)
            dbSamples.add(db)
        }
    }

    fun startDiscovery(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            state = SetupState.Searching
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            // Check if UNPROCESSED is officially supported
            val unprocessedSupported = audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
            android.util.Log.d("MicDiscovery", "PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED: $unprocessedSupported")

            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            
            val builtinMic = devices.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
            discoveredMicId = builtinMic?.id ?: -1
            discoveredMicName = builtinMic?.productName?.toString() ?: "Default Mic"

            // Strategy: Prioritize UNPROCESSED (Preset 9) even if Mono.
            // Only fall back to CAMCORDER if UNPROCESSED is dead or silent.
            
            val resultUnprocessed = runHardwareTest(discoveredMicId, SettingsManager.PRESET_UNPROCESSED, "Unprocessed")
            
            if (resultUnprocessed.hasAudio) {
                discoveredPreset = SettingsManager.PRESET_UNPROCESSED
                discoveredIsStereo = resultUnprocessed.isStereo
                
                if (resultUnprocessed.avgDb > 80f) {
                    android.util.Log.w("MicDiscovery", "Warning: High noise floor (${resultUnprocessed.avgDb}) on Unprocessed. Potential hardware AGC.")
                }
            } else {
                // Fallback to Camcorder if Unprocessed failed
                val resultCamcorder = runHardwareTest(discoveredMicId, SettingsManager.PRESET_CAMCORDER, "Camcorder")
                if (resultCamcorder.hasAudio) {
                    discoveredPreset = SettingsManager.PRESET_CAMCORDER
                    discoveredIsStereo = resultCamcorder.isStereo
                }
            }

            state = SetupState.ReadyForCalibration(
                isStereo = discoveredIsStereo,
                micName = discoveredMicName,
                presetName = if (discoveredPreset == SettingsManager.PRESET_UNPROCESSED) "Unprocessed" else "Camcorder"
            )
        }
    }

    fun startCalibration(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val settings = SettingsManager(context)
            
            // Phase 3: Calibrate Noise Floor
            state = SetupState.Calibrating
            dbSamples.clear()
            AudioBridge.addCallback(callback)
            AudioBridge.start(discoveredMicId, discoveredPreset)
            delay(3000)
            AudioBridge.stop()
            AudioBridge.removeCallback(callback)
            
            val noiseFloor = if (dbSamples.isNotEmpty()) dbSamples.average().toFloat() else 30f

            // Save Results
            settings.selectedMicId = discoveredMicId
            settings.audioPreset = discoveredPreset
            settings.noiseFloorDb = noiseFloor
            settings.micSetupCompleted = true

            state = SetupState.Finished(
                isStereo = discoveredIsStereo,
                micName = discoveredMicName,
                presetName = if (discoveredPreset == SettingsManager.PRESET_UNPROCESSED) "Unprocessed" else "Camcorder",
                noiseFloor = noiseFloor
            )
        }
    }

    private suspend fun runHardwareTest(micId: Int, preset: Int, presetName: String): TestResult {
        balanceSamples.clear()
        dbSamples.clear()
        state = SetupState.Testing("Built-in Mic", presetName, 0f)
        
        AudioBridge.addCallback(callback)
        AudioBridge.start(micId, preset)
        
        // Accumulate samples for 1.5 seconds (shorter for discovery)
        for (i in 1..15) {
            delay(100)
            state = SetupState.Testing("Built-in Mic", presetName, i / 15f)
        }
        
        AudioBridge.stop()
        AudioBridge.removeCallback(callback)

        val nonZeroSamples = balanceSamples.filter { abs(it) > 0.0001f }
        val isStereo = nonZeroSamples.size > (balanceSamples.size * 0.3)
        val avgDb = if (dbSamples.isNotEmpty()) dbSamples.average().toFloat() else 0f
        
        // We consider it working if we got samples and the volume is > 5dB (not total silence)
        val hasAudio = dbSamples.isNotEmpty() && avgDb > 5f

        return TestResult(hasAudio, isStereo, avgDb)
    }
}
