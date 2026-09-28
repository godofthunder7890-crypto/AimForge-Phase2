package com.aimforge.app.domain.cv

import org.junit.Assert.assertEquals
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
        val r = detector.detect(uniformFrame(100, 100, 128))
        assertFalse(r.detected)
    }

    @Test fun centeredBrightDotIsDetectedNearItsRealPosition() {
        val frame = frameWithBrightDot(100, 100, cx = 50, cy = 50, radius = 3)
        val r = detector.detect(frame)
        assertTrue(r.detected)
        assertTrue(r.confidence > 0.25f)
        assertTrue(kotlin.math.abs((r.xNorm ?: -1f) - 0.5f) < 0.05f)
        assertTrue(kotlin.math.abs((r.yNorm ?: -1f) - 0.5f) < 0.05f)
        assertEquals("center-bright-cluster-v1", r.method)
    }

    @Test fun offCenterDotWithinRoiIsDetectedAtItsRealPosition() {
        val frame = frameWithBrightDot(200, 200, cx = 110, cy = 90, radius = 3)
        val r = detector.detect(frame)
        assertTrue(r.detected)
        assertTrue(kotlin.math.abs((r.xNorm ?: -1f) - 0.55f) < 0.05f)
        assertTrue(kotlin.math.abs((r.yNorm ?: -1f) - 0.45f) < 0.05f)
    }

    @Test fun largeBrightRegionIsNotMistakenForACrosshair() {
        // bright area fills the whole central ROI, e.g. a bright sky patch: not a small reticle
        val w = 100; val h = 100
        val g = ByteArray(w * h) { 30.toByte() }
        for (y in 25 until 75) for (x in 25 until 75) g[y * w + x] = 220.toByte()
        val r = detector.detect(Frame(2, 2000L, w, h, g))
        assertFalse(r.detected)
    }

    @Test fun dotOutsideCentralRoiIsIgnored() {
        // bright dot in a corner, well outside the default 50% central ROI
        val frame = frameWithBrightDot(200, 200, cx = 5, cy = 5, radius = 3)
        val r = detector.detect(frame)
        assertFalse(r.detected)
    }

    @Test fun invalidFrameSizeIsHandledWithoutCrashing() {
        assertFalse(detector.detect(Frame(0, 0, 0, 0, ByteArray(0))).detected)
        assertFalse(detector.detect(Frame(0, 0, 10, 10, ByteArray(5))).detected) // too-short buffer
    }
}
