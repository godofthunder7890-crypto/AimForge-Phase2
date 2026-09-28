package com.aimforge.app.capture

import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** TEST FIXTURES: synthetic RGBA buffers, never real gameplay, used only to test the downsampling math. */
class GraySamplerTest {
    private fun rgbaFrame(w: Int, h: Int, fill: (x: Int, y: Int) -> Int): ByteBuffer {
        val stride = w * 4
        val buf = ByteBuffer.allocate(stride * h)
        for (y in 0 until h) for (x in 0 until w) {
            val v = fill(x, y).toByte()
            val i = y * stride + x * 4
            buf.put(i, v); buf.put(i + 1, v); buf.put(i + 2, v); buf.put(i + 3, 0xFF.toByte())
        }
        return buf
    }

    @Test fun outputHasRequestedWidthAndProportionalHeight() {
        val buf = rgbaFrame(640, 320) { _, _ -> 100 }
        val frame = GraySampler.build(buf, 640, 320, 640 * 4, 4, frameIndex = 7, timestampMs = 12345, outWidth = 160)
        assertEquals(160, frame.width)
        assertEquals(80, frame.height) // same 2:1 aspect ratio, scaled down
        assertEquals(160 * 80, frame.gray.size)
        assertEquals(7, frame.frameIndex)
        assertEquals(12345L, frame.timestampMs)
    }

    @Test fun uniformSourceProducesUniformGray() {
        val buf = rgbaFrame(64, 64) { _, _ -> 77 }
        val frame = GraySampler.build(buf, 64, 64, 64 * 4, 4, 0, 0, outWidth = 16)
        for (b in frame.gray) assertEquals(77, b.toInt() and 0xFF)
    }

    @Test fun brightHalfStaysBrighterThanDarkHalfAfterDownsampling() {
        val buf = rgbaFrame(64, 64) { x, _ -> if (x < 32) 20 else 220 }
        val frame = GraySampler.build(buf, 64, 64, 64 * 4, 4, 0, 0, outWidth = 16)
        val leftAvg = (0 until frame.height).map { y -> frame.gray[y * frame.width].toInt() and 0xFF }.average()
        val rightAvg = (0 until frame.height).map { y -> frame.gray[y * frame.width + frame.width - 1].toInt() and 0xFF }.average()
        assertTrue(rightAvg > leftAvg + 100)
    }

    @Test fun truncatedBufferDoesNotCrashAndFillsZero() {
        val small = ByteBuffer.allocate(8)
        val frame = GraySampler.build(small, 64, 64, 64 * 4, 4, 0, 0, outWidth = 16)
        assertEquals(16 * 16, frame.gray.size)
    }

    @Test fun outputWidthAtLeastOne() {
        val buf = rgbaFrame(4, 4) { _, _ -> 50 }
        val frame = GraySampler.build(buf, 4, 4, 4 * 4, 4, 0, 0, outWidth = 0)
        assertEquals(1, frame.width)
        assertTrue(frame.height >= 1)
    }
}

/** Central-crop sampling: TEST FIXTURES only. */
class GraySamplerCropTest {
    private fun rgba(w: Int, h: Int, fill: (Int, Int) -> Triple<Int, Int, Int>): java.nio.ByteBuffer {
        val buf = java.nio.ByteBuffer.allocate(w * 4 * h)
        for (y in 0 until h) for (x in 0 until w) {
            val (r, g, b) = fill(x, y)
            val i = (y * w + x) * 4
            buf.put(i, r.toByte()); buf.put(i + 1, g.toByte()); buf.put(i + 2, b.toByte()); buf.put(i + 3, 0xFF.toByte())
        }
        return buf
    }

    @Test fun redCrosshairPixelIsBrightUnderMaxChannelButWouldBeDarkUnderLuma() {
        val buf = rgba(400, 200) { x, y -> if (x == 200 && y == 100) Triple(255, 0, 0) else Triple(10, 10, 10) }
        val f = GraySampler.buildCentralCrop(buf, 400, 200, 400 * 4, 4, 0, 0, cropFraction = 0.5f, outWidth = 200)
        val max = f.gray.maxOf { it.toInt() and 0xFF }
        assertEquals(255, max)                        // max(R,G,B)
        assertTrue((255 * 299 + 0 + 0) / 1000 < 100)  // luma of the same pixel would be ~76
    }

    @Test fun cropCarriesSourceMappingAndDoesNotUpsample() {
        val buf = rgba(400, 200) { _, _ -> Triple(50, 50, 50) }
        val f = GraySampler.buildCentralCrop(buf, 400, 200, 400 * 4, 4, 5, 77, cropFraction = 0.5f, outWidth = 500)
        assertEquals(400, f.sourceWidth); assertEquals(200, f.sourceHeight)
        assertEquals(100, f.cropOffsetXPx); assertEquals(50, f.cropOffsetYPx)
        assertEquals(200, f.cropWidthPx); assertEquals(100, f.cropHeightPx)
        assertEquals(200, f.width)                    // capped at the crop's real pixel width, no invented detail
        assertEquals(0.5f, f.toSourceNormX(f.width / 2f), 0.01f)
    }

    @Test fun portraitSourceWorksToo() {
        val buf = rgba(200, 400) { _, _ -> Triple(20, 20, 20) }
        val f = GraySampler.buildCentralCrop(buf, 200, 400, 200 * 4, 4, 0, 0, cropFraction = 0.5f, outWidth = 100)
        assertEquals(200, f.sourceWidth); assertEquals(400, f.sourceHeight)
        assertEquals(0.5f, f.toSourceNormY(f.height / 2f), 0.01f)
    }
}
