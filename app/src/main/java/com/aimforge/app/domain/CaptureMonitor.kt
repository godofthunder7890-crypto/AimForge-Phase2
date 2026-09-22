package com.aimforge.app.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Watches the real capture state. Saves progress while capturing and finalizes the session when the
 * capture ends on its own (system stop chip, notification Stop, failure). Reacts only to engine state.
 */
class CaptureMonitor(
    private val engine: CaptureEngine,
    private val manager: SessionManager,
    private val scope: CoroutineScope,
    private val progressIntervalMs: Long = 5_000L,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    fun start(): Job = scope.launch {
        var lastPersist = 0L
        engine.state.collect { snap ->
            val id = snap.sessionId ?: return@collect
            when (snap.status) {
                CaptureRuntimeStatus.RUNNING -> {
                    val now = clock()
                    if (now - lastPersist >= progressIntervalMs) {
                        lastPersist = now
                        manager.updateCaptureProgress(id, snap)
                    }
                }
                CaptureRuntimeStatus.STOPPED, CaptureRuntimeStatus.FAILED -> manager.onCaptureEnded(id, snap)
                else -> Unit
            }
        }
    }
}
