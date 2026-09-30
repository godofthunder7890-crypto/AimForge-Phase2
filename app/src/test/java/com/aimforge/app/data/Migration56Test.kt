package com.aimforge.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real v5 database (aim_metrics without crosshairObservationCoverage) migrated with MIGRATION_5_6. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration56Test {
    @Test fun migration5to6_addsColumn_oldRowsReadAsNullNotZero() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration56-test.db"
        ctx.deleteDatabase(name)

        val helper = object : android.database.sqlite.SQLiteOpenHelper(ctx, name, null, 5) {
            override fun onCreate(db: android.database.sqlite.SQLiteDatabase) {
                db.execSQL("CREATE TABLE player_profile (id INTEGER NOT NULL, deviceName TEXT NOT NULL, controlLayout TEXT NOT NULL, gyroEnabled INTEGER NOT NULL, onboardingDone INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE TABLE sensitivity_values (scope TEXT NOT NULL, type TEXT NOT NULL, value INTEGER NOT NULL, PRIMARY KEY(scope, type))")
                db.execSQL("CREATE TABLE test_sessions (sessionId TEXT NOT NULL, createdAt INTEGER NOT NULL, startedAt INTEGER, endedAt INTEGER, pausedAt INTEGER, pausedTotalMs INTEGER NOT NULL, state TEXT NOT NULL, testType TEXT NOT NULL, scope TEXT, weapon TEXT, muzzle TEXT, grip TEXT, magazine TEXT, stock TEXT, otherAttachment TEXT, distance TEXT, targetType TEXT, cameraSensitivity INTEGER, adsSensitivity INTEGER, gyroSensitivity INTEGER, adsGyroSensitivity INTEGER, fps INTEGER, refreshRate INTEGER, captureStatus TEXT NOT NULL, analysisStatus TEXT NOT NULL, notes TEXT, aimScore INTEGER, aimErrorPx REAL, stability INTEGER, errorState TEXT, confidence INTEGER, recommendation TEXT, failureReason TEXT, PRIMARY KEY(sessionId))")
                db.execSQL("CREATE TABLE captures (captureId TEXT NOT NULL, sessionId TEXT NOT NULL, startTime INTEGER, endTime INTEGER, frameCount INTEGER, widthPx INTEGER, heightPx INTEGER, fps REAL, storageLocation TEXT, permissionGranted INTEGER, status TEXT NOT NULL, sampledFrameCount INTEGER, blankFrameCount INTEGER, stopReason TEXT, PRIMARY KEY(captureId))")
                db.execSQL("CREATE TABLE diagnostic_batches (batchId TEXT NOT NULL, createdAt INTEGER NOT NULL, state TEXT NOT NULL, weapon TEXT, notes TEXT, PRIMARY KEY(batchId))")
                db.execSQL("CREATE TABLE diagnostic_steps (batchId TEXT NOT NULL, stepIndex INTEGER NOT NULL, testType TEXT NOT NULL, scope TEXT, sessionId TEXT, PRIMARY KEY(batchId, stepIndex))")
                db.execSQL("CREATE TABLE cv_analyses (sessionId TEXT NOT NULL, state TEXT NOT NULL, framesProcessed INTEGER NOT NULL, framesWithCrosshair INTEGER NOT NULL, crosshairDetectionRate REAL, avgCrosshairConfidence REAL, movementSamples INTEGER NOT NULL, avgMovementSpeedNormPerSec REAL, totalMovementDistanceNorm REAL, rejectedJumps INTEGER NOT NULL, missedDetections INTEGER NOT NULL, longestGapMs INTEGER, framesWithTarget INTEGER NOT NULL, targetDetectionRate REAL, droppedFrames INTEGER NOT NULL, algorithmVersion TEXT NOT NULL, computedAtMs INTEGER NOT NULL, PRIMARY KEY(sessionId))")
                db.execSQL(
                    "CREATE TABLE aim_metrics (sessionId TEXT NOT NULL, status TEXT NOT NULL, framesProcessed INTEGER NOT NULL, validCrosshairSamples INTEGER NOT NULL, validTargetSamples INTEGER NOT NULL, " +
                        "crosshairDetectionRate REAL, targetDetectionRate REAL, droppedFrames INTEGER NOT NULL, timestampGapCount INTEGER NOT NULL, invalidTimestampSamples INTEGER NOT NULL, " +
                        "avgCrosshairConfidence REAL, minCrosshairConfidence REAL, trackingDurationMs INTEGER, totalMovementDistance REAL, averageMovementDistance REAL, medianMovementDistance REAL, " +
                        "horizontalMovementTotal REAL, verticalMovementTotal REAL, horizontalMovementNet REAL, verticalMovementNet REAL, averageSpeed REAL, medianSpeed REAL, peakSpeed REAL, " +
                        "directionChangeCount INTEGER, directionChangeRate REAL, averageAcceleration REAL, peakAcceleration REAL, peakDeceleration REAL, velocityVariance REAL, accelerationVariance REAL, directionReversalRate REAL, " +
                        "microAdjustmentCount INTEGER, microAdjustmentFrequency REAL, averageMicroAdjustmentMagnitude REAL, medianMicroAdjustmentMagnitude REAL, overshootCount INTEGER, correctionCount INTEGER, " +
                        "averageCorrectionMagnitude REAL, averageCorrectionTimeMs REAL, averageTargetError REAL, medianTargetError REAL, minimumTargetError REAL, maximumTargetError REAL, targetErrorVariance REAL, " +
                        "timeWithinTargetRegionMs INTEGER, trackingContinuity REAL, computedAtMs INTEGER NOT NULL, analysisDurationMs INTEGER, PRIMARY KEY(sessionId))"
                )
                db.execSQL("INSERT INTO aim_metrics (sessionId, status, framesProcessed, validCrosshairSamples, validTargetSamples, droppedFrames, timestampGapCount, invalidTimestampSamples, trackingContinuity, computedAtMs) VALUES ('old', 'PARTIAL_ANALYSIS', 10, 8, 0, 0, 0, 0, 0.8, 1)")
            }
            override fun onUpgrade(db: android.database.sqlite.SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        helper.writableDatabase.close()

        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()
        val row = db.aimMetricsDao().forSession("old")!!
        assertEquals("PARTIAL_ANALYSIS", row.status)
        assertEquals(0.8f, row.trackingContinuity!!, 0.001f)
        assertNull(row.crosshairObservationCoverage) // old rows stay unavailable, never backfilled
        db.close()
        ctx.deleteDatabase(name)
    }
}
