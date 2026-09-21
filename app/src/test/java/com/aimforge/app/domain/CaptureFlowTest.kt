package com.aimforge.app.domain

import com.aimforge.app.data.TestSessionEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real capture lifecycle rules, driven by an engine double that reports only what each test says. */
class CaptureFlowTest {
    private var time = 1_000_000L
    private var ids = 0
    private val store = FakeSessionStore()
    private val engine = FakeCaptureEngine()
    private val grant = CaptureGrant(-1, Unit)

    private fun manager() = SessionManager(store, engine, clock = { time }, newId = { "id${++ids}" })
    private fun ok(r: SessionResult): TestSessionEntity = (r as SessionResult.Ok).session
    private suspend fun ready(m: SessionManager) = ok(m.createSession(SessionDraft(TestMode.SPRAY_3X))).sessionId

    @Test fun grantedStartBecomesCapturingOnlyAfterEngineConfirms() = runBlocking {
        val m = manager()
        val id = ready(m)
        val s = ok(m.startCapture(id, grant))
        assertEquals(SessionState.CAPTURING, s.sessionState)
        assertEquals(CaptureStatus.CAPTURING, s.captureStatusEnum)
        assertEquals(1, engine.startCalls)
        val cap = store.captureForSession(id)!!
        assertEquals(CaptureStatus.CAPTURING.name, cap.status)
        assertEquals(true, cap.permissionGranted)
        assertEquals(1280, cap.widthPx)
        assertNull(cap.storageLocation)            // nothing stored, only metadata
        assertEquals(0, cap.frameCount)            // no frames yet, no invented number
        assertFalse(s.hasAnalysis)
    }

    @Test fun captureMetadataRowExistsBeforeEngineCanPublishRunning() = runBlocking {
        val m = manager()
        val id = ready(m)
        var rowSeenByEngine = false
        engine.onStartObserved = { observedSessionId, observedCaptureId ->
            val row = store.captureForSession(observedSessionId)
            rowSeenByEngine = row?.captureId == observedCaptureId &&
                row?.permissionGranted == true &&
                row?.status == CaptureStatus.NOT_STARTED.name
        }

        ok(m.startCapture(id, grant))

        assertTrue("The metadata row must precede asynchronous capture callbacks.", rowSeenByEngine)
        assertEquals(1, store.captures.size)
    }

    @Test fun immediateStopUpdatesPrecreatedRowWithoutCreatingDuplicate() = runBlocking {
        engine.stopImmediately = true
        val m = manager()
        val id = ready(m)

        val result = ok(m.startCapture(id, grant))

        assertEquals(SessionState.FAILED, result.sessionState)
        assertEquals(1, store.captures.size)
        assertEquals(CaptureStatus.FAILED.name, store.captureForSession(id)!!.status)
    }

    @Test fun secondStartForSameSessionIsRejectedWithoutStartingAnotherEngineCapture() = runBlocking {
        val m = manager()
        val id = ready(m)

        assertEquals(SessionState.CAPTURING, ok(m.startCapture(id, grant)).sessionState)
        assertTrue(m.startCapture(id, grant) is SessionResult.Rejected)
        assertEquals(1, engine.startCalls)
        assertEquals(1, store.captures.size)
    }

    @Test fun deniedPermissionKeepsSessionReadyAndStoresRealDeniedState() = runBlocking {
        val m = manager()
        val id = ready(m)
        val r = m.startCapture(id, null) as SessionResult.Ok
        assertEquals(SessionState.READY, r.session.sessionState)
        assertEquals(CaptureStatus.PERMISSION_DENIED, r.session.captureStatusEnum)
        assertEquals(0, engine.startCalls)
        assertNull(r.session.startedAt)
        assertTrue(store.captures.isEmpty())
        // can try again and succeed
        assertEquals(SessionState.CAPTURING, ok(m.startCapture(id, grant)).sessionState)
    }

    @Test fun engineStartFailureIsStoredAsFailedWithRealReason() = runBlocking {
        engine.startFailure = "Could not start screen capture: SecurityException"
        val m = manager()
        val id = ready(m)
        val r = m.startCapture(id, grant) as SessionResult.Ok
        assertEquals(SessionState.FAILED, r.session.sessionState)
        assertEquals(CaptureStatus.FAILED, r.session.captureStatusEnum)
        assertEquals("Could not start screen capture: SecurityException", r.session.failureReason)
        assertNotNull(r.session.endedAt)
        assertFalse(r.session.isActive)
        assertEquals(CaptureStatus.FAILED.name, store.captureForSession(id)!!.status)
        assertNull(store.findActive())
    }

    @Test fun endWithFramesCompletesCaptureAndLeavesAnalysisPending() = runBlocking {
        engine.framesAtStop = 600
        val m = manager()
        val id = ready(m)
        m.startCapture(id, grant)
        time += 10_000
        val s = ok(m.end(id))
        assertEquals(SessionState.CAPTURE_COMPLETE, s.sessionState)
        assertEquals(CaptureStatus.CAPTURED, s.captureStatusEnum)
        assertEquals(AnalysisStatus.PENDING, s.analysisStatusEnum)
        assertFalse(s.hasAnalysis)
        assertNull(s.aimScore)
        val cap = store.captureForSession(id)!!
        assertEquals(600, cap.frameCount)
        assertEquals(59.5f, cap.fps)
        assertEquals(40, cap.sampledFrameCount)
        assertEquals(CaptureStatus.CAPTURED.name, cap.status)
        assertEquals(9_000L, cap.endTime)
    }

    @Test fun endWithZeroFramesIsAFailureNotASuccess() = runBlocking {
        engine.framesAtStop = 0
        val m = manager()
        val id = ready(m)
        m.startCapture(id, grant)
        val s = ok(m.end(id))
        assertEquals(SessionState.FAILED, s.sessionState)
        assertEquals(CaptureStatus.FAILED, s.captureStatusEnum)
        assertEquals(AnalysisStatus.NOT_ANALYZED, s.analysisStatusEnum)
        assertNotNull(s.failureReason)
    }

    @Test fun engineStopFailureStillSavesWhateverWasReallyCounted() = runBlocking {
        val m = manager()
        val id = ready(m)
        m.startCapture(id, grant)
        engine.flow.value = engine.flow.value.copy(frameCount = 90)   // engine really counted 90 frames
        engine.stopFailure = "Capture did not confirm stop in time."
        val s = ok(m.end(id))
        assertEquals(SessionState.CAPTURE_COMPLETE, s.sessionState)
        assertEquals(90, store.captureForSession(id)!!.frameCount)
    }

    @Test fun capturingSessionCannotBePausedAndEndIsIdempotent() = runBlocking {
        val m = manager()
        val id = ready(m)
        m.startCapture(id, grant)
        assertTrue(m.pause(id) is SessionResult.Rejected)
        engine.framesAtStop = 10
        ok(m.end(id))
        assertTrue(m.end(id) is SessionResult.Rejected)
        assertEquals(1, engine.stopCalls)
    }

    @Test fun cancelWhileCapturingStopsEngineAndKeepsCancelledRecord() = runBlocking {
        engine.framesAtStop = 30
        val m = manager()
        val id = ready(m)
        m.startCapture(id, grant)
        val s = ok(m.cancel(id))
        assertEquals(SessionState.CANCELLED, s.sessionState)
        assertEquals(CaptureStatus.CANCELLED, s.captureStatusEnum)
        assertEquals(1, engine.stopCalls)
        assertEquals(CaptureStatus.CANCELLED.name, store.captureForSession(id)!!.status)
        assertNull(store.findActive())
    }

    @Test fun deleteWhileCapturingStopsEngineAndRemovesRows() = runBlocking {
        val m = manager()
        val id = ready(m)
        m.startCapture(id, grant)
        assertTrue(m.delete(id))
        assertEquals(1, engine.stopCalls)
        assertTrue(store.rows.isEmpty())
        assertTrue(store.captures.isEmpty())
    }

    @Test fun systemStopFinalizesOnce() = runBlocking {
        val m = manager()
        val id = ready(m)
        m.startCapture(id, grant)
        val snap = engine.flow.value.copy(
            status = CaptureRuntimeStatus.STOPPED, frameCount = 200, stoppedAtMs = 9_500L,
            stopReason = "Screen capture was stopped by Android or by you (system capture control)."
        )
        val first = m.onCaptureEnded(id, snap)
        assertEquals(SessionState.CAPTURE_COMPLETE, (first as SessionResult.Ok).session.sessionState)
        assertTrue(m.onCaptureEnded(id, snap) is SessionResult.Rejected)      // second call changes nothing
        assertEquals(200, store.captureForSession(id)!!.frameCount)
    }

    @Test fun progressUpdatesOnlyTouchTheCaptureRow() = runBlocking {
        val m = manager()
        val id = ready(m)
        m.startCapture(id, grant)
        m.updateCaptureProgress(id, engine.flow.value.copy(frameCount = 42, measuredFps = 30f, sampledFrames = 3, blankSampledFrames = 1))
        val cap = store.captureForSession(id)!!
        assertEquals(42, cap.frameCount); assertEquals(3, cap.sampledFrameCount); assertEquals(1, cap.blankFrameCount)
        assertEquals(SessionState.CAPTURING, store.get(id)!!.sessionState)
    }

    @Test fun callbackForAnotherCaptureIdCannotFinalizeThisSession() = runBlocking {
        val m = manager()
        val id = ready(m)
        m.startCapture(id, grant)
        val wrong = engine.flow.value.copy(captureId = "different-capture", frameCount = 999)

        m.updateCaptureProgress(id, wrong)
        assertEquals(0, store.captureForSession(id)!!.frameCount)
        assertTrue(m.onCaptureEnded(id, wrong) is SessionResult.Rejected)
        assertEquals(SessionState.CAPTURING, store.get(id)!!.sessionState)
    }

    @Test fun recoverInterruptedFailsAStaleCapturingSession() = runBlocking {
        val m = manager()
        val id = ready(m)
        m.startCapture(id, grant)
        m.updateCaptureProgress(id, engine.flow.value.copy(frameCount = 55))
        val fresh = manager()                       // simulates a new process: engine idle, DB says CAPTURING
        engine.flow.value = CaptureSnapshot()
        fresh.recoverInterrupted()
        val s = store.get(id)!!
        assertEquals(SessionState.FAILED, s.sessionState)
        assertNotNull(s.endedAt); assertNotNull(s.failureReason)
        assertEquals(55, store.captureForSession(id)!!.frameCount)   // counts kept, not invented
        assertNull(store.findActive())
    }

    @Test fun recoverInterruptedLeavesReadyAndSavedSessionsAlone() = runBlocking {
        val m = manager()
        val id = ready(m)
        m.recoverInterrupted()
        assertEquals(SessionState.READY, store.get(id)!!.sessionState)
    }

    @Test fun recoveryDoesNotTerminateALiveCaptureInThisProcess() = runBlocking {
        val m = manager()
        val id = ready(m)
        m.startCapture(id, grant)

        m.recoverInterrupted()

        assertEquals(SessionState.CAPTURING, store.get(id)!!.sessionState)
    }
}
