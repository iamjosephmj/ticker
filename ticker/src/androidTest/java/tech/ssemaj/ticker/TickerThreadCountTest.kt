package tech.ssemaj.ticker

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The zero-thread claim, verified at the kernel level: /proc/self/task lists
 * every thread in the process. Ticking for several seconds must not change it.
 */
@RunWith(AndroidJUnit4::class)
class TickerThreadCountTest {

    private fun threadNames(): Set<String> =
        File("/proc/self/task").listFiles().orEmpty().mapNotNull { dir ->
            runCatching { File(dir, "comm").readText().trim() }.getOrNull()
        }.toSet()

    @Test
    fun tickingCreatesNoThreads() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var ticker: TickerFd
        val threeTicks = CountDownLatch(3)

        // Warm up (loads the native lib) and settle.
        instrumentation.runOnMainSync {
            TickerFd { }.apply { assertTrue(start()); stop() }
        }
        Thread.sleep(500)
        val before = threadNames()

        instrumentation.runOnMainSync {
            ticker = TickerFd { threeTicks.countDown() }
            assertTrue(ticker.start())
        }
        assertTrue(threeTicks.await(5, TimeUnit.SECONDS))
        instrumentation.runOnMainSync { ticker.stop() }

        val after = threadNames()
        assertEquals(
            "threads appeared during ticking: ${after - before}",
            emptySet<String>(),
            after - before
        )
    }
}
