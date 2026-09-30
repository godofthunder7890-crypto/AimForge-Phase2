package com.aimforge.app.domain.metrics

import org.junit.Assert.assertEquals
import org.junit.Test

class DirectionClassifierTest {
    private val dz = 0.01f

    @Test fun tinyMovementInsideDeadZoneIsStationary() {
        assertEquals(MovementDirection.STATIONARY, DirectionClassifier.classify(0.001f, 0.001f, dz))
    }

    @Test fun rightIsZeroDegrees() {
        assertEquals(MovementDirection.RIGHT, DirectionClassifier.classify(0.1f, 0f, dz))
    }

    @Test fun downIsPositiveY() {
        // screen space: +y is down
        assertEquals(MovementDirection.DOWN, DirectionClassifier.classify(0f, 0.1f, dz))
    }

    @Test fun leftIsNegativeX() {
        assertEquals(MovementDirection.LEFT, DirectionClassifier.classify(-0.1f, 0f, dz))
    }

    @Test fun upIsNegativeY() {
        assertEquals(MovementDirection.UP, DirectionClassifier.classify(0f, -0.1f, dz))
    }

    @Test fun diagonalsClassifyCorrectly() {
        assertEquals(MovementDirection.DOWN_RIGHT, DirectionClassifier.classify(0.1f, 0.1f, dz))
        assertEquals(MovementDirection.DOWN_LEFT, DirectionClassifier.classify(-0.1f, 0.1f, dz))
        assertEquals(MovementDirection.UP_LEFT, DirectionClassifier.classify(-0.1f, -0.1f, dz))
        assertEquals(MovementDirection.UP_RIGHT, DirectionClassifier.classify(0.1f, -0.1f, dz))
    }

    @Test fun deadZoneBoundaryIsExclusiveOfStationary() {
        // distance exactly at the dead zone counts as stationary (<=), just above does not
        assertEquals(MovementDirection.STATIONARY, DirectionClassifier.classify(0.01f, 0f, 0.01f))
        assertEquals(MovementDirection.RIGHT, DirectionClassifier.classify(0.0101f, 0f, 0.01f))
    }

    @Test fun nanIsUnknown() {
        assertEquals(MovementDirection.UNKNOWN, DirectionClassifier.classify(Float.NaN, 0.1f, dz))
    }

    @Test fun compassCheck() {
        assert(DirectionClassifier.isCompassDirection(MovementDirection.LEFT))
        assert(!DirectionClassifier.isCompassDirection(MovementDirection.STATIONARY))
        assert(!DirectionClassifier.isCompassDirection(MovementDirection.UNKNOWN))
    }
}
