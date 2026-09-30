package com.aimforge.app.domain.metrics

import com.aimforge.app.domain.cv.CrosshairDetection
import com.aimforge.app.domain.cv.FrameObservation
import com.aimforge.app.domain.cv.TargetDetection
import org.junit.Assert.assertEquals
import org.junit.Test

class MovementCalculatorTest {
    private val noTarget = TargetDetection.notDetected(0, 0, "none")

    private fun obs(frame: Int, t: Long, x: Float?, y: Float?, conf: Float = 0.9f): FrameObservation {
        val cross = if (x == null) CrosshairDetection.notDetected(frame, t, "test")
        else CrosshairDetection(frame, t, true, x, y, confidence = conf, method = "test")
        return FrameObservation(frame, t, cross, noTarget.copy(frameIndex = frame, timestampMs = t))
    }

    @Test fun exactDeltaDistanceAndSpeedFromRealTimestamps() {
        // x1=0.5,y1=0.5 -> x2=0.6,y2=0.5 : expected normalized distance 0.1, over 200ms -> speed 0.5/s
        val obsList = listOf(obs(1, 0, 0.5f, 0.5f), obs(2, 200, 0.6f, 0.5f))
        val series = MovementCalculator.compute(obsList, minConfidence = 0.3f, maxGapMs = 1000, directionDeadZoneNorm = 0.004f)
        assertEquals(1, series.pairs.size)
        val p = series.pairs[0]
        assertEquals(0.1f, p.dx, 0.001f)
        assertEquals(0f, p.dy, 0.001f)
        assertEquals(0.1f, p.distance, 0.001f)
        assertEquals(0.5f, p.speed, 0.001f)
        assertEquals(MovementDirection.RIGHT, p.direction)
    }

    @Test fun lowConfidenceDetectionsAreExcluded() {
        val obsList = listOf(obs(1, 0, 0.5f, 0.5f, conf = 0.1f), obs(2, 100, 0.6f, 0.5f, conf = 0.1f))
        val series = MovementCalculator.compute(obsList, minConfidence = 0.3f, maxGapMs = 1000, directionDeadZoneNorm = 0.004f)
        assertEquals(0, series.pairs.size)
    }

    @Test fun missedFrameDoesNotFabricateMovementAcrossIt() {
        // frame 2 undetected: movement is measured directly from frame 1 to frame 3's real timestamps, not per "expected" frame spacing
        val obsList = listOf(obs(1, 1000, 0.5f, 0.5f), obs(2, 1050, null, null), obs(3, 1100, 0.52f, 0.5f))
        val series = MovementCalculator.compute(obsList, minConfidence = 0.3f, maxGapMs = 1000, directionDeadZoneNorm = 0.004f)
        assertEquals(1, series.pairs.size)
        assertEquals(1, series.pairs[0].fromFrame)
        assertEquals(3, series.pairs[0].toFrame)
        assertEquals(100L, series.pairs[0].dtMs)
    }

    @Test fun nonPositiveTimestampDeltaIsSkippedNotCrashed() {
        val obsList = listOf(obs(1, 1000, 0.5f, 0.5f), obs(2, 1000, 0.6f, 0.5f), obs(3, 999, 0.7f, 0.5f))
        val series = MovementCalculator.compute(obsList, minConfidence = 0.3f, maxGapMs = 1000, directionDeadZoneNorm = 0.004f)
        assertEquals(0, series.pairs.size)
        assertEquals(2, series.invalidTimestampSamples)
    }

    @Test fun largeGapBreaksTheSegmentInsteadOfBridgingIt() {
        val obsList = listOf(obs(1, 1000, 0.2f, 0.2f), obs(2, 5000, 0.9f, 0.9f), obs(3, 5100, 0.91f, 0.9f))
        val series = MovementCalculator.compute(obsList, minConfidence = 0.3f, maxGapMs = 1500, directionDeadZoneNorm = 0.004f)
        assertEquals(1, series.timestampGapCount)
        // only the second, plausible pair is produced
        assertEquals(1, series.pairs.size)
        assertEquals(2, series.pairs[0].fromFrame)
    }
}
