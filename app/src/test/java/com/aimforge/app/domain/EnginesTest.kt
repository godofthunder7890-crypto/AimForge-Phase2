package com.aimforge.app.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnginesTest {
    @Test fun placeholdersReturnNotImplementedAndNeverValues() = runBlocking {
        assertTrue(NotImplementedCaptureEngine().availability() is Availability.NotAvailable)
        assertEquals(CaptureOutcome.NotImplemented, NotImplementedCaptureEngine().start("x"))
        assertEquals(CaptureOutcome.NotImplemented, NotImplementedCaptureEngine().stop("x"))
        assertEquals(AnalysisOutcome.NotImplemented, NotImplementedAnalysisEngine().analyze("x", "y"))
        assertEquals(RecommendationOutcome.NotImplemented, NotImplementedRecommendationEngine().recommend(ScopeType.X3))
    }

    @Test fun captureEngineMessageMatchesUiText() {
        val a = NotImplementedCaptureEngine().availability() as Availability.NotAvailable
        assertEquals("Screen capture engine is not available yet.", a.reason)
    }

    @Test fun diagnosticPlanIsADefinitionOfRealTestModes() {
        assertTrue(DiagnosticPlan.steps.isNotEmpty())
        assertTrue(DiagnosticPlan.steps.none { it.mode == TestMode.FULL_DIAGNOSTIC })
    }

    @Test fun sensitivitySummaryShowsOnlyEnteredValues() {
        assertEquals("Not entered", SessionFormat.sensitivity(null, null, null, null))
        assertEquals("Camera 120  ADS 115  Gyro 300", SessionFormat.sensitivity(120, 115, 300, null))
    }
}
