#include "tick_dispatcher.h"

void dispatchTick(TickerState *state, jlong epochSeconds) {
    JNIEnv *env = state->env; // same thread that registered the fd
    env->CallVoidMethod(state->callback, state->onTick, epochSeconds);
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
}
