package com.aimforge.app.domain.metrics

import kotlin.math.atan2

/**
 * Classifies a movement step into one of 8 compass directions, or STATIONARY when the distance is
 * inside [deadZoneNorm] (normalized units). The dead-zone exists so detector jitter on an otherwise
 * still crosshair does not read as constant direction changes.
 */
object DirectionClassifier {
    fun classify(dx: Float, dy: Float, deadZoneNorm: Float): MovementDirection {
        if (dx.isNaN() || dy.isNaN()) return MovementDirection.UNKNOWN
        val distance = kotlin.math.sqrt(dx * dx + dy * dy)
        if (distance <= deadZoneNorm) return MovementDirection.STATIONARY

        // Screen space: +x = right, +y = down. Split the circle into 8 equal 45-degree sectors.
        val angleDeg = (Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() + 360f) % 360f
        return when {
            angleDeg < 22.5f || angleDeg >= 337.5f -> MovementDirection.RIGHT
            angleDeg < 67.5f -> MovementDirection.DOWN_RIGHT
            angleDeg < 112.5f -> MovementDirection.DOWN
            angleDeg < 157.5f -> MovementDirection.DOWN_LEFT
            angleDeg < 202.5f -> MovementDirection.LEFT
            angleDeg < 247.5f -> MovementDirection.UP_LEFT
            angleDeg < 292.5f -> MovementDirection.UP
            else -> MovementDirection.UP_RIGHT
        }
    }

    /** True for a real compass direction (excludes STATIONARY/UNKNOWN), used to gate direction-change counting. */
    fun isCompassDirection(d: MovementDirection): Boolean = d != MovementDirection.STATIONARY && d != MovementDirection.UNKNOWN
}
