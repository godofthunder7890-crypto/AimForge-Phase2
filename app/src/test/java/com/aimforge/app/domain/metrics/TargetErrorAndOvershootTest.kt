package com.aimforge.app.domain.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetErrorAndOvershootTest {
    private fun sample(frame: Int, t: Long, cx: Float, cy: Float, tx: Float, ty: Float) =
        AlignedSample(frame, t, cx, cy, tx, ty)

    @Test fun noAlignedSamplesIsAllNull() {
        val r = TargetErrorCalculator.compute(emptyList(), 0.05f, 1000)
        assertNull(r.average); assertNull(r.median); assertNull(r.timeWithinTargetRegionMs)
        val oc = OvershootCorrectionDetector.compute(emptyList(), 0.05f, 800)
        assertEquals(0, oc.overshootCount)
    }

    @Test fun errorStatisticsFromRealAlignedPositions() {
        val samples = listOf(
            sample(1, 0, 0.5f, 0.5f, 0.5f, 0.5f),   // error 0
            sample(2, 100, 0.5f, 0.5f, 0.6f, 0.5f)  // error 0.1
        )
        val r = TargetErrorCalculator.compute(samples, closeToTargetNorm = 0.05f, maxGapMs = 1000)
        assertEquals(0.05f, r.average!!, 0.001f)
        assertEquals(0f, r.minimum!!, 0.001f)
        assertEquals(0.1f, r.maximum!!, 0.001f)
    }

    @Test fun timeWithinTargetRegionOnlyCountsWhenStartingSampleWasClose() {
        val samples = listOf(
            sample(1, 0, 0.5f, 0.5f, 0.5f, 0.5f),      // close (error 0)
            sample(2, 300, 0.5f, 0.5f, 0.5f, 0.5f),    // still close -> +300ms
            sample(3, 600, 0.5f, 0.5f, 0.9f, 0.5f)     // far now, interval starts close -> +300ms counted for this leg
        )
        val r = TargetErrorCalculator.compute(samples, closeToTargetNorm = 0.05f, maxGapMs = 1000)
        assertEquals(600L, r.timeWithinTargetRegionMs)
    }

    @Test fun overshootThenCorrectionWithinWindowIsCounted() {
        val samples = listOf(
            sample(1, 0, 0.50f, 0.5f, 0.50f, 0.5f),     // close, error 0
            sample(2, 100, 0.60f, 0.5f, 0.50f, 0.5f),   // moved away: error 0.10 -> overshoot begins
            sample(3, 300, 0.51f, 0.5f, 0.50f, 0.5f)    // back close: error 0.01 -> correction, within 800ms
        )
        val r = OvershootCorrectionDetector.compute(samples, closeToTargetNorm = 0.05f, maxReacquireMs = 800)
        assertEquals(1, r.overshootCount)
        assertEquals(1, r.correctionCount)
        assertEquals(0.10f, r.averageCorrectionMagnitude!!, 0.005f)
        assertEquals(200f, r.averageCorrectionTimeMs!!, 0.5f)
    }

    @Test fun overshootThatNeverReturnsIsNotCountedAsCorrection() {
        val samples = listOf(
            sample(1, 0, 0.50f, 0.5f, 0.50f, 0.5f),
            sample(2, 100, 0.90f, 0.5f, 0.50f, 0.5f),   // far away, never comes back
            sample(3, 2000, 0.91f, 0.5f, 0.50f, 0.5f)   // still far, and past the reacquire window
        )
        val r = OvershootCorrectionDetector.compute(samples, closeToTargetNorm = 0.05f, maxReacquireMs = 800)
        assertEquals(1, r.overshootCount)
        assertEquals(0, r.correctionCount)
        assertNull(r.averageCorrectionMagnitude)
    }

    @Test fun stayingCloseTheWholeTimeHasNoOvershoot() {
        val samples = (0 until 5).map { sample(it, it * 100L, 0.5f, 0.5f, 0.5f, 0.5f) }
        val r = OvershootCorrectionDetector.compute(samples, closeToTargetNorm = 0.05f, maxReacquireMs = 800)
        assertEquals(0, r.overshootCount)
        assertEquals(0, r.correctionCount)
    }
}
