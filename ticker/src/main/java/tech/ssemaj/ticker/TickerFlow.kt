package tech.ssemaj.ticker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Bridges [TickerFd]'s callback into a cold [Flow] of boundary timestamps
 * (epoch millis), one per wall-clock [period] boundary. Internal: consumers
 * go through the public `ticker(dispatcher) { work }` overload, which makes
 * the threading of per-tick work explicit.
 *
 * The timerfd always registers on the main looper ([flowOn] Main.immediate),
 * while elements are delivered in the collector's context. Conflated — a slow
 * collector skips to the latest boundary rather than draining stale ones.
 * Each collector gets its own timer; cancellation releases it.
 *
 * Fails fast: if the native timer cannot be set up (fd exhaustion — should
 * not happen on real devices), the flow throws rather than silently
 * degrading to a weaker tick mechanism.
 */
internal fun ticker(period: Duration = 1.seconds): Flow<Long> = callbackFlow {
    val ticker = TickerFd(period.inWholeMilliseconds) { boundaryEpochMillis ->
        trySend(boundaryEpochMillis)
    }
    check(ticker.start()) { "timerfd setup failed — see TickerFd logcat for the native error" }
    awaitClose { ticker.stop() }
}
    .conflate()
    .flowOn(Dispatchers.Main.immediate)
