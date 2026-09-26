package com.aimforge.app.domain.cv

/** One frame's target detection result. */
data class TargetDetection(
    val frameIndex: Int,
    val timestampMs: Long,
    val detected: Boolean,
    val xNorm: Float? = null,
    val yNorm: Float? = null,
    val confidence: Float = 0f,
    val method: String
) {
    companion object {
        fun notDetected(frameIndex: Int, timestampMs: Long, method: String) =
            TargetDetection(frameIndex, timestampMs, detected = false, confidence = 0f, method = method)
    }
}

interface TargetVisionDetector {
    val version: String
    fun detect(frame: Frame): TargetDetection
}

/**
 * Phase 4 ships no real target detector: reliably telling a BGMI target/player apart from ordinary
 * scenery from a single frame, with no training data and no ML model, is not something that can be
 * done honestly yet. Every frame is reported NOT_DETECTED rather than guessing a position at screen
 * center or anywhere else. [CvAnalysisEngine] still counts and reports the (always zero) target rate
 * so the limitation is visible in the stored result, not hidden.
 */
class NoOpTargetDetector : TargetVisionDetector {
    override val version = "target-none-v1"
    override fun detect(frame: Frame): TargetDetection = TargetDetection.notDetected(frame.frameIndex, frame.timestampMs, version)
}