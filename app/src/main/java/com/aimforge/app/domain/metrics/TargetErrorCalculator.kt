package com.aimforge.app.domain.metrics

data class TargetErrorResult(
    val average: Float?,
    val median: Float?,
    val minimum: Float?,
    val maximum: Float?,
    val variance: Float?,
    val timeWithinTargetRegionMs: Long?
)

/**
 * Crosshair-to-target error statistics from real aligned samples only (see [AlignedSampleBuilder]).
 * With Phase 4's current NoOpTargetDetector [samples] is always empty, so every field is null — that
 * is the honest "not available" result, not a bug.
 */
object TargetErrorCalculator {
    fun compute(samples: List<AlignedSample>, closeToTargetNorm: Float, maxGapMs: Long): TargetErrorResult {
        if (samples.isEmpty()) return TargetErrorResult(null, null, null, null, null, null)
        val errors = samples.map { it.errorDistance }

        var withinMs = 0L
        for (i in 1 until samples.size) {
            val prev = samples[i - 1]
            val cur = samples[i]
            val dt = cur.timestampMs - prev.timestampMs
            if (dt in 1..maxGapMs && prev.errorDistance <= closeToTargetNorm) withinMs += dt
        }

        return TargetErrorResult(
            average = Stats.mean(errors),
            median = Stats.median(errors),
            minimum = Stats.min(errors),
            maximum = Stats.max(errors),
            variance = Stats.variance(errors),
            timeWithinTargetRegionMs = withinMs.takeIf { samples.size >= 2 }
        )
    }
}
