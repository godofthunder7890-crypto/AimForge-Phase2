package com.aimforge.app.capture

import com.aimforge.app.domain.cv.Frame
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Downsamples one real captured RGBA frame into a small grayscale [Frame] for the CV pipeline.
 * Deliberately cheap (nearest-neighbor box sampling on a sparse grid) so it can run on every
 * analyzed frame on a phone without hurting capture. Pure JVM math, no Android types, so it is
 * unit-testable directly.
 */
object GraySampler {
    fun build(
        buffer: ByteBuffer,
        srcWidth: Int,
        srcHeight: Int,
        rowStride: Int,
        pixelStride: Int,
        frameIndex: Int,
        timestampMs: Long,
        outWidth: Int = 160
    ): Frame {
        val safeOutWidth = max(1, outWidth)
        val outHeight = max(1, (safeOutWidth.toFloat() * srcHeight / srcWidth).roundToInt())
        val gray = ByteArray(safeOutWidth * outHeight)
        val limit = buffer.limit()

        for (gy in 0 until outHeight) {
            val sy = ((gy + 0.5f) * srcHeight / outHeight).toInt().coerceIn(0, srcHeight - 1)
            for (gx in 0 until safeOutWidth) {
                val sx = ((gx + 0.5f) * srcWidth / safeOutWidth).toInt().coerceIn(0, srcWidth - 1)
                val i = sy * rowStride + sx * pixelStride
                val luma = if (i + 2 < limit) {
                    val r = buffer.get(i).toInt() and 0xFF
                    val g = buffer.get(i + 1).toInt() and 0xFF
                    val b = buffer.get(i + 2).toInt() and 0xFF
                    (r * 299 + g * 587 + b * 114) / 1000
                } else 0
                gray[gy * safeOutWidth + gx] = luma.toByte()
            }
        }
        return Frame(frameIndex, timestampMs, safeOutWidth, outHeight, gray)
    }
}