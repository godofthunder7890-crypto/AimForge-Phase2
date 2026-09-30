package com.aimforge.app.domain.cv

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Crosshair detector v2: "cross-structure-contrast-v2".
 *
 * Written after two real BGMI sessions produced 0% detection with v1 ([HeuristicCrosshairDetector]).
 * The most likely causes of that failure, in order of how systematic the symptom was:
 *
 * 1. v1 sampled luma (0.299R+0.587G+0.114B) from a full-frame downsample. A saturated red crosshair
 *    (very common in BGMI: red dot sight, red reticle) has luma around 76/255 — not "bright" at all
 *    under that formula, so a brightness threshold built around luma structurally cannot see it.
 * 2. v1 downsampled the ENTIRE frame to ~160px wide before looking at anything. A reticle only a few
 *    pixels wide on a 1000+ px-wide screen is destroyed by that downsample before detection even runs.
 * 3. v1 used one global ROI mean as its threshold and one global weighted centroid across every bright
 *    pixel in the ROI. A single large bright region anywhere in the ROI (sky, a wall, a flare) both
 *    raises the effective threshold and drags the centroid away from the real reticle, and v1's own
 *    "reject if the bright box is large" rule then throws the whole frame out — a big bright region
 *    can make every single frame reject, matching the observed 0% exactly.
 *
 * This version fixes all three: it is fed a central CROP sampled at max(R,G,B) (see
 * [GraySampler.buildCentralCrop]) instead of luma, so it survives thin colored structures; it uses a
 * LOCAL (per-cell) contrast threshold instead of one global mean, so a big uniformly bright region
 * fails to trigger anywhere (its local contrast is near zero); and it finds every separate bright
 * component (multi-candidate, connected components) and scores each independently instead of
 * centroiding everything together, so one candidate being bad does not sink the whole frame.
 */
class LocalContrastCrosshairDetector(
    private val localCellPx: Int = 12,
    private val minConfidence: Float = 0.22f,
    private val maxCandidateAreaFraction: Float = 0.30f
) : CrosshairVisionDetector {
    override val version: String = "cross-structure-contrast-v2"

    private data class Blob(val pixels: MutableList<Int>, var bx0: Int, var by0: Int, var bx1: Int, var by1: Int)

    override fun detect(frame: Frame): CrosshairDetection {
        val w = frame.width
        val h = frame.height
        fun notDetected(reason: String, candidates: Int) = CrosshairDetection(
            frame.frameIndex, frame.timestampMs, detected = false, confidence = 0f, method = version,
            candidateCount = candidates, rejectionReason = reason
        )
        if (w <= 0 || h <= 0 || frame.gray.size < w * h) return notDetected("invalid frame", 0)

        // 1) Local background: coarse-cell average, used as a per-pixel adaptive threshold baseline.
        val cellsX = max(1, w / localCellPx)
        val cellsY = max(1, h / localCellPx)
        val cellSum = IntArray(cellsX * cellsY)
        val cellCount = IntArray(cellsX * cellsY)
        for (y in 0 until h) {
            val cy = (y * cellsY / h).coerceIn(0, cellsY - 1)
            for (x in 0 until w) {
                val cx = (x * cellsX / w).coerceIn(0, cellsX - 1)
                val idx = cy * cellsX + cx
                cellSum[idx] += frame.gray[y * w + x].toInt() and 0xFF
                cellCount[idx]++
            }
        }
        val cellMean = FloatArray(cellsX * cellsY) { i -> if (cellCount[i] > 0) cellSum[i].toFloat() / cellCount[i] else 0f }

        // 2) Salient-pixel mask: a pixel clears its OWN cell's local threshold, not a single global one.
        val salient = BooleanArray(w * h)
        for (y in 0 until h) {
            val cy = (y * cellsY / h).coerceIn(0, cellsY - 1)
            for (x in 0 until w) {
                val cx = (x * cellsX / w).coerceIn(0, cellsX - 1)
                val local = cellMean[cy * cellsX + cx]
                val threshold = (local + max(14f, (255f - local) * 0.22f)).coerceAtMost(250f)
                val v = frame.gray[y * w + x].toInt() and 0xFF
                if (v >= threshold) salient[y * w + x] = true
            }
        }

        // 3) Connected components over the salient mask (4-connectivity, iterative flood fill; bounded by w*h).
        val visited = BooleanArray(w * h)
        val blobs = ArrayList<Blob>()
        val stack = IntArray(w * h)
        for (start in 0 until w * h) {
            if (!salient[start] || visited[start]) continue
            var sp = 0
            stack[sp++] = start
            visited[start] = true
            val blob = Blob(ArrayList(), w, h, -1, -1)
            while (sp > 0) {
                val p = stack[--sp]
                val px = p % w
                val py = p / w
                blob.pixels.add(p)
                if (px < blob.bx0) blob.bx0 = px
                if (px > blob.bx1) blob.bx1 = px
                if (py < blob.by0) blob.by0 = py
                if (py > blob.by1) blob.by1 = py
                if (px > 0) { val q = p - 1; if (salient[q] && !visited[q]) { visited[q] = true; stack[sp++] = q } }
                if (px < w - 1) { val q = p + 1; if (salient[q] && !visited[q]) { visited[q] = true; stack[sp++] = q } }
                if (py > 0) { val q = p - w; if (salient[q] && !visited[q]) { visited[q] = true; stack[sp++] = q } }
                if (py < h - 1) { val q = p + w; if (salient[q] && !visited[q]) { visited[q] = true; stack[sp++] = q } }
            }
            blobs.add(blob)
        }
        if (blobs.isEmpty()) return notDetected("no salient pixels", 0)

        val cropArea = (w * h).toFloat()
        val centerX = w / 2f
        val centerY = h / 2f
        val maxCenterDist = hypot(centerX.toDouble(), centerY.toDouble()).toFloat()

        var best: CrosshairDetection? = null
        var bestScore = -1f
        for (blob in blobs) {
            val boxW = blob.bx1 - blob.bx0 + 1
            val boxH = blob.by1 - blob.by0 + 1
            val boxArea = (boxW * boxH).toFloat()
            if (boxArea / cropArea > maxCandidateAreaFraction) continue // still too big to plausibly be a reticle

            var wsum = 0.0; var wx = 0.0; var wy = 0.0; var contrastSum = 0.0
            for (p in blob.pixels) {
                val px = p % w
                val py = p / w
                val cx = (px * cellsX / w).coerceIn(0, cellsX - 1)
                val cy = (py * cellsY / h).coerceIn(0, cellsY - 1)
                val local = cellMean[cy * cellsX + cx]
                val v = (frame.gray[p].toInt() and 0xFF).toFloat()
                val weight = (v - local).coerceAtLeast(1f).toDouble()
                wsum += weight; wx += px * weight; wy += py * weight
                contrastSum += (v - local).toDouble()
            }
            if (wsum <= 0.0) continue
            val cxCentroid = wx / wsum
            val cyCentroid = wy / wsum

            val contrast = ((contrastSum / blob.pixels.size) / 255.0).toFloat().coerceIn(0f, 1f)
            val compactness = (1f - boxArea / cropArea).coerceIn(0f, 1f)
            val dist = hypot(cxCentroid - centerX, cyCentroid - centerY).toFloat()
            val centeredness = if (maxCenterDist <= 0f) 1f else (1f - dist / maxCenterDist).coerceIn(0f, 1f)
            val fillRatio = blob.pixels.size / boxArea
            val aspect = max(boxW, boxH).toFloat() / max(1, min(boxW, boxH))
            val shapeBonus = when {
                fillRatio < 0.55f -> 0.15f            // hollow/thin: consistent with a ring or thin cross
                aspect in 1.3f..8f -> 0.10f            // elongated: consistent with a single crosshair arm
                else -> 0f
            }
            val score = (0.35f * contrast + 0.30f * compactness + 0.20f * centeredness + shapeBonus).coerceIn(0f, 1f)

            if (score > bestScore) {
                bestScore = score
                best = CrosshairDetection(
                    frameIndex = frame.frameIndex,
                    timestampMs = frame.timestampMs,
                    detected = true,
                    xNorm = frame.toSourceNormX(cxCentroid),
                    yNorm = frame.toSourceNormY(cyCentroid),
                    boxWidthNorm = frame.gridLengthToSourceNormX(boxW),
                    boxHeightNorm = frame.gridLengthToSourceNormY(boxH),
                    confidence = score,
                    method = version,
                    candidateCount = blobs.size
                )
            }
        }

        val result = best
        return if (result == null || bestScore < minConfidence) {
            notDetected(if (result == null) "no candidate survived size filter" else "best score below threshold", blobs.size)
        } else result
    }
}
