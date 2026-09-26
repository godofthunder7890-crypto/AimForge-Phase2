package com.aimforge.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.aimforge.app.domain.AnalysisStatus
import com.aimforge.app.domain.CaptureStatus
import com.aimforge.app.domain.SessionState

/** Single-row player profile (id is always 1). Only user-entered values. No defaults, no assumed problems; tests decide. */
@Entity(tableName = "player_profile")
data class PlayerProfileEntity(
    @PrimaryKey val id: Int = 1,
    val deviceName: String,
    val controlLayout: String,
    val gyroEnabled: Boolean,
    val onboardingDone: Boolean
)

/** One manually entered BGMI value: scope x sensitivity type. */
@Entity(tableName = "sensitivity_values", primaryKeys = ["scope", "type"])
data class SensitivityValueEntity(
    val scope: String,
    val type: String,
    val value: Int
)

/**
 * One real test session. Conditions and the sensitivity snapshot are written once at creation and never
 * updated afterwards (the manager only copies state/time fields). null = Unknown / not measured.
 * The analysis fields stay null until a real analysis engine fills them.
 */
@Entity(tableName = "test_sessions")
data class TestSessionEntity(
    @PrimaryKey val sessionId: String,
    val createdAt: Long,
    val startedAt: Long?,
    val endedAt: Long?,
    val pausedAt: Long?,
    val pausedTotalMs: Long,
    val state: String,

    val testType: String,
    val scope: String?,
    val weapon: String?,
    val muzzle: String?,
    val grip: String?,
    val magazine: String?,
    val stock: String?,
    val otherAttachment: String?,

    val distance: String?,
    val targetType: String?,

    val cameraSensitivity: Int?,
    val adsSensitivity: Int?,
    val gyroSensitivity: Int?,
    val adsGyroSensitivity: Int?,

    val fps: Int?,
    val refreshRate: Int?,

    val captureStatus: String,
    val analysisStatus: String,
    val notes: String?,

    // Future analysis output. Null until a real engine produces it.
    val aimScore: Int? = null,
    val aimErrorPx: Float? = null,
    val stability: Int? = null,
    val errorState: String? = null,
    val confidence: Int? = null,
    val recommendation: String? = null,
    /** Real reason a session failed or a capture could not start. null when nothing went wrong. */
    val failureReason: String? = null
) {
    val sessionState: SessionState get() = SessionState.valueOf(state)
    val captureStatusEnum: CaptureStatus get() = CaptureStatus.valueOf(captureStatus)
    val analysisStatusEnum: AnalysisStatus get() = AnalysisStatus.valueOf(analysisStatus)

    /** Still running or waiting to run. Blocks creating a new session. */
    val isActive: Boolean get() = endedAt == null && sessionState.isOpenState

    /** True only when an analysis engine really finished and produced a score. */
    val hasAnalysis: Boolean get() = analysisStatusEnum == AnalysisStatus.ANALYZED && aimScore != null
}

/** Real capture metadata. No frames or video are stored (storageLocation stays null); only counts and timing measured from delivered frames. */
@Entity(tableName = "captures")
data class CaptureEntity(
    @PrimaryKey val captureId: String,
    val sessionId: String,
    val startTime: Long?,
    val endTime: Long?,
    val frameCount: Int?,
    val widthPx: Int?,
    val heightPx: Int?,
    val fps: Float?,
    val storageLocation: String?,
    val permissionGranted: Boolean?,
    val status: String,
    /** Sampled frames (a sparse pixel grid is checked on some frames) and how many of them were completely black. */
    val sampledFrameCount: Int? = null,
    val blankFrameCount: Int? = null,
    val stopReason: String? = null
)

/** Diagnostic batch definition. Each step becomes an independent real session only when it is actually run. */
@Entity(tableName = "diagnostic_batches")
data class DiagnosticBatchEntity(
    @PrimaryKey val batchId: String,
    val createdAt: Long,
    val state: String,
    val weapon: String?,
    val notes: String?
)

@Entity(tableName = "diagnostic_steps", primaryKeys = ["batchId", "stepIndex"])
data class DiagnosticStepEntity(
    val batchId: String,
    val stepIndex: Int,
    val testType: String,
    val scope: String?,
    /** null until this step is run as a real session. */
    val sessionId: String?
)

/**
 * Phase 4 real computer-vision result for one session. One row per session, written once when the
 * capture's analysis finishes. framesWithTarget/targetDetectionRate are always 0/0f today: no real
 * target detector exists yet (see NoOpTargetDetector), and that is stored honestly rather than omitted.
 */
@Entity(tableName = "cv_analyses")
data class CvAnalysisEntity(
    @PrimaryKey val sessionId: String,
    val state: String,
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
