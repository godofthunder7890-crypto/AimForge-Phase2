package com.aimforge.app.domain.metrics

/** Coarse compass direction of one movement step, classified from dx/dy with a dead-zone (see [DirectionClassifier]). */
enum class MovementDirection {
    LEFT, RIGHT, UP, DOWN, UP_LEFT, UP_RIGHT, DOWN_LEFT, DOWN_RIGHT,
    /** Movement distance was inside the dead-zone: too small to call a direction. */
    STATIONARY,
    /** dx/dy could not be classified (e.g. not-a-number); should not occur with real detections. */
    UNKNOWN
}

/** One accepted movement step between two confident, consecutive, time-plausible crosshair detections. */
data class MovementPair(
    val fromFrame: Int,
    val toFrame: Int,
    val dtMs: Long,
    val dx: Float,
    val dy: Float,
    val distance: Float,
    val speed: Float,
    val direction: MovementDirection
)

enum class AimMetricsStatus(val label: String) {
    NO_DATA("No data"),
    INSUFFICIENT_DATA("Insufficient data"),
    /** Reserved: some metric groups computed, others blocked by data problems. Not produced today; missing target data does not cause it. */
    PARTIAL_ANALYSIS("Partial analysis"),
    ANALYZED("Analyzed")
}

/** How much of the input could actually be used, and why. Every metric's trustworthiness can be judged from this. */
data class DataQuality(
    val framesProcessed: Int,
    val validCrosshairSamples: Int,
    val validTargetSamples: Int,
    val crosshairDetectionRate: Float?,
    val targetDetectionRate: Float?,
    val droppedFrames: Int,
    val timestampGapCount: Int,
    val invalidTimestampSamples: Int,
    val avgCrosshairConfidence: Float?,
    val minCrosshairConfidence: Float?,
    val trackingDurationMs: Long?
)

/**
 * Real aim metrics computed from Phase 4's per-frame detections. Every field is either a genuine
 * measurement or null. Null means unavailable / not enough evidence — it is never replaced with 0.
 * A 0 in any field is a real measured zero (e.g. zero net horizontal movement).
 */
data class AimMetricsResult(
    val sessionId: String,
    val status: AimMetricsStatus,
    val quality: DataQuality,

    val totalMovementDistance: Float?,
    val averageMovementDistance: Float?,
    val medianMovementDistance: Float?,

    val horizontalMovementTotal: Float?,
    val verticalMovementTotal: Float?,
    val horizontalMovementNet: Float?,
    val verticalMovementNet: Float?,

    val averageSpeed: Float?,
    val medianSpeed: Float?,
    val peakSpeed: Float?,

    val directionChangeCount: Int?,
    val directionChangeRate: Float?,

    val averageAcceleration: Float?,
    val peakAcceleration: Float?,
    val peakDeceleration: Float?,

    val velocityVariance: Float?,
    val accelerationVariance: Float?,
    val directionReversalRate: Float?,

    val microAdjustmentCount: Int?,
    val microAdjustmentFrequency: Float?,
    val averageMicroAdjustmentMagnitude: Float?,
    val medianMicroAdjustmentMagnitude: Float?,

    val overshootCount: Int?,
    val correctionCount: Int?,
    val averageCorrectionMagnitude: Float?,
    val averageCorrectionTimeMs: Float?,

    val averageTargetError: Float?,
    val medianTargetError: Float?,
    val minimumTargetError: Float?,
    val maximumTargetError: Float?,
    val targetErrorVariance: Float?,
    val timeWithinTargetRegionMs: Long?,

    /**
     * Fraction, in [0,1], of the session's real elapsed time (first to last processed observation)
     * that was spent inside an actually-tracked movement segment (two consecutive confident crosshair
     * detections close enough in time to count as continuous tracking — see AimMetricsEngine's maxGapMs).
     * This is temporal continuity, not raw detection coverage; see [crosshairObservationCoverage] for that.
     */
    val trackingContinuity: Float?,
    /** Fraction of processed frames with a confident crosshair detection. Simple coverage, not continuity. */
    val crosshairObservationCoverage: Float?,

    val computedAtMs: Long,
    val analysisDurationMs: Long?
)
