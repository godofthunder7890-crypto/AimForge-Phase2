package com.aimforge.app.domain.metrics

/** One frame where BOTH crosshair and target were confidently detected. Same frame => already time-aligned. */
data class AlignedSample(
    val frameIndex: Int,
    val timestampMs: Long,
    val crosshairX: Float,
    val crosshairY: Float,
    val targetX: Float,
    val targetY: Float
) {
    val errorX: Float get() = targetX - crosshairX
    val errorY: Float get() = targetY - crosshairY
    val errorDistance: Float get() = kotlin.math.sqrt(errorX * errorX + errorY * errorY)
}

object AlignedSampleBuilder {
    /**
     * Both detections come from the same [com.aimforge.app.domain.cv.FrameObservation] (same frame,
     * same timestamp), so no separate time-skew check is needed — they describe the same instant by
     * construction. A sample is included only when both are confidently detected.
     */
    fun build(observations: List<com.aimforge.app.domain.cv.FrameObservation>, minConfidence: Float): List<AlignedSample> =
        observations.mapNotNull { obs ->
            val c = obs.crosshair
            val t = obs.target
            if (c.detected && c.xNorm != null && c.yNorm != null && c.confidence >= minConfidence &&
                t.detected && t.xNorm != null && t.yNorm != null && t.confidence >= minConfidence
            ) {
                AlignedSample(obs.frameIndex, obs.timestampMs, c.xNorm, c.yNorm, t.xNorm, t.yNorm)
            } else null
        }
}
