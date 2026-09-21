package com.aimforge.app.capture

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.aimforge.app.domain.Availability
import com.aimforge.app.domain.CaptureEngine
import com.aimforge.app.domain.CaptureGrant
import com.aimforge.app.domain.CaptureOutcome
import com.aimforge.app.domain.CaptureRuntimeStatus
import com.aimforge.app.domain.CaptureSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Real screen capture through Android MediaProjection. The heavy lifting lives in [CaptureService]
 * (a foreground service of type mediaProjection). This class owns the observable state and the
 * start/stop handshake. State only says RUNNING after the service created the VirtualDisplay.
 */
class MediaProjectionCaptureEngine(private val app: Application) : CaptureEngine {
    private val _state = MutableStateFlow(CaptureSnapshot())
    override val state: StateFlow<CaptureSnapshot> = _state.asStateFlow()

    override fun availability(): Availability =
        if (app.getSystemService(Context.MEDIA_PROJECTION_SERVICE) != null) Availability.Available
        else Availability.NotAvailable("This device does not provide Android screen capture (MediaProjection).")

    /** Used by [CaptureService] only. */
    internal fun publish(transform: (CaptureSnapshot) -> CaptureSnapshot) {
        _state.update { current ->
            val next = transform(current)
            /*
             * Once this capture has reached a terminal state, a queued service callback from
             * cleanup must not make it look alive again (for example, timeout -> FAILED followed
             * by the service's STOPPED callback).
             */
            if (current.captureId == next.captureId &&
                current.status in setOf(CaptureRuntimeStatus.STOPPED, CaptureRuntimeStatus.FAILED) &&
                next.status != current.status
            ) {
                current
            } else {
                next
            }
        }
    }

    override suspend fun start(sessionId: String, captureId: String, grant: CaptureGrant): CaptureOutcome {
        val data = grant.token as? Intent ?: return CaptureOutcome.Failed("Invalid screen-capture permission result.")
        val cur = _state.value
        if (cur.status == CaptureRuntimeStatus.STARTING || cur.status == CaptureRuntimeStatus.RUNNING) {
            return CaptureOutcome.Failed("A capture is already running.")
        }
        _state.value = CaptureSnapshot(status = CaptureRuntimeStatus.STARTING, sessionId = sessionId, captureId = captureId)

        val intent = Intent(app, CaptureService::class.java).apply {
            action = CaptureService.ACTION_START
            putExtra(CaptureService.EXTRA_RESULT_CODE, grant.resultCode)
            putExtra(CaptureService.EXTRA_RESULT_DATA, data)
            putExtra(CaptureService.EXTRA_SESSION_ID, sessionId)
            putExtra(CaptureService.EXTRA_CAPTURE_ID, captureId)
        }
        try {
            ContextCompat.startForegroundService(app, intent)
        } catch (e: Exception) {
            return failNow(sessionId, captureId, "Could not start the capture service: ${e.javaClass.simpleName}: ${e.message}")
        }

        val result = withTimeoutOrNull(START_TIMEOUT_MS) {
            _state.first { it.captureId == captureId && it.status != CaptureRuntimeStatus.STARTING }
        }
        return when {
            result == null -> {
                sendStop()
                failNow(sessionId, captureId, "Capture did not start within ${START_TIMEOUT_MS / 1000} seconds.")
            }
            result.status == CaptureRuntimeStatus.RUNNING -> CaptureOutcome.Started(result)
            else -> CaptureOutcome.Failed(result.failureReason ?: result.stopReason ?: "Capture ended immediately.")
        }
    }

    override suspend fun stop(sessionId: String): CaptureOutcome {
        val cur = _state.value
        if (cur.sessionId != sessionId) return CaptureOutcome.Failed("No capture is running for this session.")
        when (cur.status) {
            CaptureRuntimeStatus.STOPPED -> return CaptureOutcome.Stopped(cur)
            CaptureRuntimeStatus.FAILED -> return CaptureOutcome.Failed(cur.failureReason ?: "Capture failed.")
            CaptureRuntimeStatus.IDLE -> return CaptureOutcome.Failed("No capture is running.")
            else -> Unit
        }
        sendStop()
        val res = withTimeoutOrNull(STOP_TIMEOUT_MS) {
            _state.first {
                it.sessionId == sessionId &&
                    (it.status == CaptureRuntimeStatus.STOPPED || it.status == CaptureRuntimeStatus.FAILED)
            }
        }
        return when {
            res == null -> CaptureOutcome.Failed("Capture did not confirm stop in time.")
            res.status == CaptureRuntimeStatus.STOPPED -> CaptureOutcome.Stopped(res)
            else -> CaptureOutcome.Failed(res.failureReason ?: "Capture failed.")
        }
    }

    private fun sendStop() {
        try {
            app.startService(Intent(app, CaptureService::class.java).setAction(CaptureService.ACTION_STOP))
        } catch (_: Exception) {
            // Service may already be gone; state handling below covers the outcome.
        }
    }

    private fun failNow(sessionId: String, captureId: String, reason: String): CaptureOutcome {
        _state.value = CaptureSnapshot(
            status = CaptureRuntimeStatus.FAILED,
            sessionId = sessionId,
            captureId = captureId,
            stoppedAtMs = System.currentTimeMillis(),
            failureReason = reason
        )
        return CaptureOutcome.Failed(reason)
    }

    private companion object {
        const val START_TIMEOUT_MS = 10_000L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
