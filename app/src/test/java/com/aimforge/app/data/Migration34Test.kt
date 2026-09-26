package com.aimforge.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.aimforge.app.domain.cv.CvAnalysisState
import com.aimforge.app.domain.cv.CvSessionAnalysis
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Builds a real version-3 database (Phase 3 schema, no cv_analyses table), then opens it with MIGRATION_3_4. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration34Test {
    @Test fun migration3to4_addsCvAnalysesTable_keepsEverythingElse(): Unit = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration34-test.db"
        ctx.deleteDatabase(name)

        val v3 = Room.databaseBuilder(ctx, AppDatabaseV3Stub::class.java, name)
            .addMigrations()
            .allowMainThreadQueries()
            .build()
        v3.sessionDao().insert(
            TestSessionEntity(
                sessionId = "m34", createdAt = 1, startedAt = null, endedAt = null, pausedAt = null, pausedTotalMs = 0,
                state = "READY", testType = "SPRAY_3X", scope = null, weapon = null, muzzle = null, grip = null,
                magazine = null, stock = null, otherAttachment = null, distance = null, targetType = null,
                cameraSensitivity = null, adsSensitivity = null, gyroSensitivity = null, adsGyroSensitivity = null,
                fps = null, refreshRate = null, captureStatus = "NOT_STARTED", analysisStatus = "NOT_ANALYZED",
                notes = null, failureReason = null
            )
        )
        v3.close()

        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()

        assertEquals(1, db.sessionDao().observeAll().first().size)
        assertEquals("m34", db.sessionDao().get("m34")!!.sessionId)
        assertTrue(db.cvAnalysisDao().observeAll().first().isEmpty())
        val store = RoomCvAnalysisStore(db)
        store.save(
            CvSessionAnalysis(
                sessionId = "m34", state = CvAnalysisState.ANALYZED, framesProcessed = 40, framesWithCrosshair = 38,
                crosshairDetectionRate = 0.95f, avgCrosshairConfidence = 0.7f, movementSamples = 30,
                avgMovementSpeedNormPerSec = 0.3f, totalMovementDistanceNorm = 1.2f, rejectedJumps = 1,
                missedDetections = 2, longestGapMs = 200L, framesWithTarget = 0, targetDetectionRate = 0f,
                droppedFrames = 15, algorithmVersion = "center-bright-cluster-v1", computedAtMs = 9999L
            )
        )
        val saved = db.cvAnalysisDao().forSession("m34")!!
        assertEquals("ANALYZED", saved.state)
        assertEquals(40, saved.framesProcessed)
        assertEquals(0, saved.framesWithTarget)
        db.close()
        ctx.deleteDatabase(name)
    }
}

/** Same shape as AppDatabase but pinned at version 3 (no cv_analyses), just to seed a real pre-Phase-4 database file. */
@androidx.room.Database(
    entities = [
        PlayerProfileEntity::class, SensitivityValueEntity::class, TestSessionEntity::class,
        CaptureEntity::class, DiagnosticBatchEntity::class, DiagnosticStepEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabaseV3Stub : androidx.room.RoomDatabase() {
    abstract fun sessionDao(): SessionDao
}