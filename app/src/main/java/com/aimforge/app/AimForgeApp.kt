package com.aimforge.app

import android.app.Application
import com.aimforge.app.capture.MediaProjectionCaptureEngine
import com.aimforge.app.data.AimForgeRepository
import com.aimforge.app.data.AppDatabase
import com.aimforge.app.data.RoomSessionStore
import com.aimforge.app.domain.CaptureMonitor
import com.aimforge.app.domain.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AimForgeApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database: AppDatabase by lazy { AppDatabase.create(this) }
    val repository: AimForgeRepository by lazy { AimForgeRepository(database) }

    /** Phase 3: real MediaProjection capture. Nothing runs until the user approves a capture for a session. */
    val captureEngine: MediaProjectionCaptureEngine by lazy { MediaProjectionCaptureEngine(this) }

    val sessionManager: SessionManager by lazy {
        SessionManager(RoomSessionStore(database), captureEngine)
    }

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            // A capture cannot survive a process restart, so a session still marked CAPTURING is really failed.
            sessionManager.recoverInterrupted()
            // Saves capture results even when no screen is open (system stop chip, notification Stop).
            CaptureMonitor(captureEngine, sessionManager, appScope).start()
        }
    }
}
