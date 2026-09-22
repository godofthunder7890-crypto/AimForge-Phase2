package com.aimforge.app.capture

import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic pixel buffers, used only to test the math. Never shown to the user. */
class FrameSamplerTest {
    private fun frame(w: Int, h: Int, rowStride: Int, fill: (x: Int, y: Int) -> Int): ByteBuffer {
        val buf = ByteBuffer.allocate(rowStride * h)
        for (y in 0 until h) for (x in 0 until w) {
            val v = fill(x, y).toByte()
            val i = y * rowStride + x * 4
            buf.put(i, v); buf.put(i + 1, v); buf.put(i + 2, v); buf.put(i + 3, 0xFF.toByte())
        }
        return buf
    }

    @Test fun allBlackFrameIsBlank() {
        val s = FrameSampler.sample(frame(64, 32, 64 * 4) { _, _ -> 0 }, 64, 32, 64 * 4, 4)
        assertTrue(s.isBlank); assertEquals(0, s.maxLuma)
    }

    @Test fun normalFrameIsNotBlank() {
        val s = FrameSampler.sample(frame(64, 32, 64 * 4) { x, _ -> if (x > 32) 200 else 20 }, 64, 32, 64 * 4, 4)
        assertFalse(s.isBlank)
        assertTrue(s.maxLuma >= 190)
    }

    @Test fun darkButRealFrameIsNotCalledBlank() {
        val s = FrameSampler.sample(frame(64, 32, 64 * 4) { _, _ -> 40 }, 64, 32, 64 * 4, 4)
        assertFalse(s.isBlank)
        assertEquals(40, s.meanLuma)
    }

    @Test fun rowPaddingAndSmallBuffersDoNotCrash() {
        val stride = 64 * 4 + 48
        FrameSampler.sample(frame(64, 32, stride) { _, _ -> 100 }, 64, 32, stride, 4)
        FrameSampler.sample(ByteBuffer.allocate(8), 64, 32, 256, 4)   // truncated buffer: skipped, no exception
    }
}
