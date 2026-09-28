package com.aimforge.app.domain.metrics

data class MicroAdjustmentResult(
    val count: Int,
    val frequencyPerSecond: Float?,
    val averageMagnitude: Float?,
    val medianMagnitude: Float?
)

/**
 * A micro-adjustment is a real movement step whose distance is greater than zero but no larger than
 * [maxMagnitudeNorm] (normalized units, resolution-independent). [trackingDurationSeconds] — real
 * elapsed time the crosshair was actually tracked — is required for a frequency; without it only the
 * count/magnitudes are reported.
 */
object MicroAdjustmentDetector {
    fun compute(pairs: List<MovementPair>, maxMagnitudeNorm: Float, trackingDurationSeconds: Float?): MicroAdjustmentResult {
        val magnitudes = pairs.filter { it.distance > 0f && it.distance <= maxMagnitudeNorm }.map { it.distance }
        val frequency = if (trackingDurationSeconds != null && trackingDurationSeconds > 0f) magnitudes.size / trackingDurationSeconds else null
        return MicroAdjustmentResult(
            count = magnitudes.size,
            frequencyPerSecond = frequency,
            averageMagnitude = Stats.mean(magnitudes),
            medianMagnitude = Stats.median(magnitudes)
        )
    }
}
