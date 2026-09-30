package com.aimforge.app.domain.metrics

import com.aimforge.app.domain.cv.CrosshairDetection
import com.aimforge.app.domain.cv.FrameObservation
import com.aimforge.app.domain.cv.TargetDetection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Synthetic numeric observation fixtures for pure algorithm tests; NOT real gameplay. */
class StreamingAccumulatorTest {
    private fun obs(i: Int, t: Long, x: Float?, conf: Float = 0.9f): FrameObservation {
        val c = if (x == null) CrosshairDetection.notDetected(i, t, "test")
        else CrosshairDetection(i, t, true, x, 0.5f, confidence = conf, method = "test")
        return FrameObservation(i, t, c, TargetDetection.notDetected(i, t, "none"))
    }

    @Test fun longSessionFarBeyondOldWindowKeepsWholeSession() {
        // 5000 frames > old 1200 cap. Total distance must include the BEGINNING of the session.
        val acc = StreamingAimMetricsAccumulator(minFramesForAnalysis = 20)
        var x = 0.2f
        for (i in 0 until 5000) {
            x += if (i % 2 == 0) 0.001f else -0.0005f   // net drift +0.00025 per 2 frames, known step sizes
            acc.onObservation(obs(i, i * 30L, x))
        }
        val r = acc.finalizeResult("long")
        assertEquals(5000, r.quality.framesProcessed)
        // 2500 steps of 0.001 + 2499 steps of 0.0005 (first step has no predecessor) = 2500*0.001+2499*0.0005 - one missing
        val expected = 2499 * 0.001f + 2500 * 0.0005f
        assertEquals(expected, r.totalMovementDistance!!, 0.05f)
    }

    @Test fun streamingMatchesBatchEngineExactlyOnSameInput() {
        val list = (0 until 200).map { obs(it, it * 50L, 0.3f + (it % 17) * 0.004f) }
        val batch = AimMetricsEngine(clock = { 1L }).analyze("s", list, 3)
        val acc = StreamingAimMetricsAccumulator(clock = { 1L })
        list.forEach { acc.onObservation(it) }
        val stream = acc.finalizeResult("s", 3)
        assertEquals(batch.totalMovementDistance!!, stream.totalMovementDistance!!, 1e-6f)
        assertEquals(batch.medianSpeed!!, stream.medianSpeed!!, 1e-6f)
        assertEquals(batch.directionChangeCount, stream.directionChangeCount)
    }

    @Test fun trackingContinuityIsTemporalNotDetectionRate() {
        // 10 confident frames at 100ms, then a 5s gap (no movement bridged), then 10 more.
        val acc = StreamingAimMetricsAccumulator(minFramesForAnalysis = 5, minMovementPairsForAnalysis = 3)
        for (i in 0 until 10) acc.onObservation(obs(i, i * 100L, 0.4f + i * 0.005f))
        for (i in 10 until 20) acc.onObservation(obs(i, 6000L + (i - 10) * 100L, 0.5f + (i - 10) * 0.005f))
        val r = acc.finalizeResult("gap")
        assertEquals(1, r.quality.timestampGapCount)
        // tracked = 9 steps*100 + 9 steps*100 = 1800ms over a 6900ms span
        assertEquals(1800f / 6900f, r.trackingContinuity!!, 0.01f)
        // coverage is a different number: 20/20 = 100%
        assertEquals(1f, r.crosshairObservationCoverage!!, 0.001f)
    }

    @Test fun continuityNullWhenNoTimeSpan() {
        val r = StreamingAimMetricsAccumulator().finalizeResult("x")
        assertNull(r.trackingContinuity)
        assertEquals(AimMetricsStatus.NO_DATA, r.status)
    }

    @Test fun reservoirIsExactWhenStreamFitsAndCoversWholeStreamWhenItDoesNot() {
        val small = ReservoirSampler(100, Random(1))
        (1..50).forEach { small.offer(it.toFloat()) }
        assertEquals(25.5f, small.median()!!, 0.001f)

        val big = ReservoirSampler(500, Random(7))
        (1..100_000).forEach { big.offer(it.toFloat()) }
        assertEquals(500, big.sampleSize)
        assertEquals(100_000L, big.totalSeen)
        // uniform over the WHOLE stream: median near 50k, not near the tail (a FIFO window would give ~99.8k)
        assertTrue(big.median()!! in 40_000f..60_000f)
    }

    @Test fun lowConfidenceFramesNeverBecomeMovement() {
        val acc = StreamingAimMetricsAccumulator(minFramesForAnalysis = 5)
        for (i in 0 until 30) acc.onObservation(obs(i, i * 100L, 0.3f + i * 0.01f, conf = 0.1f))
        val r = acc.finalizeResult("lc")
        assertEquals(0, r.quality.validCrosshairSamples)
        assertEquals(AimMetricsStatus.INSUFFICIENT_DATA, r.status)
        assertNull(r.totalMovementDistance)
    }

    @Test fun rejectedNonPositiveTimestampsAreCountedNotUsed() {
        val acc = StreamingAimMetricsAccumulator(minFramesForAnalysis = 3, minMovementPairsForAnalysis = 1)
        acc.onObservation(obs(0, 1000, 0.5f))
        acc.onObservation(obs(1, 1000, 0.6f))
        acc.onObservation(obs(2, 900, 0.7f))
        acc.onObservation(obs(3, 1100, 0.71f))
        val r = acc.finalizeResult("ts")
        assertEquals(2, r.quality.invalidTimestampSamples)
    }
}
