package com.aimforge.app.domain.cv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** TEST FIXTURES: synthetic gray grids, never real gameplay, used only to test the detector math. */
class HeuristicCrosshairDetectorTest {
    private val detector = HeuristicCrosshairDetector()

    private fun uniformFrame(w: Int, h: Int, value: Int): Frame {
        val g = ByteArray(w * h) { value.toByte() }
        return Frame(0, 0L, w, h, g)
    }

    private fun frameWithBrightDot(w: Int, h: Int, cx: Int, cy: Int, radius: Int, bg: Int = 40, fg: Int = 230): Frame {
        val g = ByteArray(w * h) { bg.toByte() }
        for (y in 0 until h) for (x in 0 until w) {
            if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= radius * radius) g[y * w + x] = fg.toByte()
        }
        return Frame(1, 1000L, w, h, g)
    }

    @Test fun uniformFrameIsNotDetected() {
        assertFalse(detector.detect(uniformFrame(100, 100, 128)).detected)
    }

    @Test fun centeredBrightDotIsDetectedNearItsRealPosition() {
        val r = detector.detect(frameWithBrightDot(100, 100, cx = 50, cy = 50, radius = 3))
        assertTrue(r.detected)
        assertTrue(r.confidence > 0.25f)
        assertTrue(kotlin.math.abs((r.xNorm ?: -1f) - 0.5f) < 0.05f)
        assertTrue(kotlin.math.abs((r.yNorm ?: -1f) - 0.5f) < 0.05f)
        assertTrue(r.method == "center-bright-cluster-v1")
    }

    @Test fun offCenterDotWithinRoiIsDetectedAtItsRealPosition() {
        val r = detector.detect(frameWithBrightDot(200, 200, cx = 110, cy = 90, radius = 3))
        assertTrue(r.detected)
        assertTrue(kotlin.math.abs((r.xNorm ?: -1f) - 0.55f) < 0.05f)
        assertTrue(kotlin.math.abs((r.yNorm ?: -1f) - 0.45f) < 0.05f)
    }

    @Test fun largeBrightRegionIsNotMistakenForACrosshair() {
        val w = 100; val h = 100
        val g = ByteArray(w * h) { 30.toByte() }
        for (y in 25 until 75) for (x in 25 until 75) g[y * w + x] = 220.toByte()
        assertFalse(detector.detect(Frame(2, 2000L, w, h, g)).detected)
    }

    @Test fun dotOutsideCentralRoiIsIgnored() {
        assertFalse(detector.detect(frameWithBrightDot(200, 200, cx = 5, cy = 5, radius = 3)).detected)
    }

    @Test fun invalidFrameSizeIsHandledWithoutCrashing() {
        assertFalse(detector.detect(Frame(0, 0, 0, 0, ByteArray(0))).detected)
        assertFalse(detector.detect(Frame(0, 0, 10, 10, ByteArray(5))).detected)
    }
}