package com.aakash.novafork.core.tmdb

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps requests under [maxRequests] in any [windowMs] (TMDB asks for about 40 per 10 seconds).
 * A request that would go over waits until the oldest one in the window has left it; waiting
 * callers are served in order. Cancellable while waiting.
 */
internal class TmdbRateLimiter(
    private val maxRequests: Int,
    private val windowMs: Long,
    /** Monotonic milliseconds. */
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val mutex = Mutex()

    /** Start times of the requests in the current window, oldest first. */
    private val sent = ArrayDeque<Long>()

    suspend fun acquire() {
        if (maxRequests <= 0 || windowMs <= 0) return
        mutex.withLock {
            while (true) {
                val now = clock()
                while (sent.isNotEmpty() && now - sent.first() >= windowMs) sent.removeFirst()
                if (sent.size < maxRequests) {
                    sent.addLast(now)
                    return
                }
                delay(windowMs - (now - sent.first()))
            }
        }
    }
}
