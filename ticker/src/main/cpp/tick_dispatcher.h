#pragma once

#include <jni.h>
#include "ticker_state.h"

// Calls up into the Kotlin OnTick callback on the current (looper) thread.
// Guards against a throwing callback poisoning the looper: any pending Java
// exception is logged and cleared.
void dispatchTick(TickerState *state, jlong epochSeconds);
