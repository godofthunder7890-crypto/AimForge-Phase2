package com.aimforge.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PlayerProfileEntity::class,
        SensitivityValueEntity::class,
        TestSessionEntity::class,
        CaptureEntity::class,
        DiagnosticBatchEntity::class,
        DiagnosticStepEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun sensitivityDao(): SensitivityDao
    abstract fun sessionDao(): SessionDao
    abstract fun captureDao(): CaptureDao
    abstract fun diagnosticDao(): DiagnosticDao

    companion object {
        /**
         * v1 -> v2. Profile and sensitivity tables are kept untouched.
         * The Phase 1 test_sessions table had a different shape and no code path ever wrote to it,
         * so it is replaced by the real session schema.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS test_sessions")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS test_sessions (" +
                        "sessionId TEXT NOT NULL, createdAt INTEGER NOT NULL, startedAt INTEGER, endedAt INTEGER, " +
                        "pausedAt INTEGER, pausedTotalMs INTEGER NOT NULL, state TEXT NOT NULL, " +
                        "testType TEXT NOT NULL, scope TEXT, weapon TEXT, muzzle TEXT, grip TEXT, magazine TEXT, " +
                        "stock TEXT, otherAttachment TEXT, distance TEXT, targetType TEXT, " +
                        "cameraSensitivity INTEGER, adsSensitivity INTEGER, gyroSensitivity INTEGER, " +
                        "adsGyroSensitivity INTEGER, fps INTEGER, refreshRate INTEGER, " +
                        "captureStatus TEXT NOT NULL, analysisStatus TEXT NOT NULL, notes TEXT, " +
                        "aimScore INTEGER, aimErrorPx REAL, stability INTEGER, errorState TEXT, " +
                        "confidence INTEGER, recommendation TEXT, PRIMARY KEY(sessionId))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS captures (" +
                        "captureId TEXT NOT NULL, sessionId TEXT NOT NULL, startTime INTEGER, endTime INTEGER, " +
                        "frameCount INTEGER, widthPx INTEGER, heightPx INTEGER, fps REAL, storageLocation TEXT, " +
                        "permissionGranted INTEGER, status TEXT NOT NULL, PRIMARY KEY(captureId))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS diagnostic_batches (" +
                        "batchId TEXT NOT NULL, createdAt INTEGER NOT NULL, state TEXT NOT NULL, " +
                        "weapon TEXT, notes TEXT, PRIMARY KEY(batchId))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS diagnostic_steps (" +
                        "batchId TEXT NOT NULL, stepIndex INTEGER NOT NULL, testType TEXT NOT NULL, " +
                        "scope TEXT, sessionId TEXT, PRIMARY KEY(batchId, stepIndex))"
                )
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "aimforge.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
