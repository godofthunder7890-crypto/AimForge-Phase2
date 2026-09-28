package com.aimforge.app.domain.metrics

import com.aimforge.app.domain.cv.CrosshairDetection
import com.aimforge.app.domain.cv.FrameObservation
import com.aimforge.app.domain.cv.TargetDetection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AimMetricsEngineTest {
    private fun cross(frame: Int, t: Long, x: Float, conf: Float = 0.9f) =
        CrosshairDetection(frame, t, true, x, 0.5f, confidence = conf, method = "test")
    private fun missCross(frame: Int, t: Long) = CrosshairDetection.notDetected(frame, t, "test")
    private fun missTarget(frame: Int, t: Long) = TargetDetection.notDetected(frame, t, "none")
    private fun target(frame: Int, t: Long, x: Float, conf: Float = 0.9f) =
        TargetDetection(frame, t, true, x, 0.5f, confidence = conf, method = "test")

    private fun obsNoTarget(frame: Int, t: Long, x: Float?, conf: Float = 0.9f): FrameObservation {
        val c = if (x == null) missCross(frame, t) else cross(frame, t, x, conf)
        return FrameObservation(frame, t, c, missTarget(frame, t))
    }

    @Test fun noObservationsIsNoData() {
        val r = AimMetricsEngine(clock = { 42L }).analyze("s0", emptyList(), 0)
        assertEquals(AimMetricsStatus.NO_DATA, r.status)
        assertEquals(0, r.quality.framesProcessed)
        assertNull(r.totalMovementDistance)
        assertEquals(42L, r.computedAtMs)
    }

    @Test fun fewFramesIsInsufficientData() {
        val obs = (0 until 5).map { obsNoTarget(it, it * 100L, 0.5f + it * 0.01f) }
        val r = AimMetricsEngine(minFramesForAnalysis = 20).analyze("s1", obs, 0)
        assertEquals(AimMetricsStatus.INSUFFICIENT_DATA, r.status)
        assertNull(r.averageSpeed)
        assertEquals(5, r.quality.framesProcessed)
    }

    @Test fun enoughFramesButNoMovementPairsIsInsufficientData() {
        // frames exist, but the crosshair is never confidently detected -> no movement pairs
        val obs = (0 until 30).map { obsNoTarget(it, it * 100L, null) }
        val r = AimMetricsEngine(minFramesForAnalysis = 20, minMovementPairsForAnalysis = 5).analyze("s2", obs, 0)
        assertEquals(AimMetricsStatus.INSUFFICIENT_DATA, r.status)
        assertEquals(0, r.quality.validCrosshairSamples)
    }

    @Test fun goodCrosshairDataWithNoTargetIsAnalyzed() {
        val obs = (0 until 40).map { obsNoTarget(it, it * 100L, 0.4f + it * 0.005f) }
        val r = AimMetricsEngine(minFramesForAnalysis = 20).analyze("s3", obs, droppedFrames = 7)
        assertEquals(AimMetricsStatus.ANALYZED, r.status)
        assertTrue(r.totalMovementDistance!! > 0f)
        assertTrue(r.averageSpeed!! > 0f)
        assertEquals(7, r.quality.droppedFrames)
        // target-dependent metrics stay unavailable (null), never zero, since target was never detected
        assertNull(r.overshootCount)
        assertNull(r.averageTargetError)
    }

    @Test fun goodCrosshairAndTargetDataIsAnalyzed() {
        val obs = (0 until 40).map { i ->
            FrameObservation(i, i * 100L, cross(i, i * 100L, 0.4f + i * 0.003f), target(i, i * 100L, 0.4f + i * 0.003f + 0.02f))
        }
        val r = AimMetricsEngine(minFramesForAnalysis = 20).analyze("s4", obs, 0)
        assertEquals(AimMetricsStatus.ANALYZED, r.status)
        assertTrue(r.averageTargetError!! > 0f)
        assertEquals(39, r.quality.validTargetSamples)
    }

    @Test fun zeroNetMovementIsARealZeroNotNull() {
        // moves right then left back to the exact same spot: net horizontal movement is a real 0
        val obs = (0 until 30).map { i ->
            val x = if (i < 15) 0.5f + i * 0.01f else 0.5f + (29 - i) * 0.01f
            obsNoTarget(i, i * 100L, x)
        }
        val r = AimMetricsEngine(minFramesForAnalysis = 20).analyze("s5", obs, 0)
        assertEquals(0f, r.horizontalMovementNet!!, 0.02f)
        assertTrue(r.horizontalMovementTotal!! > 0f) // total absolute movement is still clearly nonzero
    }

    @Test fun repeatedAnalysisOfSameInputIsDeterministic() {
        val obs = (0 until 30).map { obsNoTarget(it, it * 100L, 0.4f + it * 0.004f) }
        val engine = AimMetricsEngine(minFramesForAnalysis = 20, clock = { 1000L })
        val r1 = engine.analyze("s6", obs, 3)
        val r2 = engine.analyze("s6", obs, 3)
        assertEquals(r1.status, r2.status)
        assertEquals(r1.totalMovementDistance, r2.totalMovementDistance)
        assertEquals(r1.averageSpeed, r2.averageSpeed)
        assertEquals(r1.directionChangeCount, r2.directionChangeCount)
    }

    @Test fun lowConfidenceCrosshairIsExcludedFromQualityAndMovement() {
        val obs = (0 until 30).map { obsNoTarget(it, it * 100L, 0.4f + it * 0.005f, conf = 0.1f) }
        val r = AimMetricsEngine(minConfidence = 0.3f, minFramesForAnalysis = 20).analyze("s7", obs, 0)
        assertEquals(0, r.quality.validCrosshairSamples)
        assertEquals(AimMetricsStatus.INSUFFICIENT_DATA, r.status)
    }

    @Test fun sessionIdIsCarriedThroughUnchanged() {
        val r = AimMetricsEngine().analyze("my-id-9", emptyList(), 0)
        assertEquals("my-id-9", r.sessionId)
    }
}

/** Phase 5 status semantics: target detection is not required for ANALYZED. Synthetic numeric fixtures, not gameplay. */
class AimMetricsStatusSemanticsTest {
    private fun obs(i: Int, x: Float?, conf: Float = 0.9f): FrameObservation {
        val t = i * 100L
        val c = if (x == null) CrosshairDetection.notDetected(i, t, "test")
        else CrosshairDetection(i, t, true, x, 0.5f, confidence = conf, method = "test")
        return FrameObservation(i, t, c, TargetDetection.notDetected(i, t, "target-none-v1"))
    }

    @Test fun validCrosshairMetricsWithNoTargetIsAnalyzed() {
        val list = (0 until 40).map { obs(it, 0.4f + it * 0.004f) }
        val r = AimMetricsEngine().analyze("a", list, 0)
        assertEquals(AimMetricsStatus.ANALYZED, r.status)
        assertTrue(r.totalMovementDistance!! > 0f)
        assertTrue(r.averageSpeed!! > 0f)
        assertEquals(0, r.quality.validTargetSamples)
    }

    @Test fun targetFieldsStayNullNotZeroWhenNoTargetWasObserved() {
        val list = (0 until 40).map { obs(it, 0.4f + it * 0.004f) }
        val r = AimMetricsEngine().analyze("b", list, 0)
        assertNull(r.averageTargetError); assertNull(r.medianTargetError)
        assertNull(r.minimumTargetError); assertNull(r.maximumTargetError)
        assertNull(r.targetErrorVariance); assertNull(r.timeWithinTargetRegionMs)
        assertNull(r.overshootCount); assertNull(r.correctionCount)
        assertNull(r.averageCorrectionMagnitude); assertNull(r.averageCorrectionTimeMs)
    }

    @Test fun noObservationsIsNoData() {
        assertEquals(AimMetricsStatus.NO_DATA, AimMetricsEngine().analyze("c", emptyList(), 0).status)
    }

    @Test fun tooFewFramesIsInsufficientData() {
        val list = (0 until 8).map { obs(it, 0.4f + it * 0.004f) }
        assertEquals(AimMetricsStatus.INSUFFICIENT_DATA, AimMetricsEngine().analyze("d", list, 0).status)
    }

    @Test fun enoughFramesButNoCrosshairIsInsufficientData() {
        val list = (0 until 40).map { obs(it, null) }
        val r = AimMetricsEngine().analyze("e", list, 0)
        assertEquals(AimMetricsStatus.INSUFFICIENT_DATA, r.status)
        assertNull(r.totalMovementDistance)
    }

    @Test fun enoughFramesButTooFewMovementPairsIsInsufficientData() {
        // only 3 confident detections among 40 frames: fewer than the 5 movement pairs required
        val list = (0 until 40).map { obs(it, if (it in 10..12) 0.5f + it * 0.001f else null) }
        assertEquals(AimMetricsStatus.INSUFFICIENT_DATA, AimMetricsEngine().analyze("f", list, 0).status)
    }
}
