package tech.ssemaj.ticker

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Lifecycle edges of the raw callback API. */
@RunWith(AndroidJUnit4::class)
class TickerFdLifecycleTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun startIsIdempotent() {
        lateinit var ticker: TickerFd
        val ticks = AtomicInteger()
        val latch = CountDownLatch(2)
        instrumentation.runOnMainSync {
            ticker = TickerFd { ticks.incrementAndGet(); latch.countDown() }
            assertTrue(ticker.start())
            assertTrue(ticker.start()) // second start: no-op, still true
            assertTrue(ticker.start())
        }
        // One timer, not three: two ticks take ~2s; three timers would triple-fire.
        assertTrue(latch.await(4, TimeUnit.SECONDS))
        instrumentation.runOnMainSync { ticker.stop() }
        Thread.sleep(1_200)
        assertTrue("multiple timers were armed", ticks.get() <= 3)
    }

    @Test
    fun nonPositivePeriodIsRejectedAtConstruction() {
        assertThrows(IllegalArgumentException::class.java) { TickerFd(0) { } }
        assertThrows(IllegalArgumentException::class.java) { TickerFd(-100) { } }
    }

    @Test
    fun stopWithoutStartIsNoOp() {
        instrumentation.runOnMainSync {
            TickerFd { }.stop() // must not throw or crash
        }
    }

    @Test
    fun stopIsIdempotent() {
        instrumentation.runOnMainSync {
            val ticker = TickerFd { }
            assertTrue(ticker.start())
            ticker.stop()
            ticker.stop()
            ticker.stop()
        }
    }

    @Test
    fun restartAfterStopTicksAgain() {
        lateinit var ticker: TickerFd
        var latch = CountDownLatch(1)
        instrumentation.runOnMainSync {
            ticker = TickerFd { latch.countDown() }
            assertTrue(ticker.start())
        }
        assertTrue("no tick before stop", latch.await(3, TimeUnit.SECONDS))

        instrumentation.runOnMainSync { ticker.stop() }
        latch = CountDownLatch(1)
        instrumentation.runOnMainSync { assertTrue(ticker.start()) }
        assertTrue("no tick after restart", latch.await(3, TimeUnit.SECONDS))
        instrumentation.runOnMainSync { ticker.stop() }
    }

    @Test
    fun startOffMainThreadThrows() {
        // Instrumentation runner thread is not the main thread.
        assertThrows(IllegalStateException::class.java) { TickerFd { }.start() }
    }

    @Test
    fun stopOffMainThreadThrows() {
        assertThrows(IllegalStateException::class.java) { TickerFd { }.stop() }
    }

    @Test
    fun multipleInstancesTickIndependently() {
        val first = CountDownLatch(2)
        val second = CountDownLatch(2)
        lateinit var a: TickerFd
        lateinit var b: TickerFd
        instrumentation.runOnMainSync {
            a = TickerFd { first.countDown() }
            b = TickerFd { second.countDown() }
            assertTrue(a.start())
            assertTrue(b.start())
        }
        assertTrue("instance A starved", first.await(4, TimeUnit.SECONDS))
        assertTrue("instance B starved", second.await(4, TimeUnit.SECONDS))
        instrumentation.runOnMainSync {
            a.stop()
            b.stop()
        }
    }

    @Test
    fun throwingCallbackDoesNotKillTheLooperOrTheTicker() {
        // The native dispatcher must clear a pending Java exception; the main
        // looper keeps running and the *next* tick still arrives.
        val calls = AtomicInteger()
        val survived = CountDownLatch(2)
        lateinit var ticker: TickerFd
        instrumentation.runOnMainSync {
            ticker = TickerFd {
                survived.countDown()
                if (calls.incrementAndGet() == 1) {
                    throw RuntimeException("deliberate test exception from onTick")
                }
            }
            assertTrue(ticker.start())
        }
        assertTrue(
            "ticker (or main looper) died after a throwing callback",
            survived.await(4, TimeUnit.SECONDS)
        )
        instrumentation.runOnMainSync { ticker.stop() }
        assertEquals(0, survived.count)
    }
}
