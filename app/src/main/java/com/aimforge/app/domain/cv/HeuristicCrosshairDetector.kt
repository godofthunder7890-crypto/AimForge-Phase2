package com.aimforge.app.domain.cv

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * First-pass crosshair detector, v1. Heuristic, not ML: it looks for a small, bright, roughly
 * centered, high-contrast cluster within a central region of the frame (BGMI's reticle is drawn on
 * top of gameplay near screen center and is usually brighter/more saturated than its surroundings).
 *
 * This will miss some crosshairs (custom colors, cluttered backgrounds) and can be fooled by other
 * bright central objects (a muzzle flash, a bright sky patch). It never reports a position it is not
 * evidenced for: with no sufficiently confident cluster it returns NOT_DETECTED rather than a guess.
 * [version] is stored with every result so a future, better detector is distinguishable in history.
 */
class HeuristicCrosshairDetector(
    private val roiFraction: Float = 0.5f,
    private val minConfidence: Float = 0.25f
) : CrosshairVisionDetector {
    override val version: String = "center-bright-cluster-v1"

    override fun detect(frame: Frame): CrosshairDetection {
        val w = frame.width
        val h = frame.height
        val notDetected = CrosshairDetection.notDetected(frame.frameIndex, frame.timestampMs, version)
        if (w <= 0 || h <= 0 || frame.gray.size < w * h) return notDetected

        val roiW = max(2, (w * roiFraction).roundToInt())
        val roiH = max(2, (h * roiFraction).roundToInt())
        val roiX0 = (w - roiW) / 2
        val roiY0 = (h - roiH) / 2

        var sum = 0L
        var maxV = 0
        for (y in roiY0 until roiY0 + roiH) {
            val row = y * w
            for (x in roiX0 until roiX0 + roiW) {
                val v = frame.gray[row + x].toInt() and 0xFF
                sum += v
                if (v > maxV) maxV = v
            }
        }
        val roiCount = roiW * roiH
        val mean = sum.toFloat() / roiCount
        val threshold = (mean + max(15f, (255f - mean) * 0.25f)).coerceAtMost(250f)

        var bx0 = Int.MAX_VALUE; var by0 = Int.MAX_VALUE; var bx1 = -1; var by1 = -1
        var weightSum = 0.0; var wx = 0.0; var wy = 0.0

        for (y in roiY0 until roiY0 + roiH) {
            val row = y * w
            for (x in roiX0 until roiX0 + roiW) {
                val v = frame.gray[row + x].toInt() and 0xFF
                if (v >= threshold) {
                    val weight = (v - threshold + 1).toDouble()
                    weightSum += weight
                    wx += x * weight
                    wy += y * weight
                    if (x < bx0) bx0 = x
                    if (x > bx1) bx1 = x
                    if (y < by0) by0 = y
                    if (y > by1) by1 = y
                }
            }
        }
        if (weightSum <= 0.0 || bx1 < bx0) return notDetected

        val boxW = bx1 - bx0 + 1
        val boxH = by1 - by0 + 1
        // A bright region covering most of the ROI is background (sky, a wall), not a small reticle.
        if (boxW > roiW * 0.5f || boxH > roiH * 0.5f) return notDetected

        val cx = wx / weightSum
        val cy = wy / weightSum

        val contrast = ((maxV - mean) / 255f).coerceIn(0f, 1f)
        val compactness = (1f - (boxW.toFloat() * boxH / roiCount)).coerceIn(0f, 1f)
        val maxCenterDist = hypot(roiW / 2.0, roiH / 2.0)
        val centerDist = hypot(cx - (roiX0 + roiW / 2.0), cy - (roiY0 + roiH / 2.0))
        val centeredness = if (maxCenterDist <= 0.0) 1f else (1f - (centerDist / maxCenterDist)).toFloat().coerceIn(0f, 1f)
        val confidence = ((contrast + compactness + centeredness) / 3f).coerceIn(0f, 1f)
        if (confidence < minConfidence) return notDetected

        return CrosshairDetection(
            frameIndex = frame.frameIndex,
            timestampMs = frame.timestampMs,
            detected = true,
            xNorm = Coordinates.normX(cx, w),
            yNorm = Coordinates.normY(cy, h),
            boxWidthNorm = boxW.toFloat() / w,
            boxHeightNorm = boxH.toFloat() / h,
            confidence = confidence,
            method = version
        )
    }
}