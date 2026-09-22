package com.aimforge.app.domain

import com.aimforge.app.data.CaptureEntity
import com.aimforge.app.data.TestSessionEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** In-memory store for JVM tests. Starts EMPTY, like a fresh install. */
class FakeSessionStore : SessionStore {
    val rows = java.util.concurrent.ConcurrentHashMap<String, TestSessionEntity>()
    val captures = java.util.concurrent.ConcurrentHashMap<String, CaptureEntity>()
    override suspend fun insert(session: TestSessionEntity) { rows[session.sessionId] = session }
    override suspend fun get(sessionId: String) = rows[sessionId]
    override suspend fun update(session: TestSessionEntity) { rows[session.sessionId] = session }
    override suspend fun delete(sessionId: String) {
        rows.remove(sessionId)
        captures.values.removeAll { it.sessionId == sessionId }
    }
    override suspend fun findActive() = rows.values.firstOrNull { it.isActive }
    override suspend fun insertCapture(capture: CaptureEntity) { captures[capture.captureId] = capture }
    override suspend fun updateCapture(capture: CaptureEntity) { captures[capture.captureId] = capture }
    override suspend fun captureForSession(sessionId: String) = captures.values.lastOrNull { it.sessionId == sessionId }
}

/** Engine double for tests only. It reports exactly what the test tells it to. */
class FakeCaptureEngine(private val available: Availability = Availability.Available) : CaptureEngine {
    val flow = MutableStateFlow(CaptureSnapshot())
    var startFailure: String? = null
    var stopImmediately: Boolean = false
    var onStartObserved: (suspend (sessionId: String, captureId: String) -> Unit)? = null
    var framesAtStop: Int = 0
    var stopFailure: String? = null
    var startCalls = 0
    var stopCalls = 0

    override val state: StateFlow<CaptureSnapshot> = flow
    override fun availability(): Availability = available

    override suspend fun start(sessionId: String, captureId: String, grant: CaptureGrant): CaptureOutcome {
        startCalls++
        onStartObserved?.invoke(sessionId, captureId)
        startFailure?.let { reason ->
            flow.value = CaptureSnapshot(CaptureRuntimeStatus.FAILED, sessionId, captureId, failureReason = reason)
            return CaptureOutcome.Failed(reason)
        }
        val snap = CaptureSnapshot(
            status = CaptureRuntimeStatus.RUNNING, sessionId = sessionId, captureId = captureId,
            width = 1280, height = 592, startedAtMs = 5_000L
        )
        if (stopImmediately) {
            val stopped = snap.copy(
                status = CaptureRuntimeStatus.STOPPED,
                stoppedAtMs = 5_100L,
                stopReason = "Stopped immediately by the test engine."
            )
            flow.value = stopped
            return CaptureOutcome.Stopped(stopped)
        }
        flow.value = snap
        return CaptureOutcome.Started(snap)
    }

    override suspend fun stop(sessionId: String): CaptureOutcome {
        stopCalls++
        stopFailure?.let { return CaptureOutcome.Failed(it) }
        val snap = flow.value.copy(
            status = CaptureRuntimeStatus.STOPPED, frameCount = framesAtStop, measuredFps = if (framesAtStop > 0) 59.5f else null,
            sampledFrames = framesAtStop / 15, blankSampledFrames = 0, stoppedAtMs = 9_000L, stopReason = "Stopped by you."
        )
        flow.value = snap
        return CaptureOutcome.Stopped(snap)
    }
}
