package com.aimforge.app.data

import com.aimforge.app.domain.cv.CvAnalysisStore
import com.aimforge.app.domain.cv.CvSessionAnalysis

class RoomCvAnalysisStore(private val db: AppDatabase) : CvAnalysisStore {
    override suspend fun save(result: CvSessionAnalysis) {
        db.cvAnalysisDao().upsert(result.toEntity())
    }
}

private fun CvSessionAnalysis.toEntity() = CvAnalysisEntity(
    sessionId = sessionId,
    state = state.name,
    framesProcessed = framesProcessed,
    framesWithCrosshair = framesWithCrosshair,
    crosshairDetectionRate = crosshairDetectionRate,
    avgCrosshairConfidence = avgCrosshairConfidence,
    movementSamples = movementSamples,
    avgMovementSpeedNormPerSec = avgMovementSpeedNormPerSec,
    totalMovementDistanceNorm = totalMovementDistanceNorm,
    rejectedJumps = rejectedJumps,
    missedDetections = missedDetections,
    longestGapMs = longestGapMs,
    framesWithTarget = framesWithTarget,
    targetDetectionRate = targetDetectionRate,
    droppedFrames = droppedFrames,
    algorithmVersion = algorithmVersion,
    computedAtMs = computedAtMs
)