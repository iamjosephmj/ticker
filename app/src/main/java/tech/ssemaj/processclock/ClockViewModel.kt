package tech.ssemaj.processclock

import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import tech.ssemaj.ticker.ticker

/**
 * One tick as observed by the ViewModel. [tickThread] and [onMainThread] are
 * captured *inside* the ticker transform — they record where the per-tick
 * work actually ran, which is the ticker(dispatcher) contract under test.
 */
data class ClockTick(
    val epochSeconds: Long,
    val tickThread: String,
    val onMainThread: Boolean,
)

class ClockViewModel : ViewModel() {

    /**
     * Kernel ticks with per-tick work on [Dispatchers.IO]. WhileSubscribed
     * releases the timerfd entirely when no UI is collecting (screen off /
     * backgrounded), and restarts it on the next subscriber.
     */
    val ticks: StateFlow<ClockTick?> =
        ticker(Dispatchers.IO) { boundaryEpochMillis ->
            ClockTick(
                epochSeconds = boundaryEpochMillis / 1_000,
                tickThread = Thread.currentThread().name,
                onMainThread = Looper.myLooper() == Looper.getMainLooper(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
