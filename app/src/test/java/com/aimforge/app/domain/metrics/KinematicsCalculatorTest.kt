package com.aimforge.app.domain.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KinematicsCalculatorTest {
    private fun pair(from: Int, to: Int, dt: Long, dx: Float, dy: Float, speed: Float, dir: MovementDirection) =
        MovementPair(from, to, dt, dx, dy, kotlin.math.sqrt(dx * dx + dy * dy), speed, dir)

    @Test fun emptyPairsGivesAllNulls() {
        val r = KinematicsCalculator.compute(emptyList())
        assertNull(r.averageAcceleration); assertNull(r.velocityVariance); assertNull(r.directionChangeCount)
    }

    @Test fun accelerationFromRealSpeedChange() {
        // speed goes 0.5/s -> 1.5/s over 200ms => acceleration = 1.0/0.2 = 5.0
        val pairs = listOf(
            pair(1, 2, 200, 0.1f, 0f, 0.5f, MovementDirection.RIGHT),
            pair(2, 3, 200, 0.3f, 0f, 1.5f, MovementDirection.RIGHT)
        )
        val r = KinematicsCalculator.compute(pairs)
        assertEquals(5.0f, r.peakAcceleration!!, 0.01f)
        assertEquals(5.0f, r.averageAcceleration!!, 0.01f)
        assertEquals(0f, r.peakDeceleration!!, 0.01f) // only accelerated, never decelerated
    }

    @Test fun decelerationIsReportedAsPositiveMagnitude() {
        val pairs = listOf(
            pair(1, 2, 200, 0.3f, 0f, 1.5f, MovementDirection.RIGHT),
            pair(2, 3, 200, 0.1f, 0f, 0.5f, MovementDirection.RIGHT)
        )
        val r = KinematicsCalculator.compute(pairs)
        assertEquals(5.0f, r.peakDeceleration!!, 0.01f)
    }

    @Test fun nonContiguousPairsDoNotProduceAcceleration() {
        // pair1 ends at frame 2, pair2 starts at frame 5: not adjacent in the real frame sequence
        val pairs = listOf(
            pair(1, 2, 200, 0.1f, 0f, 0.5f, MovementDirection.RIGHT),
            pair(5, 6, 200, 0.3f, 0f, 1.5f, MovementDirection.RIGHT)
        )
        val r = KinematicsCalculator.compute(pairs)
        assertNull(r.averageAcceleration)
    }

    @Test fun directionChangeCountedOnlyBetweenTwoRealCompassDirections() {
        val pairs = listOf(
            pair(1, 2, 100, 0.1f, 0f, 1f, MovementDirection.RIGHT),
            pair(2, 3, 100, -0.1f, 0f, 1f, MovementDirection.LEFT),   // real change: RIGHT -> LEFT
            pair(3, 4, 100, 0f, 0f, 0f, MovementDirection.STATIONARY), // not counted
            pair(4, 5, 100, -0.1f, 0f, 1f, MovementDirection.LEFT)    // STATIONARY -> LEFT not counted
        )
        val r = KinematicsCalculator.compute(pairs)
        assertEquals(1, r.directionChangeCount)
    }

    @Test fun reversalRateIsFractionOfAdjacentStepsThatFlippedByMoreThan90Degrees() {
        val pairs = listOf(
            pair(1, 2, 100, 0.1f, 0f, 1f, MovementDirection.RIGHT),
            pair(2, 3, 100, -0.1f, 0f, 1f, MovementDirection.LEFT), // 180 degree reversal
            pair(3, 4, 100, -0.1f, 0f, 1f, MovementDirection.LEFT)  // same direction, no reversal
        )
        val r = KinematicsCalculator.compute(pairs)
        assertEquals(0.5f, r.directionReversalRate!!, 0.001f) // 1 reversal out of 2 adjacent contiguous steps
    }

    @Test fun velocityVarianceNeedsAtLeastTwoSamples() {
        val one = listOf(pair(1, 2, 100, 0.1f, 0f, 1f, MovementDirection.RIGHT))
        assertNull(KinematicsCalculator.compute(one).velocityVariance)
        val two = one + pair(2, 3, 100, 0.1f, 0f, 1f, MovementDirection.RIGHT)
        assertTrue(KinematicsCalculator.compute(two).velocityVariance != null)
    }
}
