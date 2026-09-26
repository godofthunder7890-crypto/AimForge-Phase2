package com.aimforge.app.domain.cv

interface TemporalTracker {
    fun accept(detection: CrosshairDetection): TrackEvent
    fun reset()
}

/**
 * Turns a stream of per-frame crosshair detections into movement. Rules, all deliberate:
 * - A miss (not detected) never breaks tracking; it is just reported and the next real detection resumes from it.
 * - A gap longer than [maxGapMs] since the last detection is not bridged into a movement sample (dropped/missed
 *   frames or a target swap should not read as one huge flick).
 * - A jump implying a speed above [maxPlausibleSpeedNormPerSec] is rejected as detector noise rather than accepted
 *   as real movement, and does not move the tracked position forward.
 * - Out-of-order or duplicate timestamps are ignored rather than producing negative or infinite speed.
 */
class CrosshairTemporalTracker(
    private val maxGapMs: Long = 1500L,
    private val maxPlausibleSpeedNormPerSec: Float = 6f
) : TemporalTracker {
    private var last: CrosshairDetection? = null

    override fun accept(detection: CrosshairDetection): TrackEvent {
        if (!detection.detected || detection.xNorm == null || detection.yNorm == null) return TrackEvent.NoDetection

        val prev = last
        if (prev == null || prev.xNorm == null || prev.yNorm == null) {
            last = detection
            return TrackEvent.FirstDetection
        }

        val dt = detection.timestampMs - prev.timestampMs
        if (dt <= 0) return TrackEvent.NoDetection
        if (dt > maxGapMs) {
            last = detection
            return TrackEvent.GapTooLarge(dt)
        }

        val dx = detection.xNorm - prev.xNorm
        val dy = detection.yNorm - prev.yNorm
        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
        val speed = dist / (dt / 1000f)
        if (speed > maxPlausibleSpeedNormPerSec) return TrackEvent.ImplausibleJump(speed)

        val dirDeg = ((Math.toDegrees(kotlin.math.atan2(dy, dx).toDouble()).toFloat()) + 360f) % 360f
        last = detection
        return TrackEvent.Movement(MovementSample(prev.frameIndex, detection.frameIndex, dt, dx, dy, dist, speed, dirDeg))
    }

    override fun reset() { last = null }
}