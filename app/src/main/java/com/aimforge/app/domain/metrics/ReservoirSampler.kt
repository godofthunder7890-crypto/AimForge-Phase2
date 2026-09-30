package com.aimforge.app.domain.metrics

import kotlin.random.Random

/**
 * Algorithm R reservoir sampling: keeps an unbiased random sample of up to [capacity] values seen
 * from a stream of unknown/unbounded length, in O(capacity) memory. Used for median-style statistics
 * on very long sessions where storing every value is not allowed: a fixed-size sample drawn uniformly
 * from the WHOLE stream represents the whole session, unlike a FIFO window which only ever represents
 * its most recent tail. When the stream is shorter than [capacity] the sample is exact (every value
 * seen is kept), so short/typical sessions get an exact median, not an approximation.
 */
class ReservoirSampler(private val capacity: Int, private val random: Random = Random.Default) {
    private val items = ArrayList<Float>(minOf(capacity, 64))
    private var seen = 0L

    fun offer(value: Float) {
        seen++
        if (items.size < capacity) {
            items.add(value)
        } else {
            val j = random.nextLong(seen)
            if (j < capacity) items[j.toInt()] = value
        }
    }

    val sampleSize: Int get() = items.size
    val totalSeen: Long get() = seen

    fun median(): Float? = Stats.median(items)
}
