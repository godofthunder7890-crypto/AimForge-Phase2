package com.aimforge.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM player_profile WHERE id = 1")
    fun observe(): Flow<PlayerProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: PlayerProfileEntity)
}

@Dao
interface SensitivityDao {
    @Query("SELECT * FROM sensitivity_values")
    fun observeAll(): Flow<List<SensitivityValueEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(value: SensitivityValueEntity)

    @Query("DELETE FROM sensitivity_values WHERE scope = :scope AND type = :type")
    suspend fun delete(scope: String, type: String)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM test_sessions ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TestSessionEntity>>

    @Query("SELECT * FROM test_sessions WHERE sessionId = :id")
    suspend fun get(id: String): TestSessionEntity?

    @Query("SELECT * FROM test_sessions WHERE endedAt IS NULL AND state IN ('READY', 'CAPTURE_PENDING', 'CAPTURING') LIMIT 1")
    suspend fun findActive(): TestSessionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: TestSessionEntity)

    @Update
    suspend fun update(session: TestSessionEntity)

    @Query("DELETE FROM test_sessions WHERE sessionId = :id")
    suspend fun delete(id: String)
}

@Dao
interface CaptureDao {
    @Query("SELECT * FROM captures ORDER BY startTime ASC")
    fun observeAll(): Flow<List<CaptureEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(capture: CaptureEntity)

    @Update
    suspend fun update(capture: CaptureEntity)

    @Query("SELECT * FROM captures WHERE captureId = :captureId")
    suspend fun get(captureId: String): CaptureEntity?

    @Query("SELECT * FROM captures WHERE sessionId = :sessionId ORDER BY startTime DESC LIMIT 1")
    suspend fun latestForSession(sessionId: String): CaptureEntity?

    @Query("SELECT * FROM captures WHERE sessionId = :sessionId")
    suspend fun forSession(sessionId: String): List<CaptureEntity>

    @Query("DELETE FROM captures WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: String)
}

@Dao
interface DiagnosticDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBatch(batch: DiagnosticBatchEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSteps(steps: List<DiagnosticStepEntity>)

    @Query("SELECT * FROM diagnostic_batches ORDER BY createdAt DESC")
    suspend fun batches(): List<DiagnosticBatchEntity>

    @Query("SELECT * FROM diagnostic_steps WHERE batchId = :batchId ORDER BY stepIndex")
    suspend fun steps(batchId: String): List<DiagnosticStepEntity>

    @Query("UPDATE diagnostic_steps SET sessionId = NULL WHERE sessionId = :sessionId")
    suspend fun unlinkSession(sessionId: String)
}
