package com.aimforge.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Builds a real version-1 database (Phase 1 schema), then opens it with Room + MIGRATION_1_2.
 * Room validates the migrated schema against the entities, so a wrong migration fails this test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    @Test fun migration1to3_keepsProfileAndSensitivity_replacesSessionTable(): Unit = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        ctx.deleteDatabase(name)

        val v1 = object : SQLiteOpenHelper(ctx, name, null, 1) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL("CREATE TABLE player_profile (id INTEGER NOT NULL, deviceName TEXT NOT NULL, controlLayout TEXT NOT NULL, gyroEnabled INTEGER NOT NULL, onboardingDone INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE TABLE sensitivity_values (scope TEXT NOT NULL, type TEXT NOT NULL, value INTEGER NOT NULL, PRIMARY KEY(scope, type))")
                db.execSQL("CREATE TABLE test_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, timestamp INTEGER NOT NULL, mode TEXT NOT NULL)")
                db.execSQL("INSERT INTO player_profile VALUES (1, 'my phone', '4-finger claw', 0, 1)")
                db.execSQL("INSERT INTO sensitivity_values VALUES ('X3', 'ADS', 115)")
            }
            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        v1.writableDatabase.close()

        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        val profile = db.profileDao().observe().first()
        assertNotNull(profile)
        assertEquals("my phone", profile!!.deviceName)
        assertTrue(profile.onboardingDone)
        assertEquals(115, db.sensitivityDao().observeAll().first().single().value)
        assertTrue(db.sessionDao().observeAll().first().isEmpty())

        db.sessionDao().insert(
            TestSessionEntity(
                sessionId = "m", createdAt = 1, startedAt = null, endedAt = null, pausedAt = null, pausedTotalMs = 0,
                state = "READY", testType = "SPRAY_3X", scope = null, weapon = null, muzzle = null, grip = null,
                magazine = null, stock = null, otherAttachment = null, distance = null, targetType = null,
                cameraSensitivity = null, adsSensitivity = null, gyroSensitivity = null, adsGyroSensitivity = null,
                fps = null, refreshRate = null, captureStatus = "NOT_STARTED", analysisStatus = "NOT_ANALYZED", notes = null
            )
        )
        assertEquals(1, db.sessionDao().observeAll().first().size)
        db.close()
        ctx.deleteDatabase(name)
    }
}
