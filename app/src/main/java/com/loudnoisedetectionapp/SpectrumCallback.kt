package com.loudnoisedetectionapp

import androidx.annotation.Keep

@Keep
interface SpectrumCallback {
    fun onSpectrum(
        spectrum: FloatArray,
        db: Float,
        dbRaw: Float,
        dbAlert: Float,
        dbL: Float,
        dbR: Float,
        balance: Float,
        rejectionActive: Boolean,
        channelCount: Int
    )
}
