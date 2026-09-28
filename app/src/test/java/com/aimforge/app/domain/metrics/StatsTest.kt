package com.aimforge.app.domain.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatsTest {
    @Test fun emptyIsNullNotZero() {
        assertNull(Stats.mean(emptyList()))
        assertNull(Stats.median(emptyList()))
        assertNull(Stats.variance(listOf(1f)))
    }

    @Test fun meanAndMedianOddAndEven() {
        assertEquals(2f, Stats.mean(listOf(1f, 2f, 3f))!!, 0.001f)
        assertEquals(2f, Stats.median(listOf(3f, 1f, 2f))!!, 0.001f)
        assertEquals(2.5f, Stats.median(listOf(1f, 2f, 3f, 4f))!!, 0.001f)
    }

    @Test fun varianceOfConstantIsZero() {
        assertEquals(0f, Stats.variance(listOf(5f, 5f, 5f))!!, 0.0001f)
    }
}
