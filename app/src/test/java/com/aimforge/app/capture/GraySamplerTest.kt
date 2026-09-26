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
        assertEquals(80, frame.height)
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