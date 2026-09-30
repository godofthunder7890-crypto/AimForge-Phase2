package com.aimforge.app.domain.metrics

import com.aimforge.app.domain.cv.FrameObservation

/**
 * Batch convenience wrapper around [StreamingAimMetricsAccumulator]: feeds a full list of
 * observations through a fresh accumulator in order and returns the final result. Kept for callers
 * (and tests) that already have a complete, bounded list of observations in hand — e.g. a short
 * session, or a synthetic fixture. The underlying math is identical to the streaming path used
 * directly by [com.aimforge.app.domain.cv.CvAnalysisEngine] for real captures of any length; see
 * that class's doc comment for why the streaming path exists.
 */
class AimMetricsEngine(
    private val minConfidence: Float = 0.3f,
    private val directionDeadZoneNorm: Float = 0.004f,
    private val microAdjustmentMaxNorm: Float = 0.012f,
    private val maxGapMs: Long = 1500L,
    private val closeToTargetNorm: Float = 0.05f,
    private val maxReacquireMs: Long = 800L,
    private val minFramesForAnalysis: Int = 20,
    private val minMovementPairsForAnalysis: Int = 5,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    fun analyze(sessionId: String, observations: List<FrameObservation>, droppedFrames: Int): AimMetricsResult {
        val accumulator = StreamingAimMetricsAccumulator(
            minConfidence = minConfidence,
            directionDeadZoneNorm = directionDeadZoneNorm,
            microAdjustmentMaxNorm = microAdjustmentMaxNorm,
            maxGapMs = maxGapMs,
            closeToTargetNorm = closeToTargetNorm,
            maxReacquireMs = maxReacquireMs,
            minFramesForAnalysis = minFramesForAnalysis,
            minMovementPairsForAnalysis = minMovementPairsForAnalysis,
            clock = clock
        )
        observations.forEach { accumulator.onObservation(it) }
        return accumulator.finalizeResult(sessionId, droppedFrames)
    }
}
