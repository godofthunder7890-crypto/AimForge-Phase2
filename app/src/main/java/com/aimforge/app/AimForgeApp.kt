package com.aimforge.app

import android.app.Application
import com.aimforge.app.data.AimForgeRepository
import com.aimforge.app.data.AppDatabase
import com.aimforge.app.data.RoomSessionStore
import com.aimforge.app.domain.NotImplementedCaptureEngine
import com.aimforge.app.domain.SessionManager

class AimForgeApp : Application() {
    private val database: AppDatabase by lazy { AppDatabase.create(this) }
    val repository: AimForgeRepository by lazy { AimForgeRepository(database) }

    /** Phase 2: capture engine is the explicit NOT_IMPLEMENTED placeholder. Phase 3 swaps it. */
    val sessionManager: SessionManager by lazy {
        SessionManager(RoomSessionStore(database), NotImplementedCaptureEngine())
    }
}
