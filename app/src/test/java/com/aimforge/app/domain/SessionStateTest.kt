package com.aimforge.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStateTest {
    @Test fun draftToReadyToCapturePendingIsLegal() {
        assertTrue(SessionState.DRAFT.canGoTo(SessionState.READY))
        assertTrue(SessionState.READY.canGoTo(SessionState.CAPTURE_PENDING))
    }

    @Test fun cannotSkipStates() {
        assertFalse(SessionState.DRAFT.canGoTo(SessionState.CAPTURE_PENDING))
        assertFalse(SessionState.READY.canGoTo(SessionState.ANALYZED))
        assertFalse(SessionState.CAPTURE_PENDING.canGoTo(SessionState.ANALYZED))
        assertFalse(SessionState.CAPTURE_PENDING.canGoTo(SessionState.CAPTURE_COMPLETE))
    }

    @Test fun terminalStatesHaveNoExit() {
        listOf(SessionState.ANALYZED, SessionState.FAILED, SessionState.CANCELLED).forEach {
            assertTrue(it.isTerminal)
            assertTrue(it.allowedNext.isEmpty())
        }
    }

    @Test fun onlyOpenStatesBlockNewSessions() {
        val open = SessionState.entries.filter { it.isOpenState }.toSet()
        assertEquals(setOf(SessionState.READY, SessionState.CAPTURE_PENDING, SessionState.CAPTURING), open)
    }
}
