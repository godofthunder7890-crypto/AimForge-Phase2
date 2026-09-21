package com.aimforge.app.domain

/** Lifecycle of one real test session. Only transitions listed here are legal. */
enum class SessionState(val label: String) {
    DRAFT("Draft"),
    READY("Ready"),
    CAPTURE_PENDING("Waiting"),
    CAPTURING("Capturing"),
    CAPTURE_COMPLETE("Captured"),
    ANALYSIS_PENDING("Analysis pending"),
    ANALYZING("Analyzing"),
    ANALYZED("Analyzed"),
    FAILED("Failed"),
    CANCELLED("Cancelled");

    /** States that can still be left. */
    val allowedNext: Set<SessionState>
        get() = when (this) {
            DRAFT -> setOf(READY, CANCELLED)
            READY -> setOf(CAPTURE_PENDING, CANCELLED)
            CAPTURE_PENDING -> setOf(CAPTURING, FAILED, CANCELLED)
            CAPTURING -> setOf(CAPTURE_COMPLETE, FAILED, CANCELLED)
            CAPTURE_COMPLETE -> setOf(ANALYSIS_PENDING, FAILED)
            ANALYSIS_PENDING -> setOf(ANALYZING, FAILED, CANCELLED)
            ANALYZING -> setOf(ANALYZED, FAILED)
            ANALYZED, FAILED, CANCELLED -> emptySet()
        }

    fun canGoTo(next: SessionState): Boolean = next in allowedNext

    /** No further transitions possible. */
    val isTerminal: Boolean get() = allowedNext.isEmpty()

    /** States in which a session (if not ended) blocks creating another one. */
    val isOpenState: Boolean get() = this == READY || this == CAPTURE_PENDING || this == CAPTURING
}

enum class CaptureStatus(val label: String) {
    NOT_STARTED("Waiting"),
    NOT_AVAILABLE("Not available yet"),
    CAPTURING("Capturing"),
    CAPTURED("Captured"),
    FAILED("Failed")
}

enum class AnalysisStatus(val label: String) {
    NOT_ANALYZED("Not analyzed"),
    PENDING_CAPTURE("Pending capture"),
    PENDING("Analysis pending"),
    ANALYZING("Analyzing"),
    ANALYZED("Analyzed"),
    FAILED("Failed")
}

enum class Distance(val label: String) { CLOSE("Close"), MEDIUM("Medium"), LONG("Long") }

enum class TargetType(val label: String) { STATIONARY("Stationary"), MOVING("Moving") }

/** UI suggestions only. Whatever the user picks is stored as typed; null always means Unknown. */
object SessionOptions {
    const val NONE = "None"
    const val OTHER = "Other"
    val weapons = listOf("M416", "SCAR-L", "AUG", "AKM", "UMP45", "UZI", OTHER)
    val muzzle = listOf("Compensator", "Flash Hider", "Suppressor")
    val grip = listOf("Vertical", "Angled", "Half", "Thumb", "Lightweight", "Laser Sight")
    val magazine = listOf("Extended", "Quickdraw", "Extended + Quickdraw")
    val stock = listOf("Tactical Stock")
    val fps = listOf(30, 40, 60, 90, 120)
    val refreshRates = listOf(60, 90, 120, 144)
    const val MAX_SENSITIVITY = 400
    const val MAX_FPS_OR_HZ = 500
}

/** In-memory form state. A draft is never written to the database; only a READY session is. */
data class SessionDraft(
    val testType: TestMode,
    val scope: ScopeType? = null,
    val weapon: String? = null,
    val muzzle: String? = null,
    val grip: String? = null,
    val magazine: String? = null,
    val stock: String? = null,
    val otherAttachment: String? = null,
    val distance: Distance? = null,
    val targetType: TargetType? = null,
    val cameraSensitivity: Int? = null,
    val adsSensitivity: Int? = null,
    val gyroSensitivity: Int? = null,
    val adsGyroSensitivity: Int? = null,
    val fps: Int? = null,
    val refreshRate: Int? = null,
    val notes: String? = null
)

/** Planned steps of the Full Diagnostic. A plan is a definition, not data; results only ever come from real sessions. */
object DiagnosticPlan {
    data class Step(val mode: TestMode)

    val steps: List<Step> = listOf(
        Step(TestMode.RED_DOT_SPRAY),
        Step(TestMode.SPRAY_3X),
        Step(TestMode.SPRAY_4X),
        Step(TestMode.MID_TRACKING)
    )
}

/** Pure elapsed-time math so it can be persisted and unit tested. */
object SessionClock {
    /**
     * startedAt null -> 0. While paused (pausedAt != null, not ended) time is frozen at pausedAt.
     * After the end, endedAt is the cut-off. pausedTotalMs is time already spent in completed pauses.
     */
    fun elapsedMs(startedAt: Long?, pausedAt: Long?, pausedTotalMs: Long, endedAt: Long?, now: Long): Long {
        if (startedAt == null) return 0L
        val end = endedAt ?: pausedAt ?: now
        return (end - startedAt - pausedTotalMs).coerceAtLeast(0L)
    }
}
