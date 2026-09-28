package com.aimforge.app.domain.metrics

/** Storage seam for Phase 5 results, mirroring [com.aimforge.app.domain.cv.CvAnalysisStore]'s pattern. */
interface AimMetricsStore {
    suspend fun save(result: AimMetricsResult)
}
