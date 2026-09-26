package com.aimforge.app.domain.cv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fake detector so the engine's state machine is tested in isolation from the real heuristic. */
private class ScriptedDetector(private val script: List<CrosshairDetection>) : CrosshairVisionDetector {
    override val version = "scripted-test-v1"
    private var i = 0
    override fun detect(frame: Frame): CrosshairDetection {
        val d = script.getOrElse(i) { script.last() }
        i++
        return d
    }
}

private fun detected(frame: Int, x: Float, conf: Float) =
    CrosshairDetection(frame, frame * 100L, true, x, 0.5f, confidence = conf, method = "scripted-test-v1")
private fun missed(frame: Int) = CrosshairDetection.notDetected(frame, frame * 100L, "scripted-test-v1")
private fun frame(i: Int) = Frame(i, i * 100L, 4, 4, ByteArray(16))

class CvAnalysisEngineTest {
    @Test fun noFramesIsNoData() {
        val engine = CvAnalysisEngine(crosshairDetector = ScriptedDetector(emptyList()), clock = { 5000L })
        val r = engine.finalizeAnalysis("s1", droppedFrames = 0)
        assertEquals(CvAnalysisState.NO_DATA, r.state)
        assertEquals(0, r.framesProcessed)
        assertNull(r.crosshairDetectionRate)
        assertEquals(5000L, r.computedAtMs)
    }

    @Test fun fewFramesIsInsufficientData() {
        val script = (1..5).map { detected(it, 0.5f, 0.9f) }
        val engine = CvAnalysisEngine(crosshairDetector = ScriptedDetector(script), minFramesForAnalysis = 20)
        repeat(5) { engine.onFrame(frame(it)) }
        assertEquals(CvAnalysisState.INSUFFICIENT_DATA, engine.finalizeAnalysis("s2", 0).state)
    }

    @Test fun neverDetectedIsCrosshairNotDetected() {
        val script = (1..30).map { missed(it) }
        val engine = CvAnalysisEngine(crosshairDetector = ScriptedDetector(script), minFramesForAnalysis = 10)
        repeat(30) { engine.onFrame(frame(it)) }
        val r = engine.finalizeAnalysis("s3", 0)
        assertEquals(CvAnalysisState.CROSSHAIR_NOT_DETECTED, r.state)
        assertEquals(0, r.framesWithCrosshair)
        assertEquals(0f, r.crosshairDetectionRate)
        assertEquals(30, r.missedDetections)
    }

    @Test fun lowConfidenceDetectionsAreReportedAsLowConfidence() {
        val script = (1..30).map { detected(it, 0.5f, 0.1f) }
        val engine = CvAnalysisEngine(crosshairDetector = ScriptedDetector(script), minFramesForAnalysis = 10)
        repeat(30) { engine.onFrame(frame(it)) }
        val r = engine.finalizeAnalysis("s4", 0)
        assertEquals(CvAnalysisState.LOW_CONFIDENCE, r.state)
        assertEquals(0.1f, r.avgCrosshairConfidence!!, 0.001f)
    }

    @Test fun infrequentDetectionIsPartialAnalysis() {
        val script = (1..30).map { i -> if (i % 3 == 0) detected(i, 0.5f, 0.9f) else missed(i) }
        val engine = CvAnalysisEngine(crosshairDetector = ScriptedDetector(script), minFramesForAnalysis = 10)
        repeat(30) { engine.onFrame(frame(it)) }
        val r = engine.finalizeAnalysis("s5", 0)
        assertEquals(CvAnalysisState.PARTIAL_ANALYSIS, r.state)
        assertTrue(r.crosshairDetectionRate!! < 0.5f)
    }

    @Test fun reliableDetectionWithMovementIsAnalyzed() {
        val script = (1..30).map { i -> detected(i, 0.4f + i * 0.001f, 0.8f) }
        val engine = CvAnalysisEngine(crosshairDetector = ScriptedDetector(script), minFramesForAnalysis = 10)
        repeat(30) { engine.onFrame(frame(it)) }
        val r = engine.finalizeAnalysis("s6", droppedFrames = 12)
        assertEquals(CvAnalysisState.ANALYZED, r.state)
        assertEquals(30, r.framesProcessed)
        assertEquals(1f, r.crosshairDetectionRate!!, 0.001f)
        assertTrue(r.movementSamples > 0)
        assertTrue(r.avgMovementSpeedNormPerSec!! > 0f)
        assertEquals(12, r.droppedFrames)
        assertEquals("scripted-test-v1", r.algorithmVersion)
    }

    @Test fun targetIsAlwaysZeroWithTheNoOpDetector() {
        val script = (1..25).map { detected(it, 0.5f, 0.9f) }
        val engine = CvAnalysisEngine(crosshairDetector = ScriptedDetector(script), minFramesForAnalysis = 10)
        repeat(25) { engine.onFrame(frame(it)) }
        val r = engine.finalizeAnalysis("s7", 0)
        assertEquals(0, r.framesWithTarget)
        assertEquals(0f, r.targetDetectionRate)
    }

    @Test fun sessionIdIsCarriedThroughUnchanged() {
        val engine = CvAnalysisEngine(crosshairDetector = ScriptedDetector(emptyList()))
        assertEquals("my-session-42", engine.finalizeAnalysis("my-session-42", 0).sessionId)
    }
}