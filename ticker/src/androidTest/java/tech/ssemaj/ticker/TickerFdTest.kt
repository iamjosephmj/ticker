package tech.ssemaj.ticker

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class TickerFdTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private data class Tick(
        val boundaryMillis: Long,
        val arrivalMillis: Long,
        val onMainThread: Boolean,
    )

    @Test
    fun ticksArriveOnMainThreadOnSecondBoundaries() {
        val ticks = ConcurrentLinkedQueue<Tick>()
        val latch = CountDownLatch(4)
        lateinit var ticker: TickerFd

        instrumentation.runOnMainSync {
            ticker = TickerFd { boundary ->
                ticks.add(
                    Tick(
                        boundaryMillis = boundary,
                        arrivalMillis = System.currentTimeMillis(),
                        onMainThread = Looper.myLooper() == Looper.getMainLooper(),
                    )
                )
                latch.countDown()
            }
            assertTrue("native ticker failed to start", ticker.start())
        }

        assertTrue("expected 4 ticks within 6s", latch.await(6, TimeUnit.SECONDS))
        instrumentation.runOnMainSync { ticker.stop() }

        val collected = ticks.toList().take(4)
        collected.forEach { tick ->
            assertTrue("tick delivered off the main thread", tick.onMainThread)
            val offsetMillis = tick.arrivalMillis - tick.boundaryMillis
            assertTrue(
                "tick arrived ${offsetMillis}ms after the second boundary",
                offsetMillis in 0 until 50
            )
        }
        collected.zipWithNext { a, b ->
            assertEquals("ticks must advance by exactly one period", a.boundaryMillis + 1_000, b.boundaryMillis)
        }
    }

    @Test
    fun stopHaltsDelivery() {
        val ticks = ConcurrentLinkedQueue<Long>()
        val latch = CountDownLatch(1)
        lateinit var ticker: TickerFd

        instrumentation.runOnMainSync {
            ticker = TickerFd { sec ->
                ticks.add(sec)
                latch.countDown()
            }
            assertTrue(ticker.start())
        }
        assertTrue(latch.await(3, TimeUnit.SECONDS))
        instrumentation.runOnMainSync { ticker.stop() }

        val countAtStop = ticks.size
        Thread.sleep(2_500)
        assertEquals("ticks kept arriving after stop()", countAtStop, ticks.size)
    }

    @Test
    fun flowCollectableFromBackgroundDispatcherAndReleasesTimer() = runBlocking {
        // Collect from a background dispatcher; flowOn must pin the timer to
        // the main looper regardless.
        val first = withContext(Dispatchers.Default) {
            ticker().take(2).toList()
        }
        assertEquals(2, first.size)
        assertEquals(first[0] + 1_000, first[1])

        // Cancellation released the timer: a fresh collect must work again.
        val again = withContext(Dispatchers.IO) {
            ticker().first()
        }
        assertTrue(again >= first.last())
    }

    @Test
    fun dispatcherOverloadRunsTransformOffMain() = runBlocking {
        // Collected from the main dispatcher, but the transform must run on
        // IO — that's the whole contract of ticker(dispatcher) { }.
        val transformWasOnMain = withContext(Dispatchers.Main) {
            ticker(Dispatchers.IO) { Looper.myLooper() == Looper.getMainLooper() }.first()
        }
        assertTrue("transform ran on the main thread", !transformWasOnMain)
    }
}
