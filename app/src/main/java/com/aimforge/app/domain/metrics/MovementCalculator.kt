package com.aimforge.app.domain.metrics

import com.aimforge.app.domain.cv.CrosshairDetection
import com.aimforge.app.domain.cv.FrameObservation

data class MovementSeries(
    val pairs: List<MovementPair>,
    /** Consecutive-detection gaps longer than [AimMetricsEngine]'s maxGapMs; the segment was broken, not bridged. */
    val timestampGapCount: Int,
    /** Consecutive pairs with a non-positive timestamp delta (out-of-order/duplicate); skipped, not fabricated. */
    val invalidTimestampSamples: Int
)

/**
 * Turns the real per-frame crosshair detections in [observations] into real movement steps.
 * Only detections with confidence >= [minConfidence] are used. A step is only produced between
 * two such detections whose real timestamp delta is positive and no larger than [maxGapMs];
 * a larger gap breaks the segment instead of being bridged into one big fabricated movement.
 */
object MovementCalculator {
    fun compute(
        observations: List<FrameObservation>,
        minConfidence: Float,
        maxGapMs: Long,
        directionDeadZoneNorm: Float
    ): MovementSeries {
        val confident: List<CrosshairDetection> = observations
            .map { it.crosshair }
            .filter { it.detected && it.xNorm != null && it.yNorm != null && it.confidence >= minConfidence }

        val pairs = ArrayList<MovementPair>()
        var gapCount = 0
        var invalidCount = 0

        for (i in 1 until confident.size) {
            val a = confident[i - 1]
            val b = confident[i]
            val dt = b.timestampMs - a.timestampMs
            if (dt <= 0) { invalidCount++; continue }
            if (dt > maxGapMs) { gapCount++; continue }
            val dx = b.xNorm!! - a.xNorm!!
            val dy = b.yNorm!! - a.yNorm!!
            val distance = kotlin.math.sqrt(dx * dx + dy * dy)
            val speed = distance / (dt / 1000f)
            val direction = DirectionClassifier.classify(dx, dy, directionDeadZoneNorm)
            pairs += MovementPair(a.frameIndex, b.frameIndex, dt, dx, dy, distance, speed, direction)
        }
        return MovementSeries(pairs, gapCount, invalidCount)
    }
}
