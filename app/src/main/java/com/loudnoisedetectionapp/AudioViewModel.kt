package com.loudnoisedetectionapp

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
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

    private var calibrationManager: CalibrationManager? = null
    private var exposureManager: ExposureManager? = null

    fun initCalibration(context: Context) {
        calibrationManager = CalibrationManager(context)
        maxDb = calibrationManager?.maxRecordedDb ?: 100f
        exposureManager = ExposureManager(context)
        dailyDose = exposureManager?.getCurrentDose() ?: 0f
    }

    val spectroState = SpectrogramState().apply {
        timeStep    = BUFFER_DURATION
        pixelsPerTic = 2
    }

    private val callback = AudioBridge.SpectrumCallback { spectrum, db ->
        viewModelScope.launch(Dispatchers.Main) {
            currentDb = db
            if (db > maxDb) {
                maxDb = db
                calibrationManager?.maxRecordedDb = db
            }
            dailyDose = exposureManager?.getCurrentDose() ?: 0f

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
        println("CREATED ${hashCode()}")
        AudioBridge.addCallback(callback)
    }

    override fun onCleared() {
        println("CLEARED ${hashCode()}")
        exposureManager?.persist()
        super.onCleared()
        AudioBridge.removeCallback(callback)
    }
}
