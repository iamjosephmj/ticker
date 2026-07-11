package tech.ssemaj.ticker

import android.os.Looper

/**
 * Raw binding to the native ticker: a kernel timerfd registered on the main
 * thread's Looper via ALooper_addFd. No extra thread, no Handler messages —
 * the main thread's own epoll wakes once per period, exactly on wall-clock
 * period boundaries (CLOCK_REALTIME, absolute). The kernel also reports
 * system clock changes (TFD_TIMER_CANCEL_ON_SET), triggering an immediate
 * corrective tick.
 *
 * Ticks carry the exact boundary timestamp (epoch millis) they were scheduled
 * for — consecutive ticks always differ by exactly [periodMillis].
 *
 * Must be started and stopped on the main thread — the timer joins the main
 * looper's fd set. This is the escape hatch; prefer the [ticker] Flow API.
 */
public class TickerFd(
    private val periodMillis: Long = 1_000,
    private val onTick: OnTick,
) {

    public fun interface OnTick {
        public fun onTick(boundaryEpochMillis: Long)
    }

    init {
        require(periodMillis > 0) { "periodMillis must be positive, was $periodMillis" }
    }

    private var handle = 0L

    /** Starts ticking. Returns false if the native timer could not be set up. */
    public fun start(): Boolean {
        requireMainThread()
        if (handle != 0L) return true
        handle = nativeStart(onTick, periodMillis)
        return handle != 0L
    }

    /** Stops ticking and releases the timer. Idempotent; [start] may be called again. */
    public fun stop() {
        requireMainThread()
        if (handle == 0L) return
        nativeStop(handle)
        handle = 0L
    }

    private fun requireMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "TickerFd must be used on the main thread — the timerfd joins the main looper"
        }
    }

    private companion object {
        init {
            System.loadLibrary("tickerfd")
        }

        @JvmStatic
        external fun nativeStart(callback: OnTick, periodMillis: Long): Long

        @JvmStatic
        external fun nativeStop(handle: Long)
    }
}
