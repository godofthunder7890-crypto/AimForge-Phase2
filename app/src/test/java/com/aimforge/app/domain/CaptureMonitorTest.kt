package com.aimforge.app.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureMonitorTest {
    private val store = FakeSessionStore()
    private val engine = FakeCaptureEngine()
    private val manager = SessionManager(store, engine, clock = { 2_000_000L }, newId = { "x${store.rows.size + store.captures.size}" })

    private suspend fun waitFor(cond: suspend () -> Boolean) = withTimeout(3_000) { while (!cond()) delay(10) }

    @Test fun captureEndingOnItsOwnIsSavedWithoutAnyScreenOpen() = runBlocking {
        val id = (manager.createSession(SessionDraft(TestMode.SPRAY_3X)) as SessionResult.Ok).session.sessionId
        manager.startCapture(id, CaptureGrant(-1, Unit))
        val scope = CoroutineScope(Dispatchers.Default)
        val job = CaptureMonitor(engine, manager, scope, progressIntervalMs = 0).start()
        engine.flow.value = engine.flow.value.copy(status = CaptureRuntimeStatus.RUNNING, frameCount = 120, sampledFrames = 8)
        waitFor { store.captureForSession(id)?.frameCount == 120 }
        engine.flow.value = engine.flow.value.copy(status = CaptureRuntimeStatus.STOPPED, stoppedAtMs = 9_900L, stopReason = "Stopped from the notification.")
        waitFor { store.get(id)?.sessionState == SessionState.CAPTURE_COMPLETE }
        assertEquals(CaptureStatus.CAPTURED.name, store.captureForSession(id)!!.status)
        assertEquals("Stopped from the notification.", store.captureForSession(id)!!.stopReason)
        job.cancel()
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    @Test fun failureAfterStartWithoutFramesFailsTheSession() = runBlocking {
        val id = (manager.createSession(SessionDraft(TestMode.SPRAY_3X)) as SessionResult.Ok).session.sessionId
        manager.startCapture(id, CaptureGrant(-1, Unit))
        val scope = CoroutineScope(Dispatchers.Default)
        val job = CaptureMonitor(engine, manager, scope, progressIntervalMs = 0).start()
        engine.flow.value = engine.flow.value.copy(status = CaptureRuntimeStatus.FAILED, failureReason = "Display released")
        waitFor { store.get(id)?.sessionState == SessionState.FAILED }
        assertEquals("Display released", store.get(id)!!.failureReason)
        job.cancel()
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }
}
