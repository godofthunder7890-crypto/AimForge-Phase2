package com.aimforge.app.capture

import java.nio.ByteBuffer

/**
 * Cheap black-screen check on a real frame. Reads a sparse grid of pixels from an RGBA_8888 buffer.
 * Some apps or screens block screen capture and Android then delivers black frames; this detects that
 * situation honestly instead of pretending capture worked.
 */
object FrameSampler {
    const val COLS = 24
    const val ROWS = 12
    /** A frame whose brightest sampled pixel is below this is treated as blank (black). */
    const val BLANK_MAX_LUMA = 10

    data class Sample(val meanLuma: Int, val maxLuma: Int) {
        val isBlank: Boolean get() = maxLuma < BLANK_MAX_LUMA
    }

    fun sample(buffer: ByteBuffer, width: Int, height: Int, rowStride: Int, pixelStride: Int): Sample {
        var sum = 0L
        var max = 0
        var n = 0
        val limit = buffer.limit()
        for (gy in 0 until ROWS) {
            val y = ((gy + 0.5f) * height / ROWS).toInt().coerceIn(0, height - 1)
            for (gx in 0 until COLS) {
                val x = ((gx + 0.5f) * width / COLS).toInt().coerceIn(0, width - 1)
                val i = y * rowStride + x * pixelStride
                if (i + 2 >= limit) continue
                val r = buffer.get(i).toInt() and 0xFF
                val g = buffer.get(i + 1).toInt() and 0xFF
                val b = buffer.get(i + 2).toInt() and 0xFF
                val luma = (r * 299 + g * 587 + b * 114) / 1000
                sum += luma
                if (luma > max) max = luma
                n++
            }
        }
        return Sample(meanLuma = if (n == 0) 0 else (sum / n).toInt(), maxLuma = max)
    }
}
