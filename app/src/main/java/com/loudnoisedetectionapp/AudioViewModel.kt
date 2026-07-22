package com.loudnoisedetectionapp

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
    
    var rawDb by mutableFloatStateOf(-100f)
        private set
    
    var maxDb by mutableFloatStateOf(100f)
        private set

    var dailyDose by mutableFloatStateOf(0f)
        private set

    var leftDb by mutableFloatStateOf(0f)
        private set

    var rightDb by mutableFloatStateOf(0f)
        private set

    var isRejectionActive by mutableStateOf(false)
        private set

    var currentNoiseType by mutableStateOf("Ambient")
        private set

    var isSelfNoiseActive by mutableStateOf(false)
        private set

    var stereoBalance by mutableFloatStateOf(0f)
        private set

    var isStereoSupported by mutableStateOf(false)
        private set

    var actualChannelCount by mutableIntStateOf(1)
        private set

    private var calibrationManager: CalibrationManager? = null
    private var settingsManager: SettingsManager? = null

    fun initCalibration(context: Context) {
        calibrationManager = CalibrationManager(context)
        settingsManager = SettingsManager(context)

        maxDb = calibrationManager?.maxRecordedDb ?: 100f
        dailyDose = settingsManager?.dailyDose ?: 0f

        // Check for stereo support
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val devices = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_INPUTS)
        isStereoSupported = devices.any { device -> 
            device.channelCounts.contains(2)
        }
    }

    val spectroState = SpectrogramState().apply {
        timeStep    = BUFFER_DURATION
        pixelsPerTic = 2
    }

    private var lastAnnotationTime = 0L
    private val ANNOTATION_THROTTLE_MS = 2000L

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
            val now = System.currentTimeMillis()
            viewModelScope.launch(Dispatchers.Main) {
                currentDb = db
                rawDb = dbRaw
                leftDb = dbL
                rightDb = dbR
                isRejectionActive = rejectionActive
                isSelfNoiseActive = AudioBridge.isSelfNoiseActive
                stereoBalance = balance
                actualChannelCount = channelCount
                if (dbAlert > maxDb) {
                    maxDb = dbAlert
                    calibrationManager?.maxRecordedDb = dbAlert
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

                val threshold = settingsManager?.thresholdDb ?: SettingsManager.DEFAULT_THRESHOLD
                if (dbAlert > threshold && (now - lastAnnotationTime > ANNOTATION_THROTTLE_MS)) {
                    val isHandling = AudioBridge.movementIntensity > 0.8f
                    val color = if (isHandling) Color.Gray else Color.Yellow
                    val prefix = if (isHandling) "HANDLING" else "LOUD"
                    spectroState.addAnnotation("$prefix: ${dbAlert.toInt()}dB @ ${domFreq.toInt()}Hz", color)
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
    }

    fun resetCalibration() {
        calibrationManager?.reset()
        maxDb = 100f
    }

    fun resetDose() {
        dailyDose = 0f
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
