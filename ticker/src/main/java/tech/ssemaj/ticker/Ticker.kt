package tech.ssemaj.ticker

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The public ticker API: kernel-aligned wall-clock ticks with per-tick work
 * threaded onto [dispatcher]:
 *
 * ```
 * ticker(Dispatchers.IO) { readProcStats() }                    // every second
 * ticker(Dispatchers.Default, period = 250.milliseconds) { it } // 4 Hz
 * ```
 *
 * Ticks fire exactly on wall-clock multiples of [period] (a 1s ticker flips
 * on the true second; a 250ms ticker on :000/:250/:500/:750) and carry the
 * exact boundary timestamp in epoch millis — consecutive ticks always differ
 * by exactly the period. Shorter periods mean proportionally more main-thread
 * wake-ups; the ticker never wakes a suspended device regardless of period.
 *
 * [transform] runs on [dispatcher] for every tick, regardless of where the
 * flow is collected; the timerfd itself stays on the main looper. This is the
 * entry point to use for per-tick work — a `map` added *after* the flow would
 * run in the collector's context instead.
 */
public fun <T> ticker(
    dispatcher: CoroutineDispatcher,
    period: Duration = 1.seconds,
    transform: suspend (boundaryEpochMillis: Long) -> T,
): Flow<T> =
    ticker(period).map(transform).flowOn(dispatcher).conflate()
// The trailing conflate matters: flowOn introduces its own buffered channel
// downstream of the source's conflation, which would hand a slow collector up
// to 64 stale boundaries. Conflating here fuses with the flowOn channel,
// keeping the contract: a slow collector always skips to the newest boundary.
