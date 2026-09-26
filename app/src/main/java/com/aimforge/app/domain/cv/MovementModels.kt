package com.aimforge.app.domain.cv

/** Movement between two accepted crosshair detections. Distances/speed are in normalized units (see [Coordinates]). */
data class MovementSample(
    val fromFrame: Int,
    val toFrame: Int,
    val dtMs: Long,
    val deltaXNorm: Float,
    val deltaYNorm: Float,
    val distanceNorm: Float,
    val speedNormPerSec: Float,
    val directionDeg: Float
)

/** What the temporal tracker decided about one incoming detection. */
sealed interface TrackEvent {
    data object NoDetection : TrackEvent
    data object FirstDetection : TrackEvent
    data class Movement(val sample: MovementSample) : TrackEvent
    /** Gap since the last detection was too large to treat as continuous motion; no movement is fabricated across it. */
    data class GapTooLarge(val dtMs: Long) : TrackEvent
    /** Implied speed was outside anything a human flick could plausibly produce; treated as detector noise. */
    data class ImplausibleJump(val speedNormPerSec: Float) : TrackEvent
}