package com.aimforge.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionClockTest {
    @Test fun notStartedIsZero() = assertEquals(0L, SessionClock.elapsedMs(null, null, 0, null, 99_999))

    @Test fun runningUsesNow() = assertEquals(5_000L, SessionClock.elapsedMs(1_000, null, 0, null, 6_000))

    @Test fun pausedIsFrozenAtPauseTime() {
        assertEquals(3_000L, SessionClock.elapsedMs(1_000, 4_000, 0, null, 50_000))
    }

    @Test fun completedPausesAreSubtracted() {
        // started 0, paused 10s in total, now 30s -> 20s of real session time
        assertEquals(20_000L, SessionClock.elapsedMs(0, null, 10_000, null, 30_000))
    }

    @Test fun endedUsesEndTimeNotNow() {
        assertEquals(8_000L, SessionClock.elapsedMs(0, null, 2_000, 10_000, 999_999))
    }

    @Test fun neverNegative() = assertEquals(0L, SessionClock.elapsedMs(10_000, null, 50_000, null, 20_000))

    @Test fun durationFormat() {
        assertEquals("00:00", SessionFormat.duration(0))
        assertEquals("01:05", SessionFormat.duration(65_000))
        assertEquals("1:01:01", SessionFormat.duration(3_661_000))
    }
}
