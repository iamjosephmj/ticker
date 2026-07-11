package tech.ssemaj.ticker

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Flow-level contract of the public ticker API. */
@RunWith(AndroidJUnit4::class)
class TickerFlowSemanticsTest {

    @Test
    fun conflationSkipsToLatestForSlowCollectors() = runBlocking {
        val received = mutableListOf<Long>()
        withTimeoutOrNull(8_000) {
            ticker(Dispatchers.Default) { it }.collect {
                received += it
                delay(2_500) // deliberately slower than the tick rate
            }
        }
        // ~8s window at 1Hz: a buffering flow would deliver ~7 elements;
        // conflation must deliver far fewer and must skip seconds.
        assertTrue("expected a slow collector to miss ticks, got $received", received.size <= 4)
        assertTrue(
            "conflation should skip seconds for a slow collector: $received",
            received.zipWithNext().any { (a, b) -> b - a >= 2_000 }
        )
        // But never deliver stale or out-of-order seconds.
        assertTrue("out-of-order ticks: $received", received.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun concurrentCollectorsEachGetTheirOwnFullStream() = runBlocking {
        val streams = (1..3).map {
            async(Dispatchers.Default) { ticker(Dispatchers.Default) { it }.take(2).toList() }
        }.map { it.await() }

        streams.forEachIndexed { i, ticks ->
            assertEquals("collector $i missing ticks", 2, ticks.size)
            assertEquals("collector $i skipped a boundary", ticks[0] + 1_000, ticks[1])
        }
        // All collectors observed the same wall clock (within scheduling slack).
        val firsts = streams.map { it.first() }
        assertTrue("collectors disagree wildly: $firsts", firsts.max() - firsts.min() <= 2_000)
    }

    @Test
    fun transformExceptionPropagatesToCollectorAndReleasesTimer() = runBlocking {
        try {
            ticker<Long>(Dispatchers.Default) { throw IllegalArgumentException("boom") }.first()
            fail("expected the transform exception to propagate")
        } catch (expected: IllegalArgumentException) {
            assertEquals("boom", expected.message)
        }
        // The failed collection must have released its timer: collecting again works.
        val tick = ticker(Dispatchers.Default) { it }.first()
        assertTrue(tick > 0)
    }

    @Test
    fun transformInvocationsNeverOverlap() = runBlocking {
        val concurrent = AtomicInteger()
        val maxConcurrent = AtomicInteger()
        ticker(Dispatchers.IO) {
            val now = concurrent.incrementAndGet()
            maxConcurrent.updateAndGet { max -> maxOf(max, now) }
            delay(1_600) // longer than the tick interval
            concurrent.decrementAndGet()
            it
        }.take(3).toList()
        assertEquals("transform invocations overlapped", 1, maxConcurrent.get())
    }

    @Test
    fun customPeriodTicksOnExactSubSecondBoundaries() = runBlocking {
        val ticks = ticker(Dispatchers.Default, period = 250.milliseconds) { it }
            .take(5).toList()
        ticks.zipWithNext { a, b ->
            assertEquals("250ms ticker must advance by exactly 250ms", a + 250, b)
        }
        ticks.forEach { boundary ->
            assertEquals("boundary not aligned to the 250ms grid: $boundary", 0, boundary % 250)
        }
    }

    @Test
    fun multiSecondPeriodTicksOnItsGrid() = runBlocking {
        val ticks = ticker(Dispatchers.Default, period = 2.seconds) { it }
            .take(2).toList()
        assertEquals(ticks[0] + 2_000, ticks[1])
        ticks.forEach { assertEquals(0, it % 2_000) }
    }

    @Test
    fun collectableDirectlyFromTheMainDispatcher() = runBlocking {
        // Main.immediate flowOn + main-thread collection must not deadlock.
        val ticks = withContext(Dispatchers.Main) {
            ticker(Dispatchers.Main.immediate) { it }.take(2).toList()
        }
        assertEquals(ticks[0] + 1_000, ticks[1])
    }
}
