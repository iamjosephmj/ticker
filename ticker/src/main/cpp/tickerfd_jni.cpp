// JNI entry points and looper wiring for the ticker.
//
// Composition root: a wall-clock timerfd (wall_clock_timer) is registered
// into the *calling thread's* existing Looper via ALooper_addFd, so the
// thread's own epoll_wait — the same one already servicing input and vsync —
// wakes when the kernel fires the timer. Ticks are forwarded to Kotlin by
// tick_dispatcher. Zero extra threads.

#include <jni.h>
#include <android/looper.h>
#include <unistd.h>
#include <errno.h>
#include <stdlib.h>
#include <string.h>

#include "log.h"
#include "ticker_state.h"
#include "tick_dispatcher.h"
#include "wall_clock_timer.h"

namespace {

// Runs on the looper's thread each time the fd is readable.
int onFdEvent(int fd, int /*events*/, void *data) {
    auto *state = static_cast<TickerState *>(data);
    uint64_t expirations = 0;
    ssize_t n = read(fd, &expirations, sizeof(expirations));

    if (n < 0) {
        if (errno == ECANCELED) {
            // System clock was set. Re-align to the new wall clock and tick
            // immediately so consumers correct without waiting a period.
            LOGI("wall clock changed, re-arming");
            long long next = armToNextBoundary(fd, state->periodMs);
            dispatchTick(state, (wallClockNowMs() / state->periodMs) * state->periodMs);
            if (next > 0) state->nextBoundaryMs = next;
        } else if (errno != EAGAIN && errno != EINTR) {
            LOGE("read failed: %s", strerror(errno));
        }
        return 1; // keep the fd registered
    }

    // Report the newest elapsed boundary (skip stale ones if the looper was
    // busy across several periods) and advance past it.
    long long reported =
        state->nextBoundaryMs + (long long) (expirations - 1) * state->periodMs;
    dispatchTick(state, reported);
    state->nextBoundaryMs = reported + state->periodMs;
    return 1;
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_tech_ssemaj_ticker_TickerFd_nativeStart(JNIEnv *env, jclass /*clazz*/,
                                             jobject callback, jlong periodMs) {
    if (periodMs <= 0) {
        LOGE("invalid period: %lld ms", (long long) periodMs);
        return 0;
    }

    ALooper *looper = ALooper_forThread();
    if (looper == nullptr) {
        LOGE("no looper on this thread");
        return 0;
    }

    int fd = createWallClockTimer();
    if (fd < 0) return 0;
    long long firstBoundary = armToNextBoundary(fd, periodMs);
    if (firstBoundary < 0) {
        close(fd);
        return 0;
    }

    jclass cls = env->GetObjectClass(callback);
    jmethodID onTick = env->GetMethodID(cls, "onTick", "(J)V");
    if (onTick == nullptr) {
        // Callback class lacks onTick(J)V — typically an R8 rename in a
        // consumer that dropped the library's keep rules.
        LOGE("onTick(J)V not found on callback class (missing keep rules?)");
        env->ExceptionClear();
        close(fd);
        return 0;
    }

    // malloc, not new: the library links with ANDROID_STL=none, so operator
    // new/delete are unavailable. Every field is assigned below.
    auto *state = static_cast<TickerState *>(malloc(sizeof(TickerState)));
    if (state == nullptr) {
        close(fd);
        return 0;
    }
    state->fd = fd;
    state->env = env; // callbacks run on this exact thread
    state->callback = env->NewGlobalRef(callback);
    state->onTick = onTick;
    state->periodMs = periodMs;
    state->nextBoundaryMs = firstBoundary;

    if (ALooper_addFd(looper, fd, ALOOPER_POLL_CALLBACK, ALOOPER_EVENT_INPUT,
                      onFdEvent, state) != 1) {
        LOGE("ALooper_addFd failed");
        env->DeleteGlobalRef(state->callback);
        free(state);
        close(fd);
        return 0;
    }

    LOGI("started: timerfd=%d period=%lldms on looper of tid=%d",
         fd, (long long) periodMs, gettid());
    return reinterpret_cast<jlong>(state);
}

extern "C" JNIEXPORT void JNICALL
Java_tech_ssemaj_ticker_TickerFd_nativeStop(JNIEnv *env, jclass /*clazz*/,
                                            jlong handle) {
    auto *state = reinterpret_cast<TickerState *>(handle);
    if (state == nullptr) return;

    // Must run on the same looper thread that registered the fd (enforced on
    // the Kotlin side), so no callback can be mid-flight while we tear down.
    ALooper *looper = ALooper_forThread();
    if (looper != nullptr) ALooper_removeFd(looper, state->fd);
    close(state->fd);
    env->DeleteGlobalRef(state->callback);
    free(state);
    LOGI("stopped");
}
