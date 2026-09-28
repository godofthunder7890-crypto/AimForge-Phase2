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

    /**
     * Central-crop variant. Crops [cropFraction] of the source frame's width and height (centered),
     * then samples that crop only — so a thin on-screen structure (e.g. a BGMI reticle a few pixels
     * wide) survives downsampling far better than sampling the whole frame down to a small grid would
     * allow. Each sampled pixel stores max(R,G,B) rather than luma: a saturated red crosshair has
     * luma around 76 under the standard weights (0.299R) but max(R,G,B)=255, so this avoids the
     * pipeline silently treating a colored crosshair as "dark".
     *
     * The returned [Frame] carries [Frame.sourceWidth]/[Frame.sourceHeight]/[Frame.cropOffsetXPx]/
     * [Frame.cropOffsetYPx] so a detector can still report positions normalized to the FULL source
     * frame (see [Frame.toSourceNormX]/[Frame.toSourceNormY]), keeping the coordinate system used
     * everywhere else in the app unchanged.
     */
    fun buildCentralCrop(
        buffer: ByteBuffer,
        srcWidth: Int,
        srcHeight: Int,
        rowStride: Int,
        pixelStride: Int,
        frameIndex: Int,
        timestampMs: Long,
        cropFraction: Float = 0.55f,
        outWidth: Int = 220
    ): Frame {
        val cropW = max(2, (srcWidth * cropFraction).roundToInt())
        val cropH = max(2, (srcHeight * cropFraction).roundToInt())
        val cropX0 = (srcWidth - cropW) / 2
        val cropY0 = (srcHeight - cropH) / 2
        // Never upsample beyond the crop's own real pixel count: that would manufacture detail that was never there.
        val safeOutWidth = max(1, minOf(outWidth, cropW))
        val outHeight = max(1, (safeOutWidth.toFloat() * cropH / cropW).roundToInt())
        val gray = ByteArray(safeOutWidth * outHeight)
        val limit = buffer.limit()

        for (gy in 0 until outHeight) {
            val sy = cropY0 + ((gy + 0.5f) * cropH / outHeight).toInt().coerceIn(0, cropH - 1)
            for (gx in 0 until safeOutWidth) {
                val sx = cropX0 + ((gx + 0.5f) * cropW / safeOutWidth).toInt().coerceIn(0, cropW - 1)
                val i = sy * rowStride + sx * pixelStride
                val value = if (i + 2 < limit) {
                    val r = buffer.get(i).toInt() and 0xFF
                    val g = buffer.get(i + 1).toInt() and 0xFF
                    val b = buffer.get(i + 2).toInt() and 0xFF
                    maxOf(r, g, b)
                } else 0
                gray[gy * safeOutWidth + gx] = value.toByte()
            }
        }
        return Frame(
            frameIndex = frameIndex, timestampMs = timestampMs, width = safeOutWidth, height = outHeight, gray = gray,
            sourceWidth = srcWidth, sourceHeight = srcHeight, cropOffsetXPx = cropX0, cropOffsetYPx = cropY0,
            cropWidthPx = cropW, cropHeightPx = cropH
        )
    }
}