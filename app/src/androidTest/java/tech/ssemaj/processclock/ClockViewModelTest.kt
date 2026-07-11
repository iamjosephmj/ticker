package tech.ssemaj.processclock

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClockViewModelTest {

    @Test
    fun tickWorkRunsOnIoPoolNotMain() = runBlocking {
        val store = ViewModelStore()
        val viewModel = ViewModelProvider(
            store,
            ViewModelProvider.NewInstanceFactory()
        )[ClockViewModel::class.java]
        try {
            val first = viewModel.ticks.filterNotNull().first()

            assertFalse(
                "ticker(Dispatchers.IO) transform ran on the main thread",
                first.onMainThread
            )
            assertTrue(
                "expected an IO pool thread, got '${first.tickThread}'",
                first.tickThread.startsWith("DefaultDispatcher-worker")
            )

            // Still the kernel tick underneath: the next second arrives intact.
            val next = viewModel.ticks.filterNotNull()
                .first { it.epochSeconds > first.epochSeconds }
            assertEquals(first.epochSeconds + 1, next.epochSeconds)
            assertFalse(next.onMainThread)
        } finally {
            store.clear() // cancels viewModelScope, releasing the timerfd
        }
    }
}
