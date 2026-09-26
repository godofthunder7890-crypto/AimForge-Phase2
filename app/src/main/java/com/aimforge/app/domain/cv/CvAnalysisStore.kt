package com.aimforge.app.domain.cv

/** Storage seam for CV results, mirroring [com.aimforge.app.domain.SessionStore]'s pattern. */
interface CvAnalysisStore {
    suspend fun save(result: CvSessionAnalysis)
}