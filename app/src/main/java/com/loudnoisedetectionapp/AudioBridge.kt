package com.loudnoisedetectionapp

import java.util.Collections

object AudioBridge {
    init {
        System.loadLibrary("noisecapture")
    }

    fun interface SpectrumCallback {
        fun onSpectrum(spectrum: FloatArray, db: Float)
    }

    private val callbacks = Collections.synchronizedSet(mutableSetOf<SpectrumCallback>())
    private var isInitialized = false

    private external fun nativeInit(callback: SpectrumCallback)
    private external fun nativeStart(deviceId: Int, inputPreset: Int): Boolean
    private external fun nativeStop()
    private external fun nativeDestroy()

    private val internalCallback = SpectrumCallback { spectrum, db ->
        val currentCallbacks = synchronized(callbacks) {
            callbacks.toList()
        }
        currentCallbacks.forEach { it.onSpectrum(spectrum, db) }
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

    fun start(deviceId: Int = -1, inputPreset: Int = 9) = nativeStart(deviceId, inputPreset)
    fun stop() = nativeStop()
    
    fun destroy() {
        if (callbacks.isEmpty()) {
            nativeDestroy()
            isInitialized = false
        }
    }
}
