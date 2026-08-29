package com.varisahayak.core.common

/**
 * Injected time source.
 *
 * Incident timestamps, sync backoff, and "is this responder's position stale" all depend
 * on the clock, and all of them need to be testable without sleeping.
 */
interface Clock {
    fun nowEpochMillis(): Long
}

class SystemClock : Clock {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
}
