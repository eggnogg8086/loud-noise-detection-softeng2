package com.loudnoisedetectionapp

import java.util.Collections

object AudioBridge {
    @Volatile
    var currentDose = 0f
    
    var movementIntensity = 0f
        set(value) {
            field = value
            nativeSetMovementIntensity(value)
        }

    @Volatile
    var isSelfNoiseActive = false

    const val FFT_SIZE = 8192
    const val SAMPLE_RATE = 44100
    const val BUFFER_DURATION = FFT_SIZE.toDouble() / SAMPLE_RATE

    init {
        System.loadLibrary("noisecapture")
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
        useAWeighting: Boolean,
        useNoiseRejection: Boolean,
        calibrationOffset: Float,
        integrationTime: Int
    ): Boolean
    private external fun nativeStop()
    private external fun nativeDestroy()
    private external fun nativeGetSessionId(): Int
    private external fun nativeStartRecording()
    private external fun nativeSetMovementIntensity(intensity: Float)
    private external fun nativeGetSnippet(path: String): Boolean

    private class BridgeCallback : SpectrumCallback {
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
            val currentCallbacks = synchronized(callbacks) {
                callbacks.toList()
            }
            currentCallbacks.forEach {
                it.onSpectrum(
                    spectrum,
                    db,
                    dbRaw,
                    dbAlert,
                    dbL,
                    dbR,
                    balance,
                    rejectionActive,
                    channelCount
                )
            }
        }
    }

    private val internalCallback = BridgeCallback()

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
        useAWeighting: Boolean = true,
        useNoiseRejection: Boolean = true,
        calibrationOffset: Float = 0f,
        integrationTime: Int = 0
    ) = nativeStart(
        deviceId,
        inputPreset,
        sensitivity,
        freqs,
        gains,
        useAWeighting,
        useNoiseRejection,
        calibrationOffset,
        integrationTime
    )

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
