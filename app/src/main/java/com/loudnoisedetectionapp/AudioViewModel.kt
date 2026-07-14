package com.loudnoisedetectionapp

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AudioViewModel : ViewModel() {

    companion object {
        const val SAMPLE_RATE  = 44100
        const val FFT_SIZE     = 8192
        val HERTZ_PER_CELL     = SAMPLE_RATE.toDouble() / FFT_SIZE
        val BUFFER_DURATION    = FFT_SIZE.toDouble() / SAMPLE_RATE
    }

    var currentDb by mutableFloatStateOf(-100f)
        private set
    
    var maxDb by mutableFloatStateOf(100f)
        private set

    var dailyDose by mutableFloatStateOf(0f)
        private set

    var currentNoiseType by mutableStateOf("Ambient")
        private set

    private var calibrationManager: CalibrationManager? = null
    private var settingsManager: SettingsManager? = null

    fun initCalibration(context: Context) {
        calibrationManager = CalibrationManager(context)
        settingsManager = SettingsManager(context)

        maxDb = calibrationManager?.maxRecordedDb ?: 100f
        dailyDose = settingsManager?.dailyDose ?: 0f
    }

    val spectroState = SpectrogramState().apply {
        timeStep    = BUFFER_DURATION
        pixelsPerTic = 2
    }

    private var lastAnnotationTime = 0L
    private val ANNOTATION_THROTTLE_MS = 2000L

    private val callback = AudioBridge.SpectrumCallback { spectrum, db ->
        val now = System.currentTimeMillis()
        viewModelScope.launch(Dispatchers.Main) {
            currentDb = db
            if (db > maxDb) {
                maxDb = db
                calibrationManager?.maxRecordedDb = db
            }
            dailyDose = AudioBridge.currentDose

            var maxVal = -1f
            var maxIdx = 0
            for (i in spectrum.indices) {
                if (spectrum[i] > maxVal) {
                    maxVal = spectrum[i]
                    maxIdx = i
                }
            }
            val domFreq = (maxIdx * HERTZ_PER_CELL).toFloat()
            spectroState.dominantFrequency = domFreq

            val threshold = settingsManager?.thresholdDb ?: 82f
            if (db > threshold && (now - lastAnnotationTime > ANNOTATION_THROTTLE_MS)) {
                val isHandling = AudioBridge.movementIntensity > 0.8f
                val color = if (isHandling) Color.Gray else Color.Yellow
                val prefix = if (isHandling) "HANDLING" else "LOUD"
                spectroState.addAnnotation("$prefix: ${db.toInt()}dB @ ${domFreq.toInt()}Hz", color)
                lastAnnotationTime = now
            }

            currentNoiseType = when {
                domFreq < 250 -> "Low Frequency (Rumble/Bass)"
                domFreq < 2000 -> "Mid Frequency (Voice/Traffic)"
                domFreq < 8000 -> "High Frequency (Whistle/Squeak)"
                else -> "Ultrasonic/Electronic Noise"
            }

            val doubles = DoubleArray(spectrum.size) { spectrum[it].toDouble() }
            spectroState.addTimeStep(doubles, HERTZ_PER_CELL)
        }
    }

    fun resetCalibration() {
        calibrationManager?.reset()
        maxDb = 100f
    }

    init {
        // We just attach to the already running (or about to run) bridge
        AudioBridge.addCallback(callback)
    }

    override fun onCleared() {
        super.onCleared()
        AudioBridge.removeCallback(callback)
    }
}
