package com.aimforge.app.domain.metrics

import kotlin.math.abs
import kotlin.math.max as kmax

data class KinematicsResult(
    val averageAcceleration: Float?,
    val peakAcceleration: Float?,
    val peakDeceleration: Float?,
    val velocityVariance: Float?,
    val accelerationVariance: Float?,
    /** Fraction, in [0,1], of adjacent contiguous movement steps whose direction reversed by more than 90 degrees. */
    val directionReversalRate: Float?,
    val directionChangeCount: Int?
)

/**
 * Second-order (acceleration) and smoothness statistics from a [MovementPair] sequence.
 * Acceleration is only computed between two pairs that are *contiguous* — pair i's toFrame equals
 * pair i+1's fromFrame — so a broken segment (a timestamp gap MovementCalculator already refused to
 * bridge) never contributes a fabricated acceleration value.
 */
object KinematicsCalculator {
    fun compute(pairs: List<MovementPair>): KinematicsResult {
        if (pairs.isEmpty()) {
            return KinematicsResult(null, null, null, null, null, null, null)
        }

        val speeds = pairs.map { it.speed }
        val velocityVariance = Stats.variance(speeds)

        val accelerations = ArrayList<Float>()
        var reversalCount = 0
        var contiguousAdjacentCount = 0
        var directionChangeCount = 0

        for (i in 1 until pairs.size) {
            val prev = pairs[i - 1]
            val cur = pairs[i]
            val contiguous = prev.toFrame == cur.fromFrame

            if (contiguous) {
                val dtSec = cur.dtMs / 1000f
                if (dtSec > 0f) accelerations += (cur.speed - prev.speed) / dtSec

                contiguousAdjacentCount++
                val dot = prev.dx * cur.dx + prev.dy * cur.dy
                val magProd = kotlin.math.sqrt((prev.dx * prev.dx + prev.dy * prev.dy).toDouble() * (cur.dx * cur.dx + cur.dy * cur.dy).toDouble()).toFloat()
                if (magProd > 0f && dot / magProd < 0f) reversalCount++

                if (DirectionClassifier.isCompassDirection(prev.direction) &&
                    DirectionClassifier.isCompassDirection(cur.direction) &&
                    prev.direction != cur.direction
                ) directionChangeCount++
            }
        }

        val avgAccel = Stats.mean(accelerations.map { abs(it) })
        val peakAccel = accelerations.maxOrNull()
        val peakDecel = accelerations.minOrNull()?.let { kmax(0f, -it) }
        val accelVariance = Stats.variance(accelerations)
        val reversalRate = if (contiguousAdjacentCount > 0) reversalCount.toFloat() / contiguousAdjacentCount else null

        return KinematicsResult(
            averageAcceleration = avgAccel,
            peakAcceleration = peakAccel,
            peakDeceleration = peakDecel,
            velocityVariance = velocityVariance,
            accelerationVariance = accelVariance,
            directionReversalRate = reversalRate,
            directionChangeCount = if (pairs.isNotEmpty()) directionChangeCount else null
        )
    }
}
