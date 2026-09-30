package com.aimforge.app.domain.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MicroAdjustmentDetectorTest {
    private fun pair(distance: Float) = MovementPair(0, 1, 100, distance, 0f, distance, distance * 10f, MovementDirection.RIGHT)

    @Test fun onlySmallNonZeroStepsCountAsMicroAdjustments() {
        val pairs = listOf(pair(0.005f), pair(0.02f), pair(0.008f), pair(0f))
        val r = MicroAdjustmentDetector.compute(pairs, maxMagnitudeNorm = 0.012f, trackingDurationSeconds = 2f)
        assertEquals(2, r.count) // 0.005 and 0.008 qualify; 0.02 too big, 0f is not a movement at all
        assertEquals(1.0f, r.frequencyPerSecond!!, 0.01f) // 2 over 2 seconds
    }

    @Test fun noDurationMeansNoFrequencyButStillCounts() {
        val pairs = listOf(pair(0.005f))
        val r = MicroAdjustmentDetector.compute(pairs, maxMagnitudeNorm = 0.012f, trackingDurationSeconds = null)
        assertEquals(1, r.count)
        assertNull(r.frequencyPerSecond)
    }

    @Test fun noQualifyingStepsGivesZeroCountAndNullMagnitudes() {
        val pairs = listOf(pair(0.5f))
        val r = MicroAdjustmentDetector.compute(pairs, maxMagnitudeNorm = 0.012f, trackingDurationSeconds = 1f)
        assertEquals(0, r.count)
        assertNull(r.averageMagnitude)
    }
}
