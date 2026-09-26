package com.aimforge.app.domain.cv

import org.junit.Assert.assertEquals
import org.junit.Test

class FramePreprocessorTest {
    private val pre = HistogramStretchPreprocessor()

    @Test fun stretchesRangeToFullZeroTo255() {
        val g = byteArrayOf(50.toByte(), 100.toByte(), 150.toByte())
        val frame = Frame(0, 0, 3, 1, g)
        val out = pre.process(frame)
        assertEquals(0, out.gray[0].toInt() and 0xFF)
        assertEquals(255, out.gray[2].toInt() and 0xFF)
    }

    @Test fun flatFrameIsUnchanged() {
        val g = ByteArray(9) { 90 }
        val frame = Frame(0, 0, 3, 3, g)
        val out = pre.process(frame)
        for (b in out.gray) assertEquals(90, b.toInt() and 0xFF)
    }

    @Test fun emptyFrameIsHandled() {
        val frame = Frame(0, 0, 0, 0, ByteArray(0))
        assertEquals(0, pre.process(frame).gray.size)
    }
}