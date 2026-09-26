package com.aimforge.app.domain.cv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AimFeatureAnalyzerTest {
    private val analyzer = DefaultAimFeatureAnalyzer()
    private fun cross(x: Float, y: Float, conf: Float = 0.9f, t: Long = 1000) =
        CrosshairDetection(1, t, true, x, y, confidence = conf, method = "test")
    private fun target(x: Float, y: Float, conf: Float = 0.9f, t: Long = 1000) =
        TargetDetection(1, t, true, x, y, confidence = conf, method = "test")

    @Test fun noTargetIsInsufficient() {
        val r = analyzer.analyze(cross(0.5f, 0.5f), TargetDetection.notDetected(1, 1000, "target-none-v1"))
        assertTrue(r is AimFeatureResult.Insufficient)
        assertEquals("Target not detected.", (r as AimFeatureResult.Insufficient).reason)
    }

    @Test fun noCrosshairIsInsufficient() {
        val r = analyzer.analyze(CrosshairDetection.notDetected(1, 1000, "x"), target(0.5f, 0.5f))
        assertTrue(r is AimFeatureResult.Insufficient)
    }

    @Test fun lowConfidenceIsInsufficientEvenWithBothDetected() {
        val r = analyzer.analyze(cross(0.5f, 0.5f, conf = 0.1f), target(0.5f, 0.5f))
        assertTrue(r is AimFeatureResult.Insufficient)
    }

    @Test fun timeSkewTooLargeIsInsufficient() {
        val r = analyzer.analyze(cross(0.5f, 0.5f, t = 1000), target(0.5f, 0.5f, t = 1500))
        assertTrue(r is AimFeatureResult.Insufficient)
    }

    @Test fun bothConfidentAndAlignedComputesRealError() {
        val r = analyzer.analyze(cross(0.40f, 0.40f), target(0.44f, 0.40f)) as AimFeatureResult.Computed
        assertEquals(0.04f, r.errorXNorm, 0.001f)
        assertEquals(0f, r.errorYNorm, 0.001f)
        assertEquals(0.04f, r.errorNorm, 0.001f)
    }

    @Test fun exactMatchIsZeroError() {
        val r = analyzer.analyze(cross(0.5f, 0.5f), target(0.5f, 0.5f)) as AimFeatureResult.Computed
        assertEquals(0f, r.errorNorm, 0.0001f)
    }
}