package com.aimforge.app.domain

import com.aimforge.app.data.CaptureEntity
import com.aimforge.app.data.TestSessionEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** Storage seam. Room implements it in the app; tests use an in-memory fake. */
interface SessionStore {
    suspend fun insert(session: TestSessionEntity)
    suspend fun get(sessionId: String): TestSessionEntity?
    suspend fun update(session: TestSessionEntity)
    suspend fun delete(sessionId: String)
    suspend fun findActive(): TestSessionEntity?

    suspend fun insertCapture(capture: CaptureEntity)
    suspend fun updateCapture(capture: CaptureEntity)
    suspend fun captureForSession(sessionId: String): CaptureEntity?
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
 *
 * Capture rule: a session becomes CAPTURING only after the capture engine reports a running MediaProjection.
 * It becomes CAPTURE_COMPLETE only if real frames were received; otherwise FAILED with the real reason.
 */
class SessionManager(
    private val store: SessionStore,
    private val captureEngine: CaptureEngine,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val newId: () -> String = { UUID.randomUUID().toString() }
) {
    /**
     * Serializes the complete capture lifecycle.
     *
     * In particular, the capture row is inserted while this lock is held and before the engine can
     * publish RUNNING. A monitor callback may therefore arrive at any point after engine.start()
     * begins, but it cannot finalize the session until startup has committed the row and the session
     * state.
     */
    private val captureLock = Mutex()

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

    /**
     * READY -> CAPTURE_PENDING -> CAPTURING.
     * [grant] is the result of the Android consent dialog; null means the user denied or cancelled it.
     * Nothing is ever marked as capturing unless the engine confirms a running capture.
     */
    suspend fun startCapture(sessionId: String, grant: CaptureGrant?): SessionResult =
        captureLock.withLock {
        val s = store.get(sessionId) ?: return@withLock SessionResult.NotFound
        if (s.sessionState != SessionState.READY) return@withLock SessionResult.Rejected("Session is not in READY state.")

        val availability = captureEngine.availability()
        if (availability is Availability.NotAvailable) {
            val updated = s.copy(
                state = SessionState.CAPTURE_PENDING.name,
                startedAt = clock(),
                captureStatus = CaptureStatus.NOT_AVAILABLE.name,
                analysisStatus = AnalysisStatus.PENDING_CAPTURE.name
            )
            store.update(updated)
            return@withLock SessionResult.Ok(updated, availability.reason)
        }

        if (grant == null) {
            // Session stays READY; the real denied state is stored so it survives a restart.
            val updated = s.copy(captureStatus = CaptureStatus.PERMISSION_DENIED.name)
            store.update(updated)
            return@withLock SessionResult.Ok(updated, "Screen-capture permission was denied. Session not started.")
        }

        val pending = s.copy(
            state = SessionState.CAPTURE_PENDING.name,
            startedAt = clock(),
            captureStatus = CaptureStatus.NOT_STARTED.name,
            analysisStatus = AnalysisStatus.PENDING_CAPTURE.name
        )
        store.update(pending)

        val captureId = newId()
        /*
         * This is deliberately before captureEngine.start(). The Android service can publish
         * RUNNING, STOPPED, or FAILED before start() returns to this coroutine. The monitor is
         * allowed to observe those states, but it waits on captureLock until this row exists and
         * the session has been moved to CAPTURING (or to its terminal failure state).
         */
        store.insertCapture(
            CaptureEntity(
                captureId = captureId,
                sessionId = sessionId,
                startTime = null,
                endTime = null,
                frameCount = null,
                widthPx = null,
                heightPx = null,
                fps = null,
                storageLocation = null,
                permissionGranted = true,
                status = CaptureStatus.NOT_STARTED.name
            )
        )

        when (val out = captureEngine.start(sessionId, captureId, grant)) {
            is CaptureOutcome.Started -> {
                val capture = store.captureForSession(sessionId)
                if (capture == null || capture.captureId != captureId) {
                    val reason = "Capture metadata could not be associated with this capture session."
                    failStart(pending, captureId, reason, permissionGranted = true, snapshot = out.snapshot)
                } else {
                    store.updateCapture(
                        capture.withSnapshot(out.snapshot).copy(
                            startTime = out.snapshot.startedAtMs ?: clock(),
                            status = CaptureStatus.CAPTURING.name,
                            permissionGranted = true
                        )
                    )
                    val updated = pending.copy(
                        state = SessionState.CAPTURING.name,
                        captureStatus = CaptureStatus.CAPTURING.name
                    )
                    store.update(updated)
                    SessionResult.Ok(updated)
                }
            }
            is CaptureOutcome.Failed -> failStart(
                pending, captureId, out.reason, permissionGranted = true,
                snapshot = captureEngine.state.value.takeIf { it.captureId == captureId }
            )
            is CaptureOutcome.Stopped -> failStart(
                pending, captureId, "Capture stopped immediately after starting.", permissionGranted = true,
                snapshot = out.snapshot
            )
            CaptureOutcome.NotImplemented -> failStart(
                pending, captureId, "Screen capture engine is not available yet.", permissionGranted = null,
                snapshot = null
            )
        }
    }

    private suspend fun failStart(
        pending: TestSessionEntity,
        captureId: String,
        reason: String,
        permissionGranted: Boolean?,
        snapshot: CaptureSnapshot?
    ): SessionResult {
        val capture = store.captureForSession(pending.sessionId)
        if (capture != null && capture.captureId == captureId) {
            val measured = snapshot?.let { capture.withSnapshot(it) } ?: capture
            store.updateCapture(
                measured.copy(
                    endTime = snapshot?.stoppedAtMs ?: clock(),
                    permissionGranted = permissionGranted,
                    status = CaptureStatus.FAILED.name,
                    stopReason = reason
                )
            )
        }
        val updated = pending.copy(
            state = SessionState.FAILED.name,
            endedAt = clock(),
            captureStatus = CaptureStatus.FAILED.name,
            analysisStatus = AnalysisStatus.NOT_ANALYZED.name,
            failureReason = reason
        )
        store.update(updated)
        return SessionResult.Ok(updated, reason)
    }

    /** Timer pause. Only for a session that is waiting (no live capture); a live capture cannot be paused. */
    suspend fun pause(sessionId: String): SessionResult {
        val s = store.get(sessionId) ?: return SessionResult.NotFound
        if (!s.isWaiting()) return SessionResult.Rejected("Only a waiting session can be paused.")
        if (s.pausedAt != null) return SessionResult.Rejected("Session is already paused.")
        val updated = s.copy(pausedAt = clock())
        store.update(updated)
        return SessionResult.Ok(updated)
    }

    suspend fun resume(sessionId: String): SessionResult {
        val s = store.get(sessionId) ?: return SessionResult.NotFound
        val pausedAt = s.pausedAt ?: return SessionResult.Rejected("Session is not paused.")
        if (!s.isWaiting()) return SessionResult.Rejected("Session is not waiting.")
        val updated = s.copy(pausedAt = null, pausedTotalMs = s.pausedTotalMs + (clock() - pausedAt).coerceAtLeast(0L))
        store.update(updated)
        return SessionResult.Ok(updated)
    }

    /**
     * CAPTURING: stops the engine, then saves what was really captured (CAPTURE_COMPLETE if frames arrived, FAILED if none).
     * CAPTURE_PENDING (no capture engine): ends and saves the session; nothing was captured, analysis stays PENDING_CAPTURE.
     */
    suspend fun end(sessionId: String): SessionResult = captureLock.withLock {
        val s = store.get(sessionId) ?: return SessionResult.NotFound
        if (s.endedAt != null) return SessionResult.Rejected("Session already ended.")
        return when (s.sessionState) {
            SessionState.CAPTURING -> {
                val (snap, note) = stopEngine(sessionId)
                finalizeCaptureLocked(sessionId, snap, note ?: "Ended by you.")
            }
            SessionState.CAPTURE_PENDING -> {
                val updated = s.closedAt(clock())
                store.update(updated)
                SessionResult.Ok(updated)
            }
            else -> SessionResult.Rejected("Only a started session can be ended. Use Cancel for a session that never started.")
        }
    }

    suspend fun cancel(sessionId: String): SessionResult = captureLock.withLock {
        val s = store.get(sessionId) ?: return SessionResult.NotFound
        if (s.endedAt != null) return SessionResult.Rejected("Session already ended.")
        if (!s.sessionState.canGoTo(SessionState.CANCELLED)) return SessionResult.Rejected("Session cannot be cancelled now.")

        if (s.sessionState == SessionState.CAPTURING) {
            val (snap, _) = stopEngine(sessionId)
            val cur = store.get(sessionId)
            if (cur == null) {
                return@withLock SessionResult.NotFound
            } else if (cur.sessionState != SessionState.CAPTURING || cur.endedAt != null) {
                return@withLock SessionResult.Rejected("Capture already ended.")
            } else {
                val now = clock()
                store.captureForSession(sessionId)?.let { c ->
                    if (snap.captureId == null || snap.captureId == c.captureId) {
                        store.updateCapture(
                            c.withSnapshot(snap).copy(
                                endTime = snap.stoppedAtMs ?: now,
                                status = CaptureStatus.CANCELLED.name,
                                stopReason = "Cancelled by you."
                            )
                        )
                    }
                }
                val updated = cur.closedAt(now).copy(
                    state = SessionState.CANCELLED.name,
                    captureStatus = CaptureStatus.CANCELLED.name,
                    analysisStatus = AnalysisStatus.NOT_ANALYZED.name
                )
                store.update(updated)
                return@withLock SessionResult.Ok(updated)
            }
        }

        val updated = s.closedAt(clock()).copy(state = SessionState.CANCELLED.name)
        store.update(updated)
        return@withLock SessionResult.Ok(updated)
    }

    /** Explicit user action. Removes the session record (and its captures, in the store). */
    suspend fun delete(sessionId: String): Boolean = captureLock.withLock {
        val s = store.get(sessionId) ?: return false
        if (s.sessionState == SessionState.CAPTURING && s.endedAt == null) {
            stopEngine(sessionId) // never leave a capture running for a record that is about to disappear
        }
        store.delete(sessionId)
        return@withLock true
    }

    // ---- capture lifecycle callbacks (used by CaptureMonitor and internally) ----

    /** Periodic progress from the running capture. Only touches the capture row. */
    suspend fun updateCaptureProgress(sessionId: String, snap: CaptureSnapshot) {
        captureLock.withLock {
            val s = store.get(sessionId) ?: return@withLock
            if (s.sessionState != SessionState.CAPTURING || s.endedAt != null) return@withLock
            val c = store.captureForSession(sessionId) ?: return@withLock
            if (snap.captureId != null && snap.captureId != c.captureId) return@withLock
            store.updateCapture(c.withSnapshot(snap))
        }
    }

    /** The engine ended the capture on its own (system stop, notification Stop, failure). No-op if already finalized. */
    suspend fun onCaptureEnded(sessionId: String, snap: CaptureSnapshot): SessionResult =
        captureLock.withLock {
            finalizeCaptureLocked(sessionId, snap, "Capture ended before you ended the session.")
        }

    /**
     * Saves the real outcome of a capture. Frames received -> CAPTURE_COMPLETE + CAPTURED, analysis pending.
     * No frames -> FAILED with the real reason. Idempotent: only acts on a session still in CAPTURING.
     */
    suspend fun finalizeCapture(sessionId: String, snap: CaptureSnapshot, note: String?): SessionResult =
        captureLock.withLock {
            finalizeCaptureLocked(sessionId, snap, note)
        }

    private suspend fun finalizeCaptureLocked(
        sessionId: String,
        snap: CaptureSnapshot,
        note: String?
    ): SessionResult {
        val s = store.get(sessionId) ?: return SessionResult.NotFound
        if (s.sessionState != SessionState.CAPTURING || s.endedAt != null) {
            return SessionResult.Rejected("Session is not capturing.")
        }
        val now = clock()
        val gotFrames = snap.frameCount > 0
        val reason = snap.failureReason ?: snap.stopReason ?: note
        val failure = if (gotFrames) null else (snap.failureReason ?: "No frames were received from the screen capture.")
        val capture = store.captureForSession(sessionId)
        if (capture != null && snap.captureId != null && snap.captureId != capture.captureId) {
            return SessionResult.Rejected("Capture metadata belongs to a different capture.")
        }
        if (capture == null) {
            val reason = "Capture metadata was missing when the real capture ended."
            val updated = s.closedAt(now).copy(
                state = SessionState.FAILED.name,
                captureStatus = CaptureStatus.FAILED.name,
                analysisStatus = AnalysisStatus.NOT_ANALYZED.name,
                failureReason = reason
            )
            store.update(updated)
            return SessionResult.Ok(updated, reason)
        }
        capture.let { c ->
            store.updateCapture(
                c.withSnapshot(snap).copy(
                    endTime = snap.stoppedAtMs ?: now,
                    status = (if (gotFrames) CaptureStatus.CAPTURED else CaptureStatus.FAILED).name,
                    stopReason = reason
                )
            )
        }
        val updated = s.closedAt(now).copy(
            state = (if (gotFrames) SessionState.CAPTURE_COMPLETE else SessionState.FAILED).name,
            captureStatus = (if (gotFrames) CaptureStatus.CAPTURED else CaptureStatus.FAILED).name,
            analysisStatus = (if (gotFrames) AnalysisStatus.PENDING else AnalysisStatus.NOT_ANALYZED).name,
            failureReason = failure
        )
        store.update(updated)
        return SessionResult.Ok(updated)
    }

    /**
     * Call once at process start. A session that says CAPTURING (or was mid-start) cannot really be capturing
     * in a fresh process, so it is closed as FAILED with that reason. Counts saved so far are kept.
     */
    suspend fun recoverInterrupted() {
        captureLock.withLock {
            /*
             * Application startup recovery can race the first UI action. If this process already
             * has a live engine, it is not a stale row from a previous process and must be left
             * alone. A genuinely restarted process creates a fresh IDLE engine, so stale rows are
             * still recovered below.
             */
            if (captureEngine.state.value.status in setOf(
                    CaptureRuntimeStatus.STARTING,
                    CaptureRuntimeStatus.RUNNING
                )
            ) {
                return@withLock
            }
            val s = store.findActive() ?: return@withLock
            val interrupted = s.sessionState == SessionState.CAPTURING ||
                (s.sessionState == SessionState.CAPTURE_PENDING && s.captureStatusEnum == CaptureStatus.NOT_STARTED)
            if (!interrupted) return@withLock
            val now = clock()
            store.captureForSession(s.sessionId)?.let { c ->
                store.updateCapture(
                    c.copy(
                        endTime = now,
                        status = CaptureStatus.FAILED.name,
                        stopReason = "AimForge was closed or stopped while capturing. Frames counted so far are kept."
                    )
                )
            }
            store.update(
                s.closedAt(now).copy(
                    state = SessionState.FAILED.name,
                    captureStatus = CaptureStatus.FAILED.name,
                    analysisStatus = AnalysisStatus.NOT_ANALYZED.name,
                    failureReason = "AimForge was closed or stopped while capturing."
                )
            )
        }
    }

    fun elapsedMs(s: TestSessionEntity, now: Long = clock()): Long =
        SessionClock.elapsedMs(s.startedAt, s.pausedAt, s.pausedTotalMs, s.endedAt, now)

    // ---- helpers ----

    private fun TestSessionEntity.isWaiting(): Boolean =
        endedAt == null && sessionState == SessionState.CAPTURE_PENDING

    /** Asks the engine to stop. Returns the best real snapshot plus a note when the stop was not clean. */
    private suspend fun stopEngine(sessionId: String): Pair<CaptureSnapshot, String?> {
        val last = captureEngine.state.value.takeIf { it.sessionId == sessionId } ?: CaptureSnapshot(sessionId = sessionId)
        return when (val out = captureEngine.stop(sessionId)) {
            is CaptureOutcome.Stopped -> out.snapshot to null
            is CaptureOutcome.Failed -> (captureEngine.state.value.takeIf { it.sessionId == sessionId } ?: last) to out.reason
            is CaptureOutcome.Started -> last to "Unexpected engine state while stopping."
            CaptureOutcome.NotImplemented -> last to "Screen capture engine is not available yet."
        }
    }

    private fun CaptureEntity.withSnapshot(snap: CaptureSnapshot): CaptureEntity = copy(
        frameCount = snap.frameCount,
        widthPx = snap.width ?: widthPx,
        heightPx = snap.height ?: heightPx,
        fps = snap.measuredFps ?: fps,
        sampledFrameCount = snap.sampledFrames,
        blankFrameCount = snap.blankSampledFrames
    )

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
