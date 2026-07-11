#pragma once

#include <jni.h>

// One ticker instance. Heap-allocated per start, passed as ALooper_addFd's
// data pointer, owned by the Kotlin side as an opaque handle. No globals;
// multiple tickers can coexist on the same looper.
//
// `env` is cached at registration: JNIEnv is valid per-thread, and every
// callback runs on the exact thread that registered the fd (the looper
// thread), so no per-tick GetEnv lookup is needed.
//
// `nextBoundaryMs` is the identity of the next unreported tick: ticks carry
// the exact boundary timestamp they were scheduled for, not a measured "now",
// so consecutive ticks always differ by exactly `periodMs`.
struct TickerState {
    int fd = -1;
    JNIEnv *env = nullptr;
    jobject callback = nullptr;   // global ref to TickerFd.OnTick
    jmethodID onTick = nullptr;
    long long periodMs = 0;
    long long nextBoundaryMs = 0;
};
