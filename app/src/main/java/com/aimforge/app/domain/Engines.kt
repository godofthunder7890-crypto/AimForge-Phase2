package com.aimforge.app.domain

/**
 * Pipeline interfaces that Phases 3-6 implement. Phase 2 ships only NOT_IMPLEMENTED placeholders.
 * A placeholder must never return metrics, scores or recommendations.
 */

sealed interface Availability {
    data object Available : Availability
    data class NotAvailable(val reason: String) : Availability
}

// ---- Capture (Phase 3) ----
sealed interface CaptureOutcome {
    data object NotImplemented : CaptureOutcome
    data class Failed(val reason: String) : CaptureOutcome
    data class Completed(val captureId: String) : CaptureOutcome
}

interface CaptureEngine {
    fun availability(): Availability
    suspend fun start(sessionId: String): CaptureOutcome
    suspend fun stop(sessionId: String): CaptureOutcome
}

// ---- Analysis (Phases 4-5) ----
sealed interface AnalysisOutcome {
    data object NotImplemented : AnalysisOutcome
    data class InsufficientData(val reason: String) : AnalysisOutcome
    data class Failed(val reason: String) : AnalysisOutcome

    /** Shape only. Values must come from measured frames. */
    data class Completed(
        val aimScore: Int?,
        val aimErrorPx: Float?,
        val stability: Int?,
        val errorState: AimErrorState?,
        val confidence: Int?
    ) : AnalysisOutcome
}

interface AnalysisEngine {
    fun availability(): Availability
    suspend fun analyze(sessionId: String, captureId: String): AnalysisOutcome
}

// ---- Recommendation (Phase 6) ----
sealed interface RecommendationOutcome {
    data object NotImplemented : RecommendationOutcome
    data class InsufficientData(val reason: String) : RecommendationOutcome

    /** Shape only. One setting, one small change, evidence-backed. */
    data class Recommended(
        val setting: String,
        val change: Int,
        val reason: String,
        val confidence: Int,
        val retestInstruction: String
    ) : RecommendationOutcome
}

interface RecommendationEngine {
    fun availability(): Availability
    suspend fun recommend(scope: ScopeType): RecommendationOutcome
}

// ---- Placeholders: explicit NOT_IMPLEMENTED, no sample values ----
class NotImplementedCaptureEngine : CaptureEngine {
    override fun availability() = Availability.NotAvailable("Screen capture engine is not available yet.")
    override suspend fun start(sessionId: String) = CaptureOutcome.NotImplemented
    override suspend fun stop(sessionId: String) = CaptureOutcome.NotImplemented
}

class NotImplementedAnalysisEngine : AnalysisEngine {
    override fun availability() = Availability.NotAvailable("Analysis engine is not available yet.")
    override suspend fun analyze(sessionId: String, captureId: String) = AnalysisOutcome.NotImplemented
}

class NotImplementedRecommendationEngine : RecommendationEngine {
    override fun availability() = Availability.NotAvailable("Recommendation engine is not available yet.")
    override suspend fun recommend(scope: ScopeType) = RecommendationOutcome.NotImplemented
}
