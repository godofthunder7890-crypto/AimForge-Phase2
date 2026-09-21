package com.aimforge.app.domain

import com.aimforge.app.data.TestSessionEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionManagerTest {
    private var time = 1_000_000L
    private var idCounter = 0
    private val store = FakeSessionStore()
    private val unavailable = FakeCaptureEngine(Availability.NotAvailable("Screen capture engine is not available yet."))

    private fun manager(engine: CaptureEngine = unavailable) =
        SessionManager(store, engine, clock = { time }, newId = { "id${++idCounter}" })

    private fun draft(
        mode: TestMode = TestMode.SPRAY_3X,
        cam: Int? = 120, ads: Int? = 115, gyro: Int? = 300, adsGyro: Int? = null
    ) = SessionDraft(
        testType = mode, scope = ScopeType.X3, weapon = "M416",
        cameraSensitivity = cam, adsSensitivity = ads, gyroSensitivity = gyro, adsGyroSensitivity = adsGyro
    )

    private fun ok(r: SessionResult): TestSessionEntity = (r as SessionResult.Ok).session

    @Test fun freshInstallHasZeroSessions() = runBlocking {
        assertTrue(store.rows.isEmpty())
        assertNull(store.findActive())
    }

    @Test fun createSessionIsReadyAndStoresExactSnapshot() = runBlocking {
        val s = ok(manager().createSession(draft()))
        assertEquals(SessionState.READY.name, s.state)
        assertEquals(120, s.cameraSensitivity)
        assertEquals(115, s.adsSensitivity)
        assertEquals(300, s.gyroSensitivity)
        assertEquals(1, store.rows.size)
    }

    @Test fun unknownValuesStayNullNotDefaults() = runBlocking {
        val s = ok(manager().createSession(SessionDraft(testType = TestMode.QUICK_AIM_CHECK)))
        assertNull(s.adsGyroSensitivity); assertNull(s.cameraSensitivity)
        assertNull(s.fps); assertNull(s.refreshRate); assertNull(s.weapon)
        assertNull(s.distance); assertNull(s.targetType); assertNull(s.scope)
        assertNull(s.muzzle); assertNull(s.otherAttachment)
    }

    @Test fun blankStringsBecomeNull() = runBlocking {
        val s = ok(manager().createSession(SessionDraft(testType = TestMode.QUICK_AIM_CHECK, weapon = "  ", notes = "")))
        assertNull(s.weapon); assertNull(s.notes)
    }

    @Test fun noAnalysisDataExistsAfterCreation() = runBlocking {
        val s = ok(manager().createSession(draft()))
        assertNull(s.aimScore); assertNull(s.stability); assertNull(s.aimErrorPx)
        assertNull(s.errorState); assertNull(s.confidence); assertNull(s.recommendation)
        assertFalse(s.hasAnalysis)
        assertEquals(AnalysisStatus.NOT_ANALYZED, s.analysisStatusEnum)
        assertEquals(CaptureStatus.NOT_STARTED, s.captureStatusEnum)
    }

    @Test fun rejectsOutOfRangeValuesAndStoresNothing() = runBlocking {
        val m = manager()
        assertTrue(m.createSession(draft(cam = 999)) is SessionResult.Rejected)
        assertTrue(m.createSession(SessionDraft(TestMode.SPRAY_3X, fps = 0)) is SessionResult.Rejected)
        assertTrue(store.rows.isEmpty())
    }

    @Test fun fullDiagnosticIsNotCreatedAsSession() = runBlocking {
        assertTrue(manager().createSession(draft(mode = TestMode.FULL_DIAGNOSTIC)) is SessionResult.Rejected)
        assertTrue(store.rows.isEmpty())
    }

    @Test fun onlyOneOpenSessionAtATime() = runBlocking {
        val m = manager()
        ok(m.createSession(draft()))
        assertTrue(m.createSession(draft()) is SessionResult.Rejected)
        assertEquals(1, store.rows.size)
    }

    @Test fun startWithoutCaptureEngineIsHonest() = runBlocking {
        val m = manager()
        val id = ok(m.createSession(draft())).sessionId
        val r = m.start(id) as SessionResult.Ok
        assertEquals("Screen capture engine is not available yet.", r.notice)
        assertEquals(SessionState.CAPTURE_PENDING.name, r.session.state)
        assertEquals(CaptureStatus.NOT_AVAILABLE, r.session.captureStatusEnum)
        assertEquals(AnalysisStatus.PENDING_CAPTURE, r.session.analysisStatusEnum)
        assertEquals(time, r.session.startedAt)
        assertFalse(r.session.hasAnalysis)
    }

    @Test fun startWithAvailableEngineWaitsForCapture() = runBlocking {
        val m = manager(FakeCaptureEngine(Availability.Available))
        val id = ok(m.createSession(draft())).sessionId
        val r = m.start(id) as SessionResult.Ok
        assertNull(r.notice)
        assertEquals(CaptureStatus.NOT_STARTED, r.session.captureStatusEnum)
    }

    @Test fun cannotStartTwiceOrUnknownSession() = runBlocking {
        val m = manager()
        val id = ok(m.createSession(draft())).sessionId
        m.start(id)
        assertTrue(m.start(id) is SessionResult.Rejected)
        assertTrue(m.start("missing") is SessionResult.NotFound)
    }

    @Test fun sensitivitySnapshotSurvivesEveryTransition() = runBlocking {
        val m = manager()
        val created = ok(m.createSession(draft()))
        val id = created.sessionId
        m.start(id); time += 1000; m.pause(id); time += 1000; m.resume(id); time += 1000; m.end(id)
        val end = store.rows.getValue(id)
        assertEquals(created.cameraSensitivity, end.cameraSensitivity)
        assertEquals(created.adsSensitivity, end.adsSensitivity)
        assertEquals(created.gyroSensitivity, end.gyroSensitivity)
        assertEquals(created.adsGyroSensitivity, end.adsGyroSensitivity)
        assertEquals(created.weapon, end.weapon)
        assertEquals(created.scope, end.scope)
    }

    @Test fun timerPauseResumeEnd() = runBlocking {
        val m = manager()
        val id = ok(m.createSession(draft())).sessionId
        m.start(id)                       // t0
        time += 10_000
        assertEquals(10_000L, m.elapsedMs(store.rows.getValue(id)))
        m.pause(id)
        time += 5_000                      // paused, must not count
        assertEquals(10_000L, m.elapsedMs(store.rows.getValue(id)))
        m.resume(id)
        time += 4_000
        val ended = ok(m.end(id))
        assertEquals(14_000L, m.elapsedMs(ended, now = time + 60_000)) // frozen after end
        assertNull(ended.pausedAt)
    }

    @Test fun endWhilePausedDoesNotCountPause() = runBlocking {
        val m = manager()
        val id = ok(m.createSession(draft())).sessionId
        m.start(id); time += 6_000; m.pause(id); time += 9_000
        val ended = ok(m.end(id))
        assertEquals(6_000L, m.elapsedMs(ended))
    }

    @Test fun pauseResumeGuards() = runBlocking {
        val m = manager()
        val id = ok(m.createSession(draft())).sessionId
        assertTrue(m.pause(id) is SessionResult.Rejected)   // READY is not running
        m.start(id)
        assertTrue(m.resume(id) is SessionResult.Rejected)  // not paused
        m.pause(id)
        assertTrue(m.pause(id) is SessionResult.Rejected)   // already paused
    }

    @Test fun endedSessionHasNoResultAndAnalysisStaysPending() = runBlocking {
        val m = manager()
        val id = ok(m.createSession(draft())).sessionId
        m.start(id); time += 3_000
        val ended = ok(m.end(id))
        assertEquals(time, ended.endedAt)
        assertEquals(SessionState.CAPTURE_PENDING.name, ended.state)
        assertEquals(CaptureStatus.NOT_AVAILABLE, ended.captureStatusEnum)
        assertEquals(AnalysisStatus.PENDING_CAPTURE, ended.analysisStatusEnum)
        assertNull(ended.aimScore); assertFalse(ended.hasAnalysis)
        assertFalse(ended.isActive)
        assertTrue(m.end(id) is SessionResult.Rejected)
    }

    @Test fun cannotEndSessionThatNeverStarted() = runBlocking {
        val m = manager()
        val id = ok(m.createSession(draft())).sessionId
        assertTrue(m.end(id) is SessionResult.Rejected)
    }

    @Test fun cancelReadySessionKeepsRecordAsCancelled() = runBlocking {
        val m = manager()
        val id = ok(m.createSession(draft())).sessionId
        val c = ok(m.cancel(id))
        assertEquals(SessionState.CANCELLED.name, c.state)
        assertEquals(1, store.rows.size)
        assertNull(store.findActive())
        assertTrue(m.cancel(id) is SessionResult.Rejected)
        assertTrue(m.start(id) is SessionResult.Rejected)
    }

    @Test fun cancelRunningSessionAndThenNewSessionAllowed() = runBlocking {
        val m = manager()
        val id = ok(m.createSession(draft())).sessionId
        m.start(id); time += 2_000
        val c = ok(m.cancel(id))
        assertEquals(2_000L, m.elapsedMs(c))
        assertTrue(m.createSession(draft()) is SessionResult.Ok)
    }

    @Test fun deleteRemovesRecord() = runBlocking {
        val m = manager()
        val id = ok(m.createSession(draft())).sessionId
        assertTrue(m.delete(id))
        assertTrue(store.rows.isEmpty())
        assertFalse(m.delete(id))
    }

    @Test fun cancelledDraftNeverCreatesRecord() = runBlocking {
        // A draft is only a SessionDraft object. Not calling createSession must leave storage empty.
        val d = draft()
        assertEquals(TestMode.SPRAY_3X, d.testType)
        assertTrue(store.rows.isEmpty())
    }
}
