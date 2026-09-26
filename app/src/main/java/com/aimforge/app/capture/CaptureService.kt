package com.aimforge.app.capture

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Display
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.IntentCompat
import com.aimforge.app.AimForgeApp
import com.aimforge.app.MainActivity
import com.aimforge.app.R
import com.aimforge.app.domain.CaptureRuntimeStatus
import com.aimforge.app.domain.cv.CvAnalysisEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

/**
 * Foreground service (type mediaProjection) that runs the real capture:
 * MediaProjection -> VirtualDisplay -> ImageReader. It counts frames Android really delivers and
 * samples a sparse pixel grid to detect black frames. It does not store frames, video or audio and
 * does not touch the network. Everything below runs on one HandlerThread, so counters need no locks.
 */
class CaptureService : Service() {

    companion object {
        const val ACTION_START = "com.aimforge.app.capture.START"
        const val ACTION_STOP = "com.aimforge.app.capture.STOP"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
        const val EXTRA_SESSION_ID = "sessionId"
        const val EXTRA_CAPTURE_ID = "captureId"

        private const val CHANNEL_ID = "capture"
        private const val NOTIFICATION_ID = 4201
        /** Longest side of the captured frame. Keeps memory and battery use low; Phase 4 works on this size. */
        private const val LONG_SIDE_PX = 1280
        private const val SAMPLE_EVERY_N_FRAMES = 15
        /** CV runs on every Nth captured frame, not every frame, to keep CPU/battery use bounded on the phone. */
        private const val ANALYSIS_EVERY_N_FRAMES = 3
        private const val PUBLISH_INTERVAL_MS = 250L
    }

    private lateinit var engine: MediaProjectionCaptureEngine
    private val finished = AtomicBoolean(false)
    @Volatile private var started = false
    private var activeSessionId: String? = null
    private var activeCaptureId: String? = null

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var projection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var displayListener: DisplayManager.DisplayListener? = null

    private var width = 0
    private var height = 0
    private var densityDpi = 0
    private var frameCount = 0
    private var sampled = 0
    private var blank = 0
    private var firstFrameElapsed = 0L
    private var lastFrameElapsed = 0L
    private var lastFrameWall = 0L
    private var lastPublishElapsed = 0L

    private var cvEngine: CvAnalysisEngine? = null
    private var analysisFramesSubmitted = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        engine = (application as AimForgeApp).captureEngine
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent.action) {
            ACTION_START -> {
                if (started) return START_NOT_STICKY
                activeSessionId = intent.getStringExtra(EXTRA_SESSION_ID)
                activeCaptureId = intent.getStringExtra(EXTRA_CAPTURE_ID)
                // A service started with startForegroundService must call startForeground quickly, always.
                try {
                    goForeground()
                } catch (e: Exception) {
                    publishForActiveCapture {
                        it.copy(
                            status = CaptureRuntimeStatus.FAILED,
                            stoppedAtMs = System.currentTimeMillis(),
                            failureReason = "Could not start foreground capture service: ${e.javaClass.simpleName}: ${e.message}"
                        )
                    }
                    stopSelf()
                    return START_NOT_STICKY
                }
                started = true
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = IntentCompat.getParcelableExtra(intent, EXTRA_RESULT_DATA, Intent::class.java)
                val t = HandlerThread("aimforge-capture").also { it.start() }
                thread = t
                val h = Handler(t.looper)
                handler = h
                h.post { beginCapture(resultCode, data) }
            }
            ACTION_STOP -> {
                val h = handler
                if (started && h != null) {
                    h.post { finish(CaptureRuntimeStatus.STOPPED, "Stopped by you.", null) }
                } else {
                    stopSelf()
                }
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (started && !finished.get()) {
            val h = handler
            if (h != null && Looper.myLooper() != h.looper) {
                h.post { finish(CaptureRuntimeStatus.STOPPED, "Capture service was stopped by Android.", null) }
            } else {
                finish(CaptureRuntimeStatus.STOPPED, "Capture service was stopped by Android.", null)
            }
        }
        super.onDestroy()
    }

    // ---- start ----

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Screen capture", NotificationManager.IMPORTANCE_LOW)
        )
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, CaptureService::class.java).setAction(ACTION_STOP), flags
        )
        val openIntent = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), flags)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_capture)
            .setContentTitle("AimForge is capturing your screen")
            .setContentText("Frames stay on this phone. Tap Stop to end capture.")
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, "Stop", stopIntent)
            .build()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    }

    private fun beginCapture(resultCode: Int, data: Intent?) {
        try {
            if (data == null) throw IllegalStateException("Missing screen-capture permission data.")
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val mp = mpm.getMediaProjection(resultCode, data)
                ?: throw IllegalStateException("Android returned no projection.")
            projection = mp

            // Android 14+: the callback must be registered before creating the VirtualDisplay.
            val cb = object : MediaProjection.Callback() {
                override fun onStop() {
                    finish(CaptureRuntimeStatus.STOPPED, "Screen capture was stopped by Android or by you (system capture control).", null)
                }
            }
            projectionCallback = cb
            mp.registerCallback(cb, handler)

            val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val (w, h, dpi) = captureSize(dm)
            width = w; height = h; densityDpi = dpi
            val r = newReader(w, h)
            reader = r
            virtualDisplay = mp.createVirtualDisplay(
                "AimForgeCapture", w, h, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, handler
            ) ?: throw IllegalStateException("Android could not create the capture display.")

            val listener = object : DisplayManager.DisplayListener {
                override fun onDisplayAdded(displayId: Int) = Unit
                override fun onDisplayRemoved(displayId: Int) = Unit
                override fun onDisplayChanged(displayId: Int) {
                    if (displayId == Display.DEFAULT_DISPLAY) onScreenChanged()
                }
            }
            displayListener = listener
            dm.registerDisplayListener(listener, handler)

            val startedWall = System.currentTimeMillis()
            cvEngine = CvAnalysisEngine()
            analysisFramesSubmitted = 0
            // Only now is a capture really running.
            publishForActiveCapture {
                it.copy(status = CaptureRuntimeStatus.RUNNING, width = w, height = h, startedAtMs = startedWall)
            }
        } catch (t: Throwable) {
            finish(CaptureRuntimeStatus.FAILED, null, "Could not start screen capture: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun captureSize(dm: DisplayManager): Triple<Int, Int, Int> {
        val display = dm.getDisplay(Display.DEFAULT_DISPLAY)
        val m = DisplayMetrics()
        display.getRealMetrics(m)
        val longSide = max(m.widthPixels, m.heightPixels).toFloat()
        val scale = min(1f, LONG_SIDE_PX / longSide)
        val w = max(2, (m.widthPixels * scale).toInt() and 1.inv())
        val h = max(2, (m.heightPixels * scale).toInt() and 1.inv())
        return Triple(w, h, m.densityDpi)
    }

    private fun newReader(w: Int, h: Int): ImageReader {
        val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3)
        r.setOnImageAvailableListener({ onFrame(it) }, handler)
        return r
    }

    /** Screen rotated (BGMI is landscape, AimForge may be portrait): resize the capture so frames keep full resolution. */
    private fun onScreenChanged() {
        if (finished.get()) return
        try {
            val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val (w, h, dpi) = captureSize(dm)
            if (w == width && h == height) return
            val newReader = newReader(w, h)
            virtualDisplay?.resize(w, h, dpi)
            virtualDisplay?.surface = newReader.surface
            val old = reader
            reader = newReader
            old?.setOnImageAvailableListener(null, null)
            old?.close()
            width = w; height = h; densityDpi = dpi
            publishProgress(force = true)
        } catch (t: Throwable) {
            finish(CaptureRuntimeStatus.FAILED, null, "Could not adapt capture to screen rotation: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    // ---- frames ----

    private fun onFrame(r: ImageReader) {
        if (finished.get()) return
        val image = try {
            r.acquireLatestImage()
        } catch (_: Exception) {
            null
        } ?: return
        try {
            val nowElapsed = SystemClock.elapsedRealtime()
            if (frameCount == 0) firstFrameElapsed = nowElapsed
            frameCount++
            lastFrameElapsed = nowElapsed
            lastFrameWall = System.currentTimeMillis()
            if (frameCount % SAMPLE_EVERY_N_FRAMES == 1) {
                val plane = image.planes[0]
                val s = FrameSampler.sample(plane.buffer, image.width, image.height, plane.rowStride, plane.pixelStride)
                sampled++
                if (s.isBlank) blank++
            }
            if (frameCount % ANALYSIS_EVERY_N_FRAMES == 1) {
                val plane = image.planes[0]
                val cvFrame = GraySampler.build(
                    plane.buffer,
                    image.width,
                    image.height,
                    plane.rowStride,
                    plane.pixelStride,
                    frameCount,
                    lastFrameWall
                )
                cvEngine?.onFrame(cvFrame)
                analysisFramesSubmitted++
            }
            publishProgress(force = frameCount == 1)
        } finally {
            image.close()
        }
    }

    private fun measuredFps(): Float? =
        if (frameCount >= 2 && lastFrameElapsed > firstFrameElapsed) {
            (frameCount - 1) * 1000f / (lastFrameElapsed - firstFrameElapsed)
        } else null

    private fun publishProgress(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastPublishElapsed < PUBLISH_INTERVAL_MS) return
        lastPublishElapsed = now
        val w = width
        val h = height
        val fps = measuredFps()
        val lastWall = if (frameCount > 0) lastFrameWall else null
        publishForActiveCapture {
            it.copy(
                status = CaptureRuntimeStatus.RUNNING,
                width = w, height = h,
                frameCount = frameCount,
                measuredFps = fps,
                sampledFrames = sampled,
                blankSampledFrames = blank,
                lastFrameAtMs = lastWall
            )
        }
    }

    // ---- stop ----

    /** Single exit path. Releases everything, publishes the final real counters, removes the notification. */
    private fun finish(status: CaptureRuntimeStatus, stopReason: String?, failureReason: String?) {
        if (!finished.compareAndSet(false, true)) return
        try { displayListener?.let { (getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).unregisterDisplayListener(it) } } catch (_: Throwable) {}
        try { reader?.setOnImageAvailableListener(null, null) } catch (_: Throwable) {}
        try { virtualDisplay?.release() } catch (_: Throwable) {}
        try { reader?.close() } catch (_: Throwable) {}
        try {
            projectionCallback?.let { projection?.unregisterCallback(it) }
            projection?.stop()
        } catch (_: Throwable) {}

        val w = if (width > 0) width else null
        val h = if (height > 0) height else null
        val fps = measuredFps()
        val lastWall = if (frameCount > 0) lastFrameWall else null
        val stoppedWall = System.currentTimeMillis()

        val sid = activeSessionId
        val cv = cvEngine
        if (sid != null && cv != null) {
            val dropped = (frameCount - analysisFramesSubmitted).coerceAtLeast(0)
            val result = cv.finalizeAnalysis(sid, dropped)
            val app = application as AimForgeApp
            app.appScope.launch(Dispatchers.IO) { app.cvAnalysisStore.save(result) }
        }
        cvEngine = null

        publishForActiveCapture {
            it.copy(
                status = status,
                width = w, height = h,
                frameCount = frameCount,
                measuredFps = fps,
                sampledFrames = sampled,
                blankSampledFrames = blank,
                lastFrameAtMs = lastWall,
                stoppedAtMs = stoppedWall,
                stopReason = stopReason,
                failureReason = failureReason
            )
        }
        try { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) } catch (_: Throwable) {}
        thread?.quitSafely()
        stopSelf()
    }

    /**
     * A late callback from an old service instance must not overwrite a newer capture's state.
     * The IDs are assigned by SessionManager before the Android service is started.
     */
    private fun publishForActiveCapture(
        transform: (com.aimforge.app.domain.CaptureSnapshot) -> com.aimforge.app.domain.CaptureSnapshot
    ) {
        val sessionId = activeSessionId
        val captureId = activeCaptureId
        if (sessionId == null || captureId == null) return
        engine.publish { current ->
            if (current.sessionId != sessionId || current.captureId != captureId) current
            else transform(current)
        }
    }
}
