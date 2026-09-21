package com.aimforge.app.domain

import com.aimforge.app.data.TestSessionEntity
import java.util.UUID

/** Storage seam. Room implements it in the app; tests use an in-memory fake. */
interface SessionStore {
    suspend fun insert(session: TestSessionEntity)
    suspend fun get(sessionId: String): TestSessionEntity?
    suspend fun update(session: TestSessionEntity)
    suspend fun delete(sessionId: String)
    suspend fun findActive(): TestSessionEntity?
}

sealed interface SessionResult {
    data class Ok(val session: TestSessionEntity, val notice: String? = null) : SessionResult
    data class Rejected(val reason: String) : SessionResult
    data object NotFound : SessionResult
}

/**
 * Owns every legal session change.
 *
 * Lock rule: conditions and the sensitivity snapshot are written once in [createSession].
 * Every later operation only copies state / time / status fields, so they cannot drift.
 *
 * Cancel rule: a persisted session is never silently dropped. Cancelling marks it CANCELLED and keeps it
 * in history. Only an explicit [delete] removes a record. A draft (in-memory form) is never persisted.
 */
class SessionManager(
    private val store: SessionStore,
    private val captureEngine: CaptureEngine,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val newId: () -> String = { UUID.randomUUID().toString() }
) {

    suspend fun createSession(draft: SessionDraft): SessionResult {
        validate(draft)?.let { return SessionResult.Rejected(it) }
        if (store.findActive() != null) {
            return SessionResult.Rejected("Another session is still open. Finish or cancel it first.")
        }
        val session = TestSessionEntity(
            sessionId = newId(),
            createdAt = clock(),
            startedAt = null,
            endedAt = null,
            pausedAt = null,
            pausedTotalMs = 0L,
            state = SessionState.READY.name,
            testType = draft.testType.name,
            scope = draft.scope?.name,
            weapon = draft.weapon.clean(),
            muzzle = draft.muzzle.clean(),
            grip = draft.grip.clean(),
            magazine = draft.magazine.clean(),
            stock = draft.stock.clean(),
            otherAttachment = draft.otherAttachment.clean(),
            distance = draft.distance?.name,
            targetType = draft.targetType?.name,
            cameraSensitivity = draft.cameraSensitivity,
            adsSensitivity = draft.adsSensitivity,
            gyroSensitivity = draft.gyroSensitivity,
            adsGyroSensitivity = draft.adsGyroSensitivity,
            fps = draft.fps,
            refreshRate = draft.refreshRate,
            captureStatus = CaptureStatus.NOT_STARTED.name,
            analysisStatus = AnalysisStatus.NOT_ANALYZED.name,
            notes = draft.notes.clean()
        )
        store.insert(session)
        return SessionResult.Ok(session)
    }

    /** READY -> CAPTURE_PENDING. Never creates a recording; capture status reflects what the engine really reports. */
    suspend fun start(sessionId: String): SessionResult {
        val s = store.get(sessionId) ?: return SessionResult.NotFound
        if (s.sessionState != SessionState.READY) return SessionResult.Rejected("Session is not in READY state.")
        val now = clock()
        val (capture, analysis, notice) = when (val a = captureEngine.availability()) {
            is Availability.Available -> Triple(CaptureStatus.NOT_STARTED, AnalysisStatus.NOT_ANALYZED, null)
            is Availability.NotAvailable -> Triple(CaptureStatus.NOT_AVAILABLE, AnalysisStatus.PENDING_CAPTURE, a.reason)
        }
        val updated = s.copy(
            state = SessionState.CAPTURE_PENDING.name,
            startedAt = now,
            captureStatus = capture.name,
            analysisStatus = analysis.name
        )
        store.update(updated)
        return SessionResult.Ok(updated, notice)
    }

    suspend fun pause(sessionId: String): SessionResult {
        val s = store.get(sessionId) ?: return SessionResult.NotFound
        if (!s.isRunning()) return SessionResult.Rejected("Session is not running.")
        if (s.pausedAt != null) return SessionResult.Rejected("Session is already paused.")
        val updated = s.copy(pausedAt = clock())
        store.update(updated)
        return SessionResult.Ok(updated)
    }

    suspend fun resume(sessionId: String): SessionResult {
        val s = store.get(sessionId) ?: return SessionResult.NotFound
        val pausedAt = s.pausedAt ?: return SessionResult.Rejected("Session is not paused.")
        if (!s.isRunning()) return SessionResult.Rejected("Session is not running.")
        val updated = s.copy(pausedAt = null, pausedTotalMs = s.pausedTotalMs + (clock() - pausedAt).coerceAtLeast(0L))
        store.update(updated)
        return SessionResult.Ok(updated)
    }

    /**
     * Ends a waiting session and saves it. Without real captured data the state stays CAPTURE_PENDING,
     * capture stays NOT_AVAILABLE and analysis stays PENDING_CAPTURE. No result is produced.
     * Ending a session that is really CAPTURING belongs to Phase 3 and is rejected for now.
     */
    suspend fun end(sessionId: String): SessionResult {
        val s = store.get(sessionId) ?: return SessionResult.NotFound
        if (s.endedAt != null) return SessionResult.Rejected("Session already ended.")
        if (s.sessionState != SessionState.CAPTURE_PENDING) {
            return SessionResult.Rejected("Only a started session can be ended. Use Cancel for a session that never started.")
        }
        val updated = s.closedAt(clock())
        store.update(updated)
        return SessionResult.Ok(updated)
    }

    suspend fun cancel(sessionId: String): SessionResult {
        val s = store.get(sessionId) ?: return SessionResult.NotFound
        if (s.endedAt != null) return SessionResult.Rejected("Session already ended.")
        if (!s.sessionState.canGoTo(SessionState.CANCELLED)) return SessionResult.Rejected("Session cannot be cancelled now.")
        val updated = s.closedAt(clock()).copy(state = SessionState.CANCELLED.name)
        store.update(updated)
        return SessionResult.Ok(updated)
    }

    /** Explicit user action. Removes the session record (and its captures, in the store). */
    suspend fun delete(sessionId: String): Boolean {
        if (store.get(sessionId) == null) return false
        store.delete(sessionId)
        return true
    }

    fun elapsedMs(s: TestSessionEntity, now: Long = clock()): Long =
        SessionClock.elapsedMs(s.startedAt, s.pausedAt, s.pausedTotalMs, s.endedAt, now)

    // ---- helpers ----

    private fun TestSessionEntity.isRunning(): Boolean =
        endedAt == null && (sessionState == SessionState.CAPTURE_PENDING || sessionState == SessionState.CAPTURING)

    /** Sets endedAt and folds a running pause into pausedTotalMs. */
    private fun TestSessionEntity.closedAt(now: Long): TestSessionEntity {
        val extra = pausedAt?.let { (now - it).coerceAtLeast(0L) } ?: 0L
        return copy(endedAt = now, pausedAt = null, pausedTotalMs = pausedTotalMs + extra)
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun validate(d: SessionDraft): String? {
        if (d.testType == TestMode.FULL_DIAGNOSTIC) {
            return "Full Diagnostic is not available yet. Run its tests one by one."
        }
        listOf(d.cameraSensitivity, d.adsSensitivity, d.gyroSensitivity, d.adsGyroSensitivity).forEach {
            if (it != null && it !in 0..SessionOptions.MAX_SENSITIVITY) {
                return "Sensitivity must be between 0 and ${SessionOptions.MAX_SENSITIVITY}."
            }
        }
        listOf(d.fps, d.refreshRate).forEach {
            if (it != null && it !in 1..SessionOptions.MAX_FPS_OR_HZ) {
                return "FPS and refresh rate must be between 1 and ${SessionOptions.MAX_FPS_OR_HZ}."
            }
        }
        return null
    }
}
