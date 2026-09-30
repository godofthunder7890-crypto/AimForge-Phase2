package com.aimforge.app.domain.cv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * TEST FIXTURES: synthetic grids, NOT real BGMI footage. They only prove the algorithm's logic;
 * they do not count as real-device validation.
 */
class LocalContrastCrosshairDetectorTest {
    private val detector = LocalContrastCrosshairDetector()

    private fun blank(w: Int, h: Int, v: Int) = ByteArray(w * h) { v.toByte() }
    private fun set(g: ByteArray, w: Int, x: Int, y: Int, v: Int) { g[y * w + x] = v.toByte() }

    /** Thin plus-sign crosshair (1px arms) centered at (cx,cy). */
    private fun plus(g: ByteArray, w: Int, h: Int, cx: Int, cy: Int, arm: Int, v: Int) {
        for (d in -arm..arm) {
            if (cx + d in 0 until w) set(g, w, cx + d, cy, v)
            if (cy + d in 0 until h) set(g, w, cx, cy + d, v)
        }
    }

    private fun frame(g: ByteArray, w: Int, h: Int, idx: Int = 1, ts: Long = 1000L,
                      srcW: Int = w, srcH: Int = h, offX: Int = 0, offY: Int = 0,
                      cropW: Int = w, cropH: Int = h) =
        Frame(idx, ts, w, h, g, srcW, srcH, offX, offY, cropW, cropH)

    @Test fun thinPlusCrosshairOnDarkBackgroundIsDetectedAtItsPosition() {
        val w = 120; val h = 120
        val g = blank(w, h, 40)
        plus(g, w, h, 60, 60, 6, 230)
        val r = detector.detect(frame(g, w, h))
        assertTrue(r.detected)
        assertEquals(0.5f, r.xNorm!!, 0.03f)
        assertEquals(0.5f, r.yNorm!!, 0.03f)
        assertEquals("cross-structure-contrast-v2", r.method)
    }

    @Test fun lowContrastCrosshairStillDetectedWhenLocallyDistinct() {
        val w = 120; val h = 120
        val g = blank(w, h, 100)
        plus(g, w, h, 60, 60, 5, 170)
        assertTrue(detector.detect(frame(g, w, h)).detected)
    }

    @Test fun uniformFrameIsNotDetected() {
        val r = detector.detect(frame(blank(100, 100, 128), 100, 100))
        assertFalse(r.detected)
        assertNotNull(r.rejectionReason)
    }

    @Test fun largeBrightBackgroundDoesNotBlockDetectionOfTheRealCrosshair() {
        // The v1 failure mode: a big bright region + a small real crosshair.
        val w = 120; val h = 120
        val g = blank(w, h, 40)
        for (y in 0 until 50) for (x in 0 until w) set(g, w, x, y, 230) // bright "sky" strip
        plus(g, w, h, 60, 70, 5, 250)
        val r = detector.detect(frame(g, w, h))
        assertTrue(r.detected)
        assertTrue(abs(r.yNorm!! - 70f / 120f) < 0.05f)
    }

    @Test fun offCenterFalseObjectLosesToCenteredCrosshair() {
        val w = 120; val h = 120
        val g = blank(w, h, 40)
        plus(g, w, h, 60, 60, 5, 220)      // real, centered
        plus(g, w, h, 12, 12, 5, 240)      // brighter false candidate in a corner
        val r = detector.detect(frame(g, w, h))
        assertTrue(r.detected)
        assertEquals(0.5f, r.xNorm!!, 0.05f)
        assertTrue((r.candidateCount ?: 0) >= 2)
    }

    @Test fun coloredCrosshairIsSeenBecauseSalienceIsMaxChannelNotLuma() {
        // GraySampler stores max(R,G,B); a pure red (255,0,0) pixel is 255 there (luma would be ~76).
        val w = 120; val h = 120
        val g = blank(w, h, 30)
        plus(g, w, h, 60, 60, 4, 255)
        assertTrue(detector.detect(frame(g, w, h)).detected)
    }

    @Test fun portraitAndLandscapeBothWork() {
        val land = blank(160, 72, 40).also { plus(it, 160, 72, 80, 36, 5, 230) }
        val port = blank(72, 160, 40).also { plus(it, 72, 160, 36, 80, 5, 230) }
        val a = detector.detect(frame(land, 160, 72))
        val b = detector.detect(frame(port, 72, 160))
        assertTrue(a.detected && b.detected)
        assertEquals(0.5f, a.xNorm!!, 0.04f); assertEquals(0.5f, a.yNorm!!, 0.04f)
        assertEquals(0.5f, b.xNorm!!, 0.04f); assertEquals(0.5f, b.yNorm!!, 0.04f)
    }

    @Test fun cropOffsetIsMappedBackToFullFrameNormalizedCoordinates() {
        // crop is the central 50% of a 1000x500 source: offset (250,125), size 500x250 mapped to a 100x50 grid.
        val w = 100; val h = 50
        val g = blank(w, h, 40)
        plus(g, w, h, 50, 25, 4, 230) // center of the crop
        val r = detector.detect(frame(g, w, h, srcW = 1000, srcH = 500, offX = 250, offY = 125, cropW = 500, cropH = 250))
        // grid (50,25) -> source x = 250 + 50*(500/100) = 500 -> 0.5 ; y = 125 + 25*(250/50) = 250 -> 0.5
        assertTrue(r.detected)
        assertEquals(0.5f, r.xNorm!!, 0.03f)
        assertEquals(0.5f, r.yNorm!!, 0.03f)

        // an off-center grid position must map to the right FULL-frame coordinate, not the crop-relative one
        val g2 = blank(w, h, 40).also { plus(it, w, h, 75, 25, 4, 230) }
        val r2 = detector.detect(frame(g2, w, h, srcW = 1000, srcH = 500, offX = 250, offY = 125, cropW = 500, cropH = 250))
        // grid x=75 -> 250 + 75*5 = 625 -> 0.625
        assertEquals(0.625f, r2.xNorm!!, 0.03f)
    }

    @Test fun invalidFramesAreHandled() {
        assertFalse(detector.detect(Frame(0, 0, 0, 0, ByteArray(0))).detected)
        assertFalse(detector.detect(Frame(0, 0, 10, 10, ByteArray(5))).detected)
    }

    @Test fun repeatedRunsAreDeterministic() {
        val w = 120; val h = 120
        val g = blank(w, h, 40).also { plus(it, w, h, 58, 61, 6, 230) }
        val a = detector.detect(frame(g, w, h))
        val b = detector.detect(frame(g, w, h))
        assertEquals(a.xNorm, b.xNorm); assertEquals(a.confidence, b.confidence, 0f)
    }
}
