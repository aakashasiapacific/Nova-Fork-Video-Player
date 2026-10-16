package com.aakash.novafork.core.tmdb

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Collections

@OptIn(ExperimentalCoroutinesApi::class)
class TmdbRateLimiterTest {
    @Test
    fun requestWaitsForTheOldestToLeaveTheWindow() = runTest {
        val limiter = TmdbRateLimiter(maxRequests = 3, windowMs = 1_000, clock = { testScheduler.currentTime })
        val starts = ArrayList<Long>()
        repeat(7) {
            limiter.acquire()
            starts += testScheduler.currentTime
        }
        assertEquals(listOf(0L, 0L, 0L, 1_000L, 1_000L, 1_000L, 2_000L), starts)
    }

    @Test
    fun spacedRequestsNeverWait() = runTest {
        val limiter = TmdbRateLimiter(maxRequests = 2, windowMs = 1_000, clock = { testScheduler.currentTime })
        val starts = ArrayList<Long>()
        repeat(5) { index ->
            testScheduler.advanceTimeBy(600)
            limiter.acquire()
            starts += testScheduler.currentTime - 600L * (index + 1)
        }
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L), starts)
    }

    @Test
    fun concurrentCallersShareTheLimit() = runTest {
        val limiter = TmdbRateLimiter(maxRequests = 2, windowMs = 1_000, clock = { testScheduler.currentTime })
        val starts = Collections.synchronizedList(ArrayList<Long>())
        coroutineScope {
            repeat(5) {
                launch {
                    limiter.acquire()
                    starts += testScheduler.currentTime
                }
            }
        }
        assertEquals(listOf(0L, 0L, 1_000L, 1_000L, 2_000L), starts.sorted())
    }

    @Test
    fun zeroTurnsTheLimitOff() = runTest {
        val limiter = TmdbRateLimiter(maxRequests = 0, windowMs = 1_000, clock = { testScheduler.currentTime })
        repeat(100) { limiter.acquire() }
        assertEquals(0L, testScheduler.currentTime)
    }
}
