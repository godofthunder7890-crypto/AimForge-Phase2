package com.aimforge.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.aimforge.app.domain.metrics.AimMetricsResult
import com.aimforge.app.domain.metrics.AimMetricsStatus
import com.aimforge.app.domain.metrics.DataQuality
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Builds a real version-4 database (Phase 4 schema, no aim_metrics table), then opens it with MIGRATION_4_5. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration45Test {
    @Test fun migration4to5_addsAimMetricsTable_keepsEverythingElse() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration45-test.db"
        ctx.deleteDatabase(name)

        val v4 = Room.databaseBuilder(ctx, AppDatabaseV4Stub::class.java, name)
            .allowMainThreadQueries()
            .build()
        v4.sessionDao().insert(
            TestSessionEntity(
                sessionId = "m45", createdAt = 1, startedAt = null, endedAt = null, pausedAt = null, pausedTotalMs = 0,
                state = "READY", testType = "SPRAY_3X", scope = null, weapon = null, muzzle = null, grip = null,
                magazine = null, stock = null, otherAttachment = null, distance = null, targetType = null,
                cameraSensitivity = null, adsSensitivity = null, gyroSensitivity = null, adsGyroSensitivity = null,
                fps = null, refreshRate = null, captureStatus = "NOT_STARTED", analysisStatus = "NOT_ANALYZED",
                notes = null, failureReason = null
            )
        )
        v4.close()

        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()

        assertEquals(1, db.sessionDao().observeAll().first().size)
        assertTrue(db.aimMetricsDao().observeAll().first().isEmpty())

        val store = RoomAimMetricsStore(db)
        store.save(
            AimMetricsResult(
                sessionId = "m45", status = AimMetricsStatus.ANALYZED,
                quality = DataQuality(
                    framesProcessed = 40, validCrosshairSamples = 38, validTargetSamples = 0,
                    crosshairDetectionRate = 0.95f, targetDetectionRate = 0f, droppedFrames = 5,
                    timestampGapCount = 0, invalidTimestampSamples = 0, avgCrosshairConfidence = 0.7f,
                    minCrosshairConfidence = 0.4f, trackingDurationMs = 4000L
                ),
                totalMovementDistance = 1.2f, averageMovementDistance = 0.03f, medianMovementDistance = 0.02f,
                horizontalMovementTotal = 0.9f, verticalMovementTotal = 0.3f, horizontalMovementNet = 0.1f, verticalMovementNet = -0.05f,
                averageSpeed = 0.3f, medianSpeed = 0.25f, peakSpeed = 1.1f,
                directionChangeCount = 4, directionChangeRate = 1.0f,
                averageAcceleration = 2.0f, peakAcceleration = 5.0f, peakDeceleration = 4.5f,
                velocityVariance = 0.02f, accelerationVariance = 0.5f, directionReversalRate = 0.2f,
                microAdjustmentCount = 6, microAdjustmentFrequency = 1.5f, averageMicroAdjustmentMagnitude = 0.008f, medianMicroAdjustmentMagnitude = 0.007f,
                overshootCount = null, correctionCount = null, averageCorrectionMagnitude = null, averageCorrectionTimeMs = null,
                averageTargetError = null, medianTargetError = null, minimumTargetError = null, maximumTargetError = null,
                targetErrorVariance = null, timeWithinTargetRegionMs = null,
                trackingContinuity = 0.9f, crosshairObservationCoverage = 0.95f, computedAtMs = 9999L, analysisDurationMs = 12L
            )
        )
        val saved = db.aimMetricsDao().forSession("m45")!!
        assertEquals("ANALYZED", saved.status)
        assertEquals(40, saved.framesProcessed)
        assertNull(saved.overshootCount)      // unavailable metric stays NULL through Room, not 0
        assertEquals(0, saved.validTargetSamples) // a real measured zero stays 0
        db.close()
        ctx.deleteDatabase(name)
    }
}

/** Same shape as AppDatabase but pinned at version 4 (no aim_metrics), to seed a real pre-Phase-5 database file. */
@androidx.room.Database(
    entities = [
        PlayerProfileEntity::class, SensitivityValueEntity::class, TestSessionEntity::class,
        CaptureEntity::class, DiagnosticBatchEntity::class, DiagnosticStepEntity::class, CvAnalysisEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabaseV4Stub : androidx.room.RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun cvAnalysisDao(): CvAnalysisDao
}
