package com.aimforge.app.domain.cv

/**
 * One downsampled grayscale frame handed to the CV pipeline. Built from a real captured frame
 * (Phase 3 MediaProjection); [gray] is a width*height single-byte-per-pixel luma buffer.
 *
 * Coordinate system used everywhere in this package: origin top-left, x grows left-to-right,
 * y grows top-to-bottom. Positions are reported normalized (xNorm/yNorm in [0,1] = px / width or
 * height) so detections stay comparable across frames and sessions even when capture resolution
 * differs (rotation, device). See [Coordinates].
 */
data class Frame(
    val frameIndex: Int,
    val timestampMs: Long,
    val width: Int,
    val height: Int,
    val gray: ByteArray
)

object Coordinates {
    fun normX(px: Number, width: Int): Float = if (width <= 0) 0f else (px.toFloat() / width).coerceIn(0f, 1f)
    fun normY(px: Number, height: Int): Float = if (height <= 0) 0f else (px.toFloat() / height).coerceIn(0f, 1f)
}