package com.aimforge.app.domain.cv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporalTrackerTest {
    private fun det(frame: Int, t: Long, x: Float, y: Float, conf: Float = 0.8f) =
        CrosshairDetection(frame, t, true, x, y, confidence = conf, method = "test")
    private fun miss(frame: Int, t: Long) = CrosshairDetection.notDetected(frame, t, "test")

    @Test fun firstDetectionHasNoMovement() {
        val t = CrosshairTemporalTracker()
        assertEquals(TrackEvent.FirstDetection, t.accept(det(1, 1000, 0.5f, 0.5f)))
    }

    @Test fun missDoesNotBreakTracking() {
        val t = CrosshairTemporalTracker()
        t.accept(det(1, 1000, 0.5f, 0.5f))
        assertEquals(TrackEvent.NoDetection, t.accept(miss(2, 1033)))
        val ev = t.accept(det(3, 1066, 0.51f, 0.5f)) as TrackEvent.Movement
        assertTrue(ev.sample.distanceNorm > 0f)
    }

    @Test fun computesRealDeltaDistanceSpeedAndDirection() {
        val t = CrosshairTemporalTracker()
        t.accept(det(1, 1000, 0.40f, 0.40f))
        val ev = t.accept(det(2, 1100, 0.44f, 0.40f)) as TrackEvent.Movement
        assertEquals(0.04f, ev.sample.deltaXNorm, 0.001f)
        assertEquals(0f, ev.sample.deltaYNorm, 0.001f)
        assertEquals(0.04f, ev.sample.distanceNorm, 0.001f)
        assertEquals(0.4f, ev.sample.speedNormPerSec, 0.01f)
        assertEquals(0f, ev.sample.directionDeg, 0.5f)
    }

    @Test fun gapLargerThanMaxIsNotBridgedIntoMovement() {
        val t = CrosshairTemporalTracker(maxGapMs = 500)
        t.accept(det(1, 1000, 0.2f, 0.2f))
        assertTrue(t.accept(det(2, 2000, 0.9f, 0.9f)) is TrackEvent.GapTooLarge)
        val next = t.accept(det(3, 2050, 0.91f, 0.9f)) as TrackEvent.Movement
        assertTrue(next.sample.distanceNorm < 0.1f)
    }

    @Test fun implausibleJumpIsRejectedAndDoesNotMoveTrackedPosition() {
        val t = CrosshairTemporalTracker(maxPlausibleSpeedNormPerSec = 1f)
        t.accept(det(1, 1000, 0.1f, 0.1f))
        assertTrue(t.accept(det(2, 1010, 0.9f, 0.9f)) is TrackEvent.ImplausibleJump)
        val ev = t.accept(det(3, 1300, 0.15f, 0.1f)) as TrackEvent.Movement
        assertEquals(0.05f, ev.sample.distanceNorm, 0.005f)
    }

    @Test fun outOfOrderTimestampIsIgnored() {
        val t = CrosshairTemporalTracker()
        t.accept(det(1, 1000, 0.5f, 0.5f))
        assertEquals(TrackEvent.NoDetection, t.accept(det(2, 999, 0.6f, 0.6f)))
    }

    @Test fun resetClearsTrackedPosition() {
        val t = CrosshairTemporalTracker()
        t.accept(det(1, 1000, 0.5f, 0.5f))
        t.reset()
        assertEquals(TrackEvent.FirstDetection, t.accept(det(2, 1100, 0.5f, 0.5f)))
    }
}