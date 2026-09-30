package com.aimforge.app.domain.cv

/** One frame's paired crosshair + target detections, as produced during Phase 4's CV pass. */
data class FrameObservation(
    val frameIndex: Int,
    val timestampMs: Long,
    val crosshair: CrosshairDetection,
    val target: TargetDetection
)
