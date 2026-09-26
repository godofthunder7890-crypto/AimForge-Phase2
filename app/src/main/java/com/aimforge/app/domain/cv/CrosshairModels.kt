package com.aimforge.app.domain.cv

/** One frame's crosshair detection result. Position is normalized (see [Coordinates]); undetected has no position. */
data class CrosshairDetection(
    val frameIndex: Int,
    val timestampMs: Long,
    val detected: Boolean,
    val xNorm: Float? = null,
    val yNorm: Float? = null,
    val boxWidthNorm: Float? = null,
    val boxHeightNorm: Float? = null,
    val confidence: Float = 0f,
    val method: String
) {
    companion object {
        fun notDetected(frameIndex: Int, timestampMs: Long, method: String) =
            CrosshairDetection(frameIndex, timestampMs, detected = false, confidence = 0f, method = method)
    }
}

interface CrosshairVisionDetector {
    /** Detector name + version, stored with every result so an algorithm change is traceable. */
    val version: String
    fun detect(frame: Frame): CrosshairDetection
}