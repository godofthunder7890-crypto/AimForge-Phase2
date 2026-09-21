package com.aimforge.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.aimforge.app.domain.Availability
import com.aimforge.app.domain.CaptureEngine
import com.aimforge.app.domain.CaptureOutcome
import com.aimforge.app.domain.ScopeType
import com.aimforge.app.domain.SessionDraft
import com.aimforge.app.domain.SessionManager
import com.aimforge.app.domain.SessionResult
import com.aimforge.app.domain.SessionState
import com.aimforge.app.domain.TestMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real Room + real SQLite (via Robolectric). Exercises the same store the app uses. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomPersistenceTest {
    private lateinit var ctx: Context
    private val dbName = "persist-test.db"

    private val engine = object : CaptureEngine {
        override fun availability() = Availability.NotAvailable("Screen capture engine is not available yet.")
        override suspend fun start(sessionId: String) = CaptureOutcome.NotImplemented
        override suspend fun stop(sessionId: String) = CaptureOutcome.NotImplemented
    }

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        ctx.deleteDatabase(dbName)
    }

    @After fun tearDown() {
        ctx.deleteDatabase(dbName)
    }

    private fun open() = Room.databaseBuilder(ctx, AppDatabase::class.java, dbName)
        .allowMainThreadQueries()
        .build()

    @Test fun newInstallationContainsZeroRecords() = runBlocking {
        val db = open()
        assertTrue(db.sessionDao().observeAll().first().isEmpty())
        assertTrue(db.captureDao().observeAll().first().isEmpty())
        assertTrue(db.sensitivityDao().observeAll().first().isEmpty())
        assertNull(db.profileDao().observe().first())
        assertTrue(db.diagnosticDao().batches().isEmpty())
        db.close()
    }

    @Test fun sessionSurvivesCloseAndReopen_simulatesAppRestart() = runBlocking {
        var db = open()
        var manager = SessionManager(RoomSessionStore(db), engine)
        val id = (manager.createSession(SessionDraft(TestMode.SPRAY_3X, scope = ScopeType.X3, weapon = "M416", cameraSensitivity = 120)) as SessionResult.Ok).session.sessionId
        manager.start(id)
        db.close()

        db = open()
        manager = SessionManager(RoomSessionStore(db), engine)
        val restored = db.sessionDao().get(id)
        assertNotNull(restored)
        assertEquals(SessionState.CAPTURE_PENDING.name, restored!!.state)
        assertEquals(120, restored.cameraSensitivity)
        assertNull(restored.adsSensitivity)               // unknown stays null in SQLite
        assertNull(restored.aimScore)
        assertEquals(id, db.sessionDao().findActive()!!.sessionId)
        assertNotNull(restored.startedAt)
        db.close()
    }

    @Test fun cancelPersistsAsCancelledAndFreesTheActiveSlot() = runBlocking {
        val db = open()
        val manager = SessionManager(RoomSessionStore(db), engine)
        val id = (manager.createSession(SessionDraft(TestMode.RED_DOT_SPRAY)) as SessionResult.Ok).session.sessionId
        manager.cancel(id)
        assertEquals(SessionState.CANCELLED.name, db.sessionDao().get(id)!!.state)
        assertNull(db.sessionDao().findActive())
        db.close()
    }

    @Test fun deleteRemovesSessionCapturesAndStepLink() = runBlocking {
        val db = open()
        val store = RoomSessionStore(db)
        val manager = SessionManager(store, engine)
        val id = (manager.createSession(SessionDraft(TestMode.SPRAY_4X)) as SessionResult.Ok).session.sessionId
        db.captureDao().insert(CaptureEntity("c1", id, null, null, null, null, null, null, null, null, "NOT_STARTED"))
        db.diagnosticDao().insertBatch(DiagnosticBatchEntity("b1", 1L, "PLANNED", null, null))
        db.diagnosticDao().insertSteps(listOf(DiagnosticStepEntity("b1", 0, "SPRAY_4X", null, id)))

        assertTrue(manager.delete(id))

        assertNull(db.sessionDao().get(id))
        assertTrue(db.captureDao().forSession(id).isEmpty())
        assertNull(db.diagnosticDao().steps("b1").single().sessionId)
        db.close()
    }

    @Test fun captureFieldsStayNullWhenNothingWasCaptured() = runBlocking {
        val db = open()
        db.sessionDao().insert(
            TestSessionEntity(
                sessionId = "s", createdAt = 1, startedAt = null, endedAt = null, pausedAt = null, pausedTotalMs = 0,
                state = "READY", testType = "SPRAY_3X", scope = null, weapon = null, muzzle = null, grip = null,
                magazine = null, stock = null, otherAttachment = null, distance = null, targetType = null,
                cameraSensitivity = null, adsSensitivity = null, gyroSensitivity = null, adsGyroSensitivity = null,
                fps = null, refreshRate = null, captureStatus = "NOT_STARTED", analysisStatus = "NOT_ANALYZED", notes = null
            )
        )
        db.captureDao().insert(CaptureEntity("c", "s", null, null, null, null, null, null, null, null, "NOT_STARTED"))
        val c = db.captureDao().forSession("s").single()
        assertNull(c.frameCount); assertNull(c.widthPx); assertNull(c.fps)
        assertNull(c.storageLocation); assertNull(c.permissionGranted)
        assertFalse(db.sessionDao().get("s")!!.hasAnalysis)
        db.close()
    }

    @Test fun deleteAllDataClearsEveryTable() = runBlocking {
        val db = open()
        val repo = AimForgeRepository(db)
        repo.completeOnboarding("dev", "4-finger claw", false)
        repo.saveSensitivity(ScopeType.X3, com.aimforge.app.domain.SensType.ADS, 115)
        SessionManager(RoomSessionStore(db), engine).createSession(SessionDraft(TestMode.SPRAY_3X))
        repo.deleteAllData()
        assertTrue(db.sessionDao().observeAll().first().isEmpty())
        assertTrue(db.sensitivityDao().observeAll().first().isEmpty())
        assertNull(db.profileDao().observe().first())
        db.close()
    }
}
