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
    val gray: ByteArray,
    /**
     * Full source-frame width/height this Frame's coordinates should be normalized against.
     * Defaults to [width]/[height] themselves: a plain full-frame Frame already spans the whole
     * source. A cropped Frame (see GraySampler.buildCentralCrop) sets these to the real, larger
     * source dimensions so a detector can report positions in full-frame-normalized coordinates
     * even though it only looked at a central crop.
     */
    val sourceWidth: Int = width,
    val sourceHeight: Int = height,
    /** Pixel offset of this Frame's local (0,0) within the source frame. 0 for a full-frame Frame. */
    val cropOffsetXPx: Int = 0,
    val cropOffsetYPx: Int = 0,
    /** Size, in SOURCE pixels, of the region this grid covers (grid is a downsample of it). Defaults to the grid size (1:1). */
    val cropWidthPx: Int = width,
    val cropHeightPx: Int = height
) {
    /** Maps a local grid coordinate to full-source-normalized [0,1], accounting for crop offset and grid scaling. */
    fun toSourceNormX(localPx: Number): Float =
        Coordinates.normX(cropOffsetXPx + localPx.toFloat() * cropWidthPx / max1(width), sourceWidth)
    fun toSourceNormY(localPx: Number): Float =
        Coordinates.normY(cropOffsetYPx + localPx.toFloat() * cropHeightPx / max1(height), sourceHeight)
    /** Converts a length in grid pixels to a fraction of the full source width/height. */
    fun gridLengthToSourceNormX(gridPx: Number): Float = Coordinates.normX(gridPx.toFloat() * cropWidthPx / max1(width), sourceWidth)
    fun gridLengthToSourceNormY(gridPx: Number): Float = Coordinates.normY(gridPx.toFloat() * cropHeightPx / max1(height), sourceHeight)
    private fun max1(v: Int) = if (v < 1) 1 else v
}

object Coordinates {
    fun normX(px: Number, width: Int): Float = if (width <= 0) 0f else (px.toFloat() / width).coerceIn(0f, 1f)
    fun normY(px: Number, height: Int): Float = if (height <= 0) 0f else (px.toFloat() / height).coerceIn(0f, 1f)
}
