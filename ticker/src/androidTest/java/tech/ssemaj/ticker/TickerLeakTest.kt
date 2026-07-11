package tech.ssemaj.ticker

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TickerLeakTest {

    private fun openFdCount(): Int = File("/proc/self/fd").list()?.size ?: -1

    /**
     * Every start creates a timerfd and every stop must close it. 100 rapid
     * cycles must leave the process fd table where it started — any leak
     * shows up as +100 fds, far above the noise of unrelated runtime activity.
     */
    @Test
    fun rapidStartStopCyclesDoNotLeakFileDescriptors() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        // Warm up: load the native lib and let the runtime settle its own fds.
        instrumentation.runOnMainSync {
            TickerFd { }.apply { assertTrue(start()); stop() }
        }

        val before = openFdCount()
        instrumentation.runOnMainSync {
            repeat(100) {
                val ticker = TickerFd { }
                assertTrue(ticker.start())
                ticker.stop()
            }
        }
        val after = openFdCount()

        assertTrue(
            "fd leak: $before open fds before, $after after 100 start/stop cycles",
            after - before < 10
        )
    }

    /**
     * The flow path: cancellation (including cancellation *before* the first
     * tick ever arrives) must run awaitClose and release the timer. 30 cycles
     * where most collections are cancelled mid-wait.
     */
    @Test
    fun flowCollectCancelCyclesDoNotLeakFileDescriptors() = runBlocking {
        ticker(Dispatchers.Default) { it }.first() // warm up

        val before = openFdCount()
        repeat(30) {
            withTimeoutOrNull(80) { ticker(Dispatchers.Default) { it }.first() }
        }
        val after = openFdCount()

        assertTrue(
            "fd leak: $before open fds before, $after after 30 collect/cancel cycles",
            after - before < 10
        )
    }
}
