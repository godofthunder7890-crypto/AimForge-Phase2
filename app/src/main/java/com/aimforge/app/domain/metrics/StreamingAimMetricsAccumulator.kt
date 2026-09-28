package com.aimforge.app.domain.metrics

import com.aimforge.app.domain.cv.CrosshairDetection
import com.aimforge.app.domain.cv.FrameObservation
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Computes [AimMetricsResult] incrementally, one real [FrameObservation] at a time, in O(1) extra
 * memory per frame (plus three small bounded [ReservoirSampler]s for median-style statistics).
 *
 * Why streaming: an earlier version of this pipeline buffered every observation from a session and
 * ran the metric math once at the end, capped at the last 1200 observations to bound memory. On a
 * real long BGMI session that capped buffer silently dropped everything before its last ~1200
 * analyzed frames, so the final metrics represented only the tail of the session, not the whole
 * thing. This class instead maintains running sums/counts/extremes (exact) and reservoir samples
 * (unbiased across the WHOLE stream) so a session of any length is represented in full, in bounded
 * memory — see also [CvAnalysisEngine] which now feeds this class directly from its own onFrame().
 *
 * Sums use Double internally to keep long-session accumulation numerically stable; results are Float
 * to match the rest of the app.
 */
class StreamingAimMetricsAccumulator(
    private val minConfidence: Float = 0.3f,
    private val directionDeadZoneNorm: Float = 0.004f,
    private val microAdjustmentMaxNorm: Float = 0.012f,
    private val maxGapMs: Long = 1500L,
    private val closeToTargetNorm: Float = 0.05f,
    private val maxReacquireMs: Long = 800L,
    private val minFramesForAnalysis: Int = 20,
    private val minMovementPairsForAnalysis: Int = 5,
    private val reservoirCapacity: Int = 2000,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val startedAt = clock()

    private var framesProcessed = 0
    private var validCrosshairSamples = 0
    private var validTargetSamples = 0
    private var confidenceSum = 0.0
    private var confidenceCount = 0
    private var confidenceMin: Float? = null
    private var firstTimestampMs: Long? = null
    private var lastTimestampMs: Long? = null
    private var timestampGapCount = 0
    private var invalidTimestampSamples = 0

    private var lastConfidentCrosshair: CrosshairDetection? = null
    private var lastPair: MovementPair? = null

    private var pairCount = 0
    private var distanceSum = 0.0
    private var speedSum = 0.0
    private var speedSumSq = 0.0
    private var peakSpeed: Float? = null
    private var dxAbsSum = 0.0
    private var dyAbsSum = 0.0
    private var dxNetSum = 0.0
    private var dyNetSum = 0.0
    private var trackedSegmentMs = 0L
    private val distanceReservoir = ReservoirSampler(reservoirCapacity)
    private val speedReservoir = ReservoirSampler(reservoirCapacity)

    private var contiguousAdjacentCount = 0
    private var reversalCount = 0
    private var directionChangeCount = 0
    private var accelSum = 0.0
    private var accelAbsSum = 0.0
    private var accelSumSq = 0.0
    private var accelCount = 0
    private var peakAccel: Float? = null
    private var peakDecelMagnitude: Float? = null

    private var microCount = 0
    private var microMagnitudeSum = 0.0
    private val microReservoir = ReservoirSampler(reservoirCapacity)

    private var targetErrorSum = 0.0
    private var targetErrorSumSq = 0.0
    private var targetErrorCount = 0
    private var targetErrorMin: Float? = null
    private var targetErrorMax: Float? = null
    private var withinTargetRegionMs = 0L
    private val targetErrorReservoir = ReservoirSampler(reservoirCapacity)

    private var lastAlignedWasClose: Boolean? = null
    private var lastAlignedTimestampMs: Long? = null
    private var leftCloseAtMs: Long? = null
    private var peakErrorSinceLeaving = 0f
    private var overshootCount = 0
    private var correctionCount = 0
    private var correctionMagnitudeSum = 0.0
    private var correctionTimeSum = 0.0

    fun onObservation(obs: FrameObservation) {
        framesProcessed++
        if (firstTimestampMs == null) firstTimestampMs = obs.timestampMs
        lastTimestampMs = obs.timestampMs

        val c = obs.crosshair
        if (c.detected) {
            confidenceSum += c.confidence
            confidenceCount++
            confidenceMin = confidenceMin?.let { min(it, c.confidence) } ?: c.confidence
        }
        val confidentCrosshair = c.detected && c.xNorm != null && c.yNorm != null && c.confidence >= minConfidence
        if (confidentCrosshair) validCrosshairSamples++

        val t = obs.target
        val confidentTarget = t.detected && t.xNorm != null && t.yNorm != null && t.confidence >= minConfidence
        if (confidentTarget) validTargetSamples++

        if (confidentCrosshair) {
            val prev = lastConfidentCrosshair
            if (prev != null && prev.xNorm != null && prev.yNorm != null) {
                val dt = c.timestampMs - prev.timestampMs
                when {
                    dt <= 0 -> invalidTimestampSamples++
                    dt > maxGapMs -> timestampGapCount++
                    else -> onMovementStep(prev, c, dt)
                }
            }
            lastConfidentCrosshair = c
        }

        if (confidentCrosshair && confidentTarget) {
            onAlignedSample(obs.timestampMs, c.xNorm!!, c.yNorm!!, t.xNorm!!, t.yNorm!!)
        }
    }

    private fun onMovementStep(prev: CrosshairDetection, cur: CrosshairDetection, dt: Long) {
        val dx = cur.xNorm!! - prev.xNorm!!
        val dy = cur.yNorm!! - prev.yNorm!!
        val distance = sqrt(dx * dx + dy * dy)
        val speed = distance / (dt / 1000f)
        val direction = DirectionClassifier.classify(dx, dy, directionDeadZoneNorm)
        val pair = MovementPair(prev.frameIndex, cur.frameIndex, dt, dx, dy, distance, speed, direction)

        pairCount++
        distanceSum += distance
        speedSum += speed
        speedSumSq += speed.toDouble() * speed
        peakSpeed = peakSpeed?.let { max(it, speed) } ?: speed
        dxAbsSum += abs(dx); dyAbsSum += abs(dy)
        dxNetSum += dx; dyNetSum += dy
        trackedSegmentMs += dt
        distanceReservoir.offer(distance)
        speedReservoir.offer(speed)

        if (distance > 0f && distance <= microAdjustmentMaxNorm) {
            microCount++
            microMagnitudeSum += distance
            microReservoir.offer(distance)
        }

        val prevPair = lastPair
        if (prevPair != null && prevPair.toFrame == pair.fromFrame) {
            contiguousAdjacentCount++
            val dtSec = pair.dtMs / 1000f
            if (dtSec > 0f) {
                val accel = (pair.speed - prevPair.speed) / dtSec
                accelSum += accel
                accelAbsSum += abs(accel)
                accelSumSq += accel.toDouble() * accel
                accelCount++
                peakAccel = peakAccel?.let { max(it, accel) } ?: accel
                val decelMag = max(0f, -accel)
                peakDecelMagnitude = peakDecelMagnitude?.let { max(it, decelMag) } ?: decelMag
            }
            val dot = prevPair.dx * pair.dx + prevPair.dy * pair.dy
            val magProd = sqrt((prevPair.dx * prevPair.dx + prevPair.dy * prevPair.dy).toDouble() * (pair.dx * pair.dx + pair.dy * pair.dy).toDouble()).toFloat()
            if (magProd > 0f && dot / magProd < 0f) reversalCount++
            if (DirectionClassifier.isCompassDirection(prevPair.direction) &&
                DirectionClassifier.isCompassDirection(pair.direction) &&
                prevPair.direction != pair.direction
            ) directionChangeCount++
        }
        lastPair = pair
    }

    private fun onAlignedSample(timestampMs: Long, cx: Float, cy: Float, tx: Float, ty: Float) {
        val errX = tx - cx
        val errY = ty - cy
        val err = sqrt(errX * errX + errY * errY)
        targetErrorSum += err
        targetErrorSumSq += err.toDouble() * err
        targetErrorCount++
        targetErrorMin = targetErrorMin?.let { min(it, err) } ?: err
        targetErrorMax = targetErrorMax?.let { max(it, err) } ?: err
        targetErrorReservoir.offer(err)

        val isClose = err <= closeToTargetNorm
        val prevWasClose = lastAlignedWasClose
        val prevTs = lastAlignedTimestampMs
        if (prevWasClose != null && prevTs != null) {
            val dt = timestampMs - prevTs
            if (dt in 1..maxGapMs && prevWasClose) withinTargetRegionMs += dt
        }

        when {
            prevWasClose == true && !isClose -> {
                overshootCount++
                leftCloseAtMs = timestampMs
                peakErrorSinceLeaving = err
            }
            prevWasClose == false && !isClose && leftCloseAtMs != null -> {
                if (err > peakErrorSinceLeaving) peakErrorSinceLeaving = err
                if (timestampMs - leftCloseAtMs!! > maxReacquireMs) leftCloseAtMs = null
            }
            prevWasClose == false && isClose && leftCloseAtMs != null -> {
                val elapsed = timestampMs - leftCloseAtMs!!
                if (elapsed <= maxReacquireMs) {
                    correctionCount++
                    correctionMagnitudeSum += peakErrorSinceLeaving
                    correctionTimeSum += elapsed
                }
                leftCloseAtMs = null
            }
        }
        lastAlignedWasClose = isClose
        lastAlignedTimestampMs = timestampMs
    }

    fun finalizeResult(sessionId: String, droppedFrames: Int = 0): AimMetricsResult {
        val trackingDurationMs = if (framesProcessed >= 2) (lastTimestampMs!! - firstTimestampMs!!) else null
        val crosshairRate = if (framesProcessed > 0) validCrosshairSamples.toFloat() / framesProcessed else null
        val targetRate = if (framesProcessed > 0) validTargetSamples.toFloat() / framesProcessed else null

        val quality = DataQuality(
            framesProcessed = framesProcessed,
            validCrosshairSamples = validCrosshairSamples,
            validTargetSamples = validTargetSamples,
            crosshairDetectionRate = crosshairRate,
            targetDetectionRate = targetRate,
            droppedFrames = droppedFrames,
            timestampGapCount = timestampGapCount,
            invalidTimestampSamples = invalidTimestampSamples,
            avgCrosshairConfidence = if (confidenceCount > 0) (confidenceSum / confidenceCount).toFloat() else null,
            minCrosshairConfidence = confidenceMin,
            trackingDurationMs = trackingDurationMs
        )

        if (framesProcessed == 0) return empty(sessionId, AimMetricsStatus.NO_DATA, quality)
        if (framesProcessed < minFramesForAnalysis || pairCount < minMovementPairsForAnalysis) {
            return empty(sessionId, AimMetricsStatus.INSUFFICIENT_DATA, quality)
        }

        // Target detection is not a Phase 5 requirement. Once enough frames were processed and enough valid
        // crosshair movement pairs exist (gates above), the crosshair metrics are fully computed: ANALYZED.
        // Target-based fields are populated only from real aligned target observations and stay null otherwise.
        val targetAvailable = targetErrorCount > 0
        val status = AimMetricsStatus.ANALYZED
        val trackingDurationSeconds = trackingDurationMs?.let { it / 1000f }

        val velocityVariance = if (pairCount >= 2) {
            val mean = speedSum / pairCount
            (speedSumSq / pairCount - mean * mean).toFloat().coerceAtLeast(0f)
        } else null
        val accelerationVariance = if (accelCount >= 2) {
            val mean = accelSum / accelCount
            (accelSumSq / accelCount - mean * mean).toFloat().coerceAtLeast(0f)
        } else null

        return AimMetricsResult(
            sessionId = sessionId,
            status = status,
            quality = quality,
            totalMovementDistance = distanceSum.toFloat(),
            averageMovementDistance = (distanceSum / pairCount).toFloat(),
            medianMovementDistance = distanceReservoir.median(),
            horizontalMovementTotal = dxAbsSum.toFloat(),
            verticalMovementTotal = dyAbsSum.toFloat(),
            horizontalMovementNet = dxNetSum.toFloat(),
            verticalMovementNet = dyNetSum.toFloat(),
            averageSpeed = (speedSum / pairCount).toFloat(),
            medianSpeed = speedReservoir.median(),
            peakSpeed = peakSpeed,
            directionChangeCount = directionChangeCount,
            directionChangeRate = if (trackingDurationSeconds != null && trackingDurationSeconds > 0f) directionChangeCount / trackingDurationSeconds else null,
            averageAcceleration = if (accelCount > 0) (accelAbsSum / accelCount).toFloat() else null,
            peakAcceleration = peakAccel,
            peakDeceleration = peakDecelMagnitude,
            velocityVariance = velocityVariance,
            accelerationVariance = accelerationVariance,
            directionReversalRate = if (contiguousAdjacentCount > 0) reversalCount.toFloat() / contiguousAdjacentCount else null,
            microAdjustmentCount = microCount,
            microAdjustmentFrequency = if (trackingDurationSeconds != null && trackingDurationSeconds > 0f) microCount / trackingDurationSeconds else null,
            averageMicroAdjustmentMagnitude = if (microCount > 0) (microMagnitudeSum / microCount).toFloat() else null,
            medianMicroAdjustmentMagnitude = microReservoir.median(),
            overshootCount = if (targetAvailable) overshootCount else null,
            correctionCount = if (targetAvailable) correctionCount else null,
            averageCorrectionMagnitude = if (targetAvailable && correctionCount > 0) (correctionMagnitudeSum / correctionCount).toFloat() else null,
            averageCorrectionTimeMs = if (targetAvailable && correctionCount > 0) (correctionTimeSum / correctionCount).toFloat() else null,
            averageTargetError = if (targetAvailable) (targetErrorSum / targetErrorCount).toFloat() else null,
            medianTargetError = if (targetAvailable) targetErrorReservoir.median() else null,
            minimumTargetError = if (targetAvailable) targetErrorMin else null,
            maximumTargetError = if (targetAvailable) targetErrorMax else null,
            targetErrorVariance = if (targetErrorCount >= 2) {
                val mean = targetErrorSum / targetErrorCount
                (targetErrorSumSq / targetErrorCount - mean * mean).toFloat().coerceAtLeast(0f)
            } else null,
            timeWithinTargetRegionMs = if (targetAvailable) withinTargetRegionMs else null,
            trackingContinuity = trackingDurationMs?.takeIf { it > 0 }?.let { trackedSegmentMs.toFloat() / it },
            crosshairObservationCoverage = crosshairRate,
            computedAtMs = clock(),
            analysisDurationMs = clock() - startedAt
        )
    }

    private fun empty(sessionId: String, status: AimMetricsStatus, quality: DataQuality) = AimMetricsResult(
        sessionId = sessionId, status = status, quality = quality,
        totalMovementDistance = null, averageMovementDistance = null, medianMovementDistance = null,
        horizontalMovementTotal = null, verticalMovementTotal = null, horizontalMovementNet = null, verticalMovementNet = null,
        averageSpeed = null, medianSpeed = null, peakSpeed = null,
        directionChangeCount = null, directionChangeRate = null,
        averageAcceleration = null, peakAcceleration = null, peakDeceleration = null,
        velocityVariance = null, accelerationVariance = null, directionReversalRate = null,
        microAdjustmentCount = null, microAdjustmentFrequency = null, averageMicroAdjustmentMagnitude = null, medianMicroAdjustmentMagnitude = null,
        overshootCount = null, correctionCount = null, averageCorrectionMagnitude = null, averageCorrectionTimeMs = null,
        averageTargetError = null, medianTargetError = null, minimumTargetError = null, maximumTargetError = null,
        targetErrorVariance = null, timeWithinTargetRegionMs = null,
        trackingContinuity = null, crosshairObservationCoverage = quality.crosshairDetectionRate,
        computedAtMs = clock(), analysisDurationMs = clock() - startedAt
    )

    private fun min(a: Float, b: Float) = if (a < b) a else b
}
