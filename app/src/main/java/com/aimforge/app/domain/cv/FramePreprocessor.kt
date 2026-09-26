package com.aimforge.app.domain.cv

interface FramePreprocessor {
    fun process(frame: Frame): Frame
}

/** Min-max contrast stretch. Helps the detector on dim or washed-out frames; a no-op on an already-flat frame. */
class HistogramStretchPreprocessor : FramePreprocessor {
    override fun process(frame: Frame): Frame {
        if (frame.gray.isEmpty()) return frame
        var min = 255
        var max = 0
        for (b in frame.gray) {
            val v = b.toInt() and 0xFF
            if (v < min) min = v
            if (v > max) max = v
        }
        if (max <= min) return frame
        val range = (max - min).toFloat()
        val out = ByteArray(frame.gray.size)
        for (i in frame.gray.indices) {
            val v = frame.gray[i].toInt() and 0xFF
            val stretched = (((v - min) / range) * 255f).let { if (it < 0f) 0 else if (it > 255f) 255 else it.toInt() }
            out[i] = stretched.toByte()
        }
        return frame.copy(gray = out)
    }
}