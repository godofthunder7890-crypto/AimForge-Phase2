package com.aimforge.app.domain.cv

import org.junit.Assert.assertEquals
import org.junit.Test

class CoordinatesTest {
    @Test fun normalizesToZeroToOne() {
        assertEquals(0.5f, Coordinates.normX(50, 100), 0.001f)
        assertEquals(0f, Coordinates.normY(0, 200), 0.001f)
        assertEquals(1f, Coordinates.normX(100, 100), 0.001f)
    }

    @Test fun clampsOutOfRangePixels() {
        assertEquals(1f, Coordinates.normX(150, 100), 0.001f)
        assertEquals(0f, Coordinates.normY(-10, 100), 0.001f)
    }

    @Test fun zeroSizeIsZeroNotCrash() {
        assertEquals(0f, Coordinates.normX(10, 0), 0.001f)
    }
}