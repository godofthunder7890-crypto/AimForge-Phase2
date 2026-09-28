package com.aimforge.app.domain.metrics

/** Small shared math helpers. Never invents a value: every function returns null on insufficient input. */
object Stats {
    fun mean(values: List<Float>): Float? = if (values.isEmpty()) null else values.sum() / values.size

    fun median(values: List<Float>): Float? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
    }

    /** Population variance (divides by N). Needs at least 2 samples to be meaningful. */
    fun variance(values: List<Float>): Float? {
        if (values.size < 2) return null
        val m = values.sum() / values.size
        return values.sumOf { ((it - m) * (it - m)).toDouble() }.toFloat() / values.size
    }

    fun max(values: List<Float>): Float? = values.maxOrNull()
    fun min(values: List<Float>): Float? = values.minOrNull()
}
