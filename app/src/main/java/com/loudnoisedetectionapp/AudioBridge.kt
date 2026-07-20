package com.loudnoisedetectionapp

import java.util.Collections

object AudioBridge {
    @Volatile
    var currentDose = 0f
    
    @Volatile
    var movementIntensity = 0f

    @Volatile
    var isSelfNoiseActive = false

    init {
        System.loadLibrary("noisecapture")
    }

    fun interface SpectrumCallback {
        fun onSpectrum(spectrum: FloatArray, db: Float, balance: Float)
    }

    private val callbacks = Collections.synchronizedSet(mutableSetOf<SpectrumCallback>())
    private var isInitialized = false

    private external fun nativeInit(callback: SpectrumCallback)
    private external fun nativeStart(
        deviceId: Int,
        inputPreset: Int,
        sensitivity: Float,
        freqs: FloatArray?,
        gains: FloatArray?,
        useAWeighting: Boolean
    ): Boolean
    private external fun nativeStop()
    private external fun nativeDestroy()
    private external fun nativeGetSessionId(): Int
    private external fun nativeStartRecording()
    private external fun nativeGetSnippet(path: String): Boolean

    private val internalCallback = SpectrumCallback { spectrum, db, balance ->
        val currentCallbacks = synchronized(callbacks) {
            callbacks.toList()
        }
        currentCallbacks.forEach { it.onSpectrum(spectrum, db, balance) }
    }

    fun addCallback(callback: SpectrumCallback) {
        callbacks.add(callback)
        if (!isInitialized) {
            nativeInit(internalCallback)
            isInitialized = true
        }
    }

    fun removeCallback(callback: SpectrumCallback) {
        callbacks.remove(callback)
    }

    fun start(
        deviceId: Int = -1,
        inputPreset: Int = 9,
        sensitivity: Float = -999f,
        freqs: FloatArray? = null,
        gains: FloatArray? = null,
        useAWeighting: Boolean = true
    ) = nativeStart(deviceId, inputPreset, sensitivity, freqs, gains, useAWeighting)

    fun startRecording() = nativeStartRecording()
    fun saveSnippet(path: String) = nativeGetSnippet(path)
    fun getSessionId() = nativeGetSessionId()

    fun stop() = nativeStop()
    
    fun destroy() {
        if (callbacks.isEmpty()) {
            nativeDestroy()
            isInitialized = false
        }
    }
}
