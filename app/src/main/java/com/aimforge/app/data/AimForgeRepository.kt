package com.aimforge.app.data

import com.aimforge.app.domain.ScopeType
import com.aimforge.app.domain.SensType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AimForgeRepository(private val db: AppDatabase) {
    val profile: Flow<PlayerProfileEntity?> = db.profileDao().observe()
    val sensitivities: Flow<List<SensitivityValueEntity>> = db.sensitivityDao().observeAll()
    val sessions: Flow<List<TestSessionEntity>> = db.sessionDao().observeAll()
    val captures: Flow<List<CaptureEntity>> = db.captureDao().observeAll()
    val cvAnalyses: Flow<List<CvAnalysisEntity>> = db.cvAnalysisDao().observeAll()

    suspend fun completeOnboarding(device: String, control: String, gyro: Boolean) {
        db.profileDao().upsert(
            PlayerProfileEntity(
                deviceName = device.trim(),
                controlLayout = control,
                gyroEnabled = gyro,
                onboardingDone = true
            )
        )
    }

    /** null value = clear that entry. */
    suspend fun saveSensitivity(scope: ScopeType, type: SensType, value: Int?) {
        if (value == null) {
            db.sensitivityDao().delete(scope.name, type.name)
        } else {
            db.sensitivityDao().upsert(SensitivityValueEntity(scope.name, type.name, value))
        }
    }

    suspend fun deleteAllData() = withContext(Dispatchers.IO) {
        db.clearAllTables()
    }
}
