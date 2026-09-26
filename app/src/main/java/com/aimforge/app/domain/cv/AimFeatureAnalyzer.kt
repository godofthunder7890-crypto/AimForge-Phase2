package com.aimforge.app.domain.cv

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

sealed interface AimFeatureResult {
    data class Computed(
        val errorXNorm: Float,
        val errorYNorm: Float,
        val errorNorm: Float,
        val directionDeg: Float
    ) : AimFeatureResult

    data class Insufficient(val reason: String) : AimFeatureResult
}

interface AimFeatureAnalyzer {
    fun analyze(crosshair: CrosshairDetection, target: TargetDetection): AimFeatureResult
}

/**
 * Crosshair-to-target error, computed only when both detections are confident and close enough in
 * time to describe the same moment. Phase 4 ships [NoOpTargetDetector], so in practice every call
 * returns [AimFeatureResult.Insufficient] today; the math here is real and unit-tested so Phase 5/6
 * can consume it directly once a real target detector exists.
 */
class DefaultAimFeatureAnalyzer(
    private val minConfidence: Float = 0.3f,
    private val maxTimeSkewMs: Long = 100L
) : AimFeatureAnalyzer {
    override fun analyze(crosshair: CrosshairDetection, target: TargetDetection): AimFeatureResult {
        if (!crosshair.detected || crosshair.xNorm == null || crosshair.yNorm == null) {
            return AimFeatureResult.Insufficient("Crosshair not detected.")
        }
        if (!target.detected || target.xNorm == null || target.yNorm == null) {
            return AimFeatureResult.Insufficient("Target not detected.")
        }
        if (crosshair.confidence < minConfidence) return AimFeatureResult.Insufficient("Crosshair confidence too low.")
        if (target.confidence < minConfidence) return AimFeatureResult.Insufficient("Target confidence too low.")
        if (abs(crosshair.timestampMs - target.timestampMs) > maxTimeSkewMs) {
            return AimFeatureResult.Insufficient("Crosshair and target detections are not time-aligned.")
        }
        val dx = target.xNorm - crosshair.xNorm
        val dy = target.yNorm - crosshair.yNorm
        val err = sqrt(dx * dx + dy * dy)
        val dir = ((Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()) + 360f) % 360f
        return AimFeatureResult.Computed(dx, dy, err, dir)
    }
}