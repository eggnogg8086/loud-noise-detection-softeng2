package com.loudnoisedetectionapp

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import kotlin.math.*

private val COLOR_RAMP: List<Color> = listOf(
    Color(0xFF303030), Color(0xFF2D3C2D), Color(0xFF2A482A), Color(0xFF275427),
    Color(0xFF246024), Color(0xFF216C21), Color(0xFF3F8E19), Color(0xFF61A514),
    Color(0xFF82BB0F), Color(0xFFA4D20A), Color(0xFFC5E805), Color(0xFFE7FF00),
    Color(0xFFEBD400), Color(0xFFEFAA00), Color(0xFFF37F00), Color(0xFFF75500),
    Color(0xFFFB2A00),
)

private fun getColor(value: Float, min: Float = 0f, max: Float = 70f): Color {
    val index = ((value - min) / (max - min) * COLOR_RAMP.size)
        .toInt().coerceIn(0, COLOR_RAMP.size - 1)
    return COLOR_RAMP[index]
}

enum class SpectrogramScaleMode { LOG, LINEAR }

private const val FREQ_LEGEND_TIC_WIDTH_PX = 4
private const val TIME_LEGEND_TIC_HEIGHT_PX = 4
private const val FREQ_LEGEND_TEXT_SIZE_SP = 11f

private val FREQ_POSITIONS_LOG    = intArrayOf(63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
private val FREQ_POSITIONS_LINEAR = intArrayOf(0, 1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000, 10000, 12500, 16000)

// ---------------------------------------------------------------------------
// A column owns its bitmap. When it falls off the buffer it is recycled.
// ---------------------------------------------------------------------------
class Column(val spectrum: DoubleArray) {
    var bitmap: Bitmap? = null

    fun ensureBitmap(
        hertzPerCell: Double,
        fmin: Double,
        r: Double,
        height: Int,
        ticW: Int,
        scaleMode: SpectrogramScaleMode,
    ): Bitmap {
        val bmp = bitmap
        if (bmp != null && !bmp.isRecycled && bmp.width == ticW && bmp.height == height) {
            return bmp
        }
        bmp?.recycle()
        val pixels = IntArray(height * ticW)
        for (pixel in 0 until height) {
            val freqStart: Int
            val freqEnd: Int
            if (scaleMode == SpectrogramScaleMode.LOG) {
                val fLow  = fmin * 10.0.pow(pixel.toDouble() * log10(r) / height)
                val fHigh = fmin * 10.0.pow((pixel + 1.0)   * log10(r) / height)
                freqStart = (fLow  / hertzPerCell).toInt().coerceIn(0, spectrum.size)
                freqEnd   = ((fHigh / hertzPerCell).toInt() + 1).coerceAtMost(spectrum.size)
            } else {
                val freqByPixel = spectrum.size / height.toDouble()
                freqStart = (pixel * freqByPixel).toInt()
                freqEnd   = (pixel * freqByPixel + freqByPixel).toInt().coerceAtMost(spectrum.size)
            }
            var sumVal = 0.0
            for (fi in freqStart until freqEnd) sumVal += 10.0.pow(spectrum[fi] / 10.0)
            val db = if (sumVal > 0) (10 * log10(sumVal)).toFloat() else -1f
            val argb = getColor(db).toArgb()
            for (x in 0 until ticW) pixels[(height - 1 - pixel) * ticW + x] = argb
        }
        return Bitmap.createBitmap(pixels, ticW, height, Bitmap.Config.ARGB_8888)
            .also { bitmap = it }
    }

    fun recycle() {
        bitmap?.recycle()
        bitmap = null
    }
}

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------
class SpectrogramState {
    var scaleMode: SpectrogramScaleMode by mutableStateOf(SpectrogramScaleMode.LOG)
    var timeStep: Double = 0.125
    var pixelsPerTic: Int by mutableIntStateOf(4)
    var hertzPerCell: Double by mutableDoubleStateOf(0.0)

    // Compose-observable count — Canvas reads this to know when to redraw
    var frameCount  by mutableIntStateOf(0)
        private set

    var windowSeconds: Double = 10.0

    private val maxBufferTics get() = (windowSeconds / timeStep).toInt().coerceAtLeast(10)    // Internal ring buffer of Column objects — not directly observed by Compose
    private val columns = ArrayDeque<Column>()

    fun addTimeStep(spectrum: DoubleArray, hertzBySpectrumCell: Double) {
        hertzPerCell = hertzBySpectrumCell
        columns.addFirst(Column(spectrum))
        while (columns.size > maxBufferTics) {
            columns.removeLast().recycle()
        }
        frameCount++
    }

    // Called by Canvas to iterate columns — returns a stable snapshot view
    fun getColumns(): List<Column> = columns.toList()

    fun invalidateBitmaps() {
        columns.forEach { it.recycle() }
    }

    fun destroy() {
        columns.forEach { it.recycle() }
        columns.clear()
        frameCount = 0
    }
}

private fun formatFrequency(hz: Int): String =
    if (hz >= 1000) "${"%.2f".format(hz / 1000.0).trimEnd('0').trimEnd('.')} kHz"
    else "$hz Hz"

// ---------------------------------------------------------------------------
// Composable
// ---------------------------------------------------------------------------
@OptIn(ExperimentalTextApi::class)
@Composable
fun Spectrogram(
    state: SpectrogramState,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    val textMeasurer = rememberTextMeasurer()

    // Reading columnCount here is what subscribes Canvas to redraws
    val count = state.frameCount

    // Track last known render params — if they change, invalidate bitmaps
    var lastHeight by remember { mutableIntStateOf(-1) }
    var lastTicW   by remember { mutableIntStateOf(-1) }
    var lastScale  by remember { mutableStateOf(state.scaleMode) }

    Canvas(modifier = modifier) {
        val canvasW = size.width
        val canvasH = size.height
        if (canvasW <= 0f || canvasH <= 0f || count == 0) {
            drawRect(color = COLOR_RAMP[0], size = size)
            return@Canvas
        }

        val ticW = state.pixelsPerTic
        val freqPositions = if (state.scaleMode == SpectrogramScaleMode.LOG)
            FREQ_POSITIONS_LOG else FREQ_POSITIONS_LINEAR

        val textStyle = TextStyle(
            color = Color.White,
            fontSize = FREQ_LEGEND_TEXT_SIZE_SP.sp,
            fontFamily = FontFamily.Monospace,
        )
        val labels = freqPositions.map { formatFrequency(it) }
        val labelLayouts = labels.map { textMeasurer.measure(it, textStyle) }
        val maxLabelWidth = labelLayouts.maxOf { it.size.width }.toFloat()
        val legendW = maxLabelWidth + FREQ_LEGEND_TIC_WIDTH_PX * 2

        val sampleTimeLabel = textMeasurer.measure("+SS.d", textStyle)
        val timeLegendH = (sampleTimeLabel.size.height + TIME_LEGEND_TIC_HEIGHT_PX).toFloat()

        val spectroW = canvasW - legendW
        val spectroH = canvasH - timeLegendH
        if (spectroW <= 0 || spectroH <= 0) return@Canvas
        val height = spectroH.toInt()

        // Invalidate bitmaps if render params changed
        if (height != lastHeight || ticW != lastTicW || state.scaleMode != lastScale) {
            state.invalidateBitmaps()
            lastHeight = height
            lastTicW   = ticW
            lastScale  = state.scaleMode
        }

        drawRect(color = COLOR_RAMP[0], size = size)

        if (state.hertzPerCell > 0) {
            val hertzPerCell = state.hertzPerCell
            val columns = state.getColumns()
            val fmax = if (columns.isNotEmpty()) columns[0].spectrum.size * hertzPerCell else return@Canvas
            val fmin = freqPositions[0].toDouble()
            val r = fmax / fmin

            val bufferSize = columns.size.coerceAtLeast(1)
            val scaledTicW = spectroW / bufferSize.toFloat()

            columns.forEachIndexed { ticIndex, col ->
                val leftPx = spectroW - ((ticIndex + 1) * scaledTicW)
                if (leftPx + scaledTicW < 0) return@forEachIndexed

                val bmp = col.ensureBitmap(hertzPerCell, fmin, r, height, ticW, state.scaleMode)

                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawBitmap(
                        bmp,
                        null,
                        android.graphics.RectF(leftPx, 0f, leftPx + scaledTicW, spectroH),
                        null
                    )
                }
            }
        }

        // Frequency legend
        if (state.hertzPerCell > 0) {
            val cols = state.getColumns()
            if (cols.isNotEmpty()) {
                val fmax = cols[0].spectrum.size * state.hertzPerCell
                val fmin = freqPositions[0].toDouble()
                val r = fmax / fmin

                freqPositions.forEachIndexed { i, freq ->
                    val tickY = if (state.scaleMode == SpectrogramScaleMode.LOG) {
                        (spectroH - spectroH * ln(freq / fmin) / ln(r)).toFloat()
                    } else {
                        val cellByPx = cols[0].spectrum.size / spectroH.toDouble()
                        (spectroH - freq / (cellByPx * state.hertzPerCell)).toFloat()
                    }.coerceIn(0f, spectroH - 1f)

                    drawLine(Color.White, Offset(spectroW, tickY), Offset(spectroW + FREQ_LEGEND_TIC_WIDTH_PX, tickY))
                    val layout = labelLayouts[i]
                    val textY = (tickY - layout.size.height / 2f).coerceIn(0f, spectroH - layout.size.height)
                    drawText(layout, topLeft = Offset(spectroW + FREQ_LEGEND_TIC_WIDTH_PX * 2f, textY))
                }
            }
        }

        // Time legend
        var timeCursor = 0.0
        val maxShownTime = (spectroW / ticW) * state.timeStep
        val maxLabels = (spectroW / sampleTimeLabel.size.width).toInt().coerceAtLeast(1)
        val stepByLabel = ceil(maxShownTime / maxLabels).toInt().coerceAtLeast(1)
        var ticPrinted = 0

        while (true) {
            val xPos = spectroW - ticW * (timeCursor / state.timeStep).toFloat()
            if (xPos < 0) break
            drawLine(Color.White, Offset(xPos, spectroH), Offset(xPos, spectroH + TIME_LEGEND_TIC_HEIGHT_PX))
            if (ticPrinted % stepByLabel == 0) {
                val text = "+${"%.1f".format(timeCursor)}"
                val layout = textMeasurer.measure(text, textStyle)
                val textX = (xPos - layout.size.width / 2f).coerceIn(0f, spectroW - layout.size.width)
                drawText(layout, topLeft = Offset(textX, spectroH + TIME_LEGEND_TIC_HEIGHT_PX))
            }
            timeCursor += 1.0
            ticPrinted++
        }
    }
}