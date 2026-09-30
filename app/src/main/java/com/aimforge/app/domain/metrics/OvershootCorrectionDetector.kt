package com.aimforge.app.domain.metrics

data class OvershootCorrectionResult(
    val overshootCount: Int,
    val correctionCount: Int,
    val averageCorrectionMagnitude: Float?,
    val averageCorrectionTimeMs: Float?
)

/**
 * Detects overshoot-then-correction cycles from real aligned crosshair/target error samples:
 * the crosshair gets within [closeToTargetNorm] of the target ("close"), then moves away again
 * ("overshoot"), then comes back within the threshold ("correction") inside [maxReacquireMs].
 * If it never comes back within that window, no correction is counted — evidence, not inference.
 * Algorithm version: "error-crossing-v1".
 */
object OvershootCorrectionDetector {
    fun compute(samples: List<AlignedSample>, closeToTargetNorm: Float, maxReacquireMs: Long): OvershootCorrectionResult {
        if (samples.size < 2) return OvershootCorrectionResult(0, 0, null, null)

        var overshoots = 0
        var corrections = 0
        val correctionMagnitudes = ArrayList<Float>()
        val correctionTimes = ArrayList<Float>()

        var wasClose = samples.first().errorDistance <= closeToTargetNorm
        var leftCloseAtMs: Long? = null
        var peakErrorSinceLeaving = 0f

        for (i in 1 until samples.size) {
            val s = samples[i]
            val isClose = s.errorDistance <= closeToTargetNorm

            if (wasClose && !isClose) {
                // Just left the target region: a candidate overshoot begins.
                overshoots++
                leftCloseAtMs = s.timestampMs
                peakErrorSinceLeaving = s.errorDistance
            } else if (!wasClose && !isClose && leftCloseAtMs != null) {
                if (s.errorDistance > peakErrorSinceLeaving) peakErrorSinceLeaving = s.errorDistance
                if (s.timestampMs - leftCloseAtMs > maxReacquireMs) {
                    // Gave up waiting for a correction: this excursion no longer counts as a live overshoot.
                    leftCloseAtMs = null
                }
            } else if (!wasClose && isClose && leftCloseAtMs != null) {
                val elapsed = s.timestampMs - leftCloseAtMs
                if (elapsed <= maxReacquireMs) {
                    corrections++
                    correctionMagnitudes += peakErrorSinceLeaving
                    correctionTimes += elapsed.toFloat()
                }
                leftCloseAtMs = null
            }

            wasClose = isClose
        }

        return OvershootCorrectionResult(
            overshootCount = overshoots,
            correctionCount = corrections,
            averageCorrectionMagnitude = Stats.mean(correctionMagnitudes),
            averageCorrectionTimeMs = Stats.mean(correctionTimes)
        )
    }
}
