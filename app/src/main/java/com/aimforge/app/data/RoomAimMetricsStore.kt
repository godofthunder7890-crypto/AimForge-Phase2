package com.aimforge.app.data

import com.aimforge.app.domain.metrics.AimMetricsResult
import com.aimforge.app.domain.metrics.AimMetricsStore

class RoomAimMetricsStore(private val db: AppDatabase) : AimMetricsStore {
    override suspend fun save(result: AimMetricsResult) {
        db.aimMetricsDao().upsert(result.toEntity())
    }
}

private fun AimMetricsResult.toEntity() = AimMetricsEntity(
    sessionId = sessionId,
    status = status.name,
    framesProcessed = quality.framesProcessed,
    validCrosshairSamples = quality.validCrosshairSamples,
    validTargetSamples = quality.validTargetSamples,
    crosshairDetectionRate = quality.crosshairDetectionRate,
    targetDetectionRate = quality.targetDetectionRate,
    droppedFrames = quality.droppedFrames,
    timestampGapCount = quality.timestampGapCount,
    invalidTimestampSamples = quality.invalidTimestampSamples,
    avgCrosshairConfidence = quality.avgCrosshairConfidence,
    minCrosshairConfidence = quality.minCrosshairConfidence,
    trackingDurationMs = quality.trackingDurationMs,
    totalMovementDistance = totalMovementDistance,
    averageMovementDistance = averageMovementDistance,
    medianMovementDistance = medianMovementDistance,
    horizontalMovementTotal = horizontalMovementTotal,
    verticalMovementTotal = verticalMovementTotal,
    horizontalMovementNet = horizontalMovementNet,
    verticalMovementNet = verticalMovementNet,
    averageSpeed = averageSpeed,
    medianSpeed = medianSpeed,
    peakSpeed = peakSpeed,
    directionChangeCount = directionChangeCount,
    directionChangeRate = directionChangeRate,
    averageAcceleration = averageAcceleration,
    peakAcceleration = peakAcceleration,
    peakDeceleration = peakDeceleration,
    velocityVariance = velocityVariance,
    accelerationVariance = accelerationVariance,
    directionReversalRate = directionReversalRate,
    microAdjustmentCount = microAdjustmentCount,
    microAdjustmentFrequency = microAdjustmentFrequency,
    averageMicroAdjustmentMagnitude = averageMicroAdjustmentMagnitude,
    medianMicroAdjustmentMagnitude = medianMicroAdjustmentMagnitude,
    overshootCount = overshootCount,
    correctionCount = correctionCount,
    averageCorrectionMagnitude = averageCorrectionMagnitude,
    averageCorrectionTimeMs = averageCorrectionTimeMs,
    averageTargetError = averageTargetError,
    medianTargetError = medianTargetError,
    minimumTargetError = minimumTargetError,
    maximumTargetError = maximumTargetError,
    targetErrorVariance = targetErrorVariance,
    timeWithinTargetRegionMs = timeWithinTargetRegionMs,
    trackingContinuity = trackingContinuity,
    crosshairObservationCoverage = crosshairObservationCoverage,
    computedAtMs = computedAtMs,
    analysisDurationMs = analysisDurationMs
)
