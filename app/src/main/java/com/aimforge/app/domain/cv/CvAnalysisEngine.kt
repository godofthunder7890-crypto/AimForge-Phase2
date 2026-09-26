package com.aimforge.app.domain.cv

/** Honest analysis states. See [CvAnalysisEngine.finalizeAnalysis] for exactly when each applies. */
enum class CvAnalysisState(val label: String) {
    NO_DATA("No data"),
    PROCESSING("Processing"),
    INSUFFICIENT_DATA("Insufficient data"),
    CROSSHAIR_NOT_DETECTED("Crosshair not detected"),
    TARGET_NOT_DETECTED("Target not detected"),
    LOW_CONFIDENCE("Low confidence"),
    PARTIAL_ANALYSIS("Partial analysis"),
    ANALYZED("Analyzed")
}

/**
 * Stored result of one session's CV pass. "ANALYZED" describes crosshair detection and movement
 * only: [framesWithTarget] and [targetDetectionRate] are always 0 with the Phase 4 detector, since
 * no real target detector exists yet (see [NoOpTargetDetector]) — this is reported honestly rather
 * than left out.
 */
data class CvSessionAnalysis(
    val sessionId: String,
    val state: CvAnalysisState,
    val framesProcessed: Int,
    val framesWithCrosshair: Int,
    val crosshairDetectionRate: Float?,
    val avgCrosshairConfidence: Float?,
    val movementSamples: Int,
    val avgMovementSpeedNormPerSec: Float?,
    val totalMovementDistanceNorm: Float?,
    val rejectedJumps: Int,
    val missedDetections: Int,
    val longestGapMs: Long?,
    val framesWithTarget: Int,
    val targetDetectionRate: Float?,
    val droppedFrames: Int,
    val algorithmVersion: String,
    val computedAtMs: Long
)

/**
 * Orchestrates FramePreprocessor -> VisionDetector -> TemporalTracker across a real capture and
 * accumulates only real, measured counts. One instance is used for exactly one session: create a
 * fresh engine per capture (state is not meant to be reused or shared across sessions).
 */
class CvAnalysisEngine(
    private val preprocessor: FramePreprocessor = HistogramStretchPreprocessor(),
    private val crosshairDetector: CrosshairVisionDetector = HeuristicCrosshairDetector(),
    private val targetDetector: TargetVisionDetector = NoOpTargetDetector(),
    private val tracker: TemporalTracker = CrosshairTemporalTracker(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val minFramesForAnalysis: Int = 20,
    private val crosshairDetectedRateFloor: Float = 0.05f,
    private val lowConfidenceThreshold: Float = 0.4f,
    private val partialRateThreshold: Float = 0.5f
) {
    private var framesProcessed = 0
    private var framesWithCrosshair = 0
    private var confidenceSum = 0.0
    private var movementSamples = 0
    private var movementDistanceSum = 0.0
    private var movementSpeedSum = 0.0
    private var rejectedJumps = 0
    private var missedDetections = 0
    private var longestGapMs = 0L
    private var framesWithTarget = 0

    /** Feed one real, already-downsampled frame. Call from a background thread; this does real per-pixel work. */
    fun onFrame(rawFrame: Frame) {
        framesProcessed++
        val frame = preprocessor.process(rawFrame)

        val crosshair = crosshairDetector.detect(frame)
        if (crosshair.detected) {
            framesWithCrosshair++
            confidenceSum += crosshair.confidence
        }

        val target = targetDetector.detect(frame)
        if (target.detected) framesWithTarget++

        when (val event = tracker.accept(crosshair)) {
            is TrackEvent.Movement -> {
                movementSamples++
                movementDistanceSum += event.sample.distanceNorm
                movementSpeedSum += event.sample.speedNormPerSec
            }
            is TrackEvent.ImplausibleJump -> rejectedJumps++
            is TrackEvent.GapTooLarge -> if (event.dtMs > longestGapMs) longestGapMs = event.dtMs
            TrackEvent.NoDetection -> missedDetections++
            TrackEvent.FirstDetection -> Unit
        }
    }

    /**
     * Call once, after the capture has ended. [droppedFrames] is the number of frames the capture
     * really delivered but this engine never saw (throttled for CPU cost) — supplied by the caller
     * from real capture counters, never guessed here.
     */
    fun finalizeAnalysis(sessionId: String, droppedFrames: Int): CvSessionAnalysis {
        val rate = if (framesProcessed > 0) framesWithCrosshair.toFloat() / framesProcessed else null
        val avgConfidence = if (framesWithCrosshair > 0) (confidenceSum / framesWithCrosshair).toFloat() else null
        val targetRate = if (framesProcessed > 0) framesWithTarget.toFloat() / framesProcessed else null
        val avgSpeed = if (movementSamples > 0) (movementSpeedSum / movementSamples).toFloat() else null

        val state = when {
            framesProcessed == 0 -> CvAnalysisState.NO_DATA
            framesProcessed < minFramesForAnalysis -> CvAnalysisState.INSUFFICIENT_DATA
            rate != null && rate < crosshairDetectedRateFloor -> CvAnalysisState.CROSSHAIR_NOT_DETECTED
            avgConfidence != null && avgConfidence < lowConfidenceThreshold -> CvAnalysisState.LOW_CONFIDENCE
            rate != null && rate < partialRateThreshold -> CvAnalysisState.PARTIAL_ANALYSIS
            else -> CvAnalysisState.ANALYZED
        }

        return CvSessionAnalysis(
            sessionId = sessionId,
            state = state,
            framesProcessed = framesProcessed,
            framesWithCrosshair = framesWithCrosshair,
            crosshairDetectionRate = rate,
            avgCrosshairConfidence = avgConfidence,
            movementSamples = movementSamples,
            avgMovementSpeedNormPerSec = avgSpeed,
            totalMovementDistanceNorm = if (movementSamples > 0) movementDistanceSum.toFloat() else null,
            rejectedJumps = rejectedJumps,
            missedDetections = missedDetections,
            longestGapMs = longestGapMs.takeIf { it > 0 },
            framesWithTarget = framesWithTarget,
            targetDetectionRate = targetRate,
            droppedFrames = droppedFrames,
            algorithmVersion = crosshairDetector.version,
            computedAtMs = clock()
        )
    }
}