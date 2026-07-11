#include "wall_clock_timer.h"
#include "log.h"

#include <sys/timerfd.h>
#include <errno.h>
#include <string.h>
#include <time.h>

int createWallClockTimer() {
    int fd = timerfd_create(CLOCK_REALTIME, TFD_NONBLOCK | TFD_CLOEXEC);
    if (fd < 0) {
        LOGE("timerfd_create failed: %s", strerror(errno));
    }
    return fd;
}

long long wallClockNowMs() {
    timespec now{};
    clock_gettime(CLOCK_REALTIME, &now);
    return now.tv_sec * 1000LL + now.tv_nsec / 1000000LL;
}

long long armToNextBoundary(int fd, long long periodMs) {
    long long boundary = (wallClockNowMs() / periodMs + 1) * periodMs;
    itimerspec spec{};
    spec.it_value.tv_sec = boundary / 1000;
    spec.it_value.tv_nsec = (boundary % 1000) * 1000000LL;
    spec.it_interval.tv_sec = periodMs / 1000;
    spec.it_interval.tv_nsec = (periodMs % 1000) * 1000000LL;
    if (timerfd_settime(fd, TFD_TIMER_ABSTIME | TFD_TIMER_CANCEL_ON_SET,
                        &spec, nullptr) != 0) {
        LOGE("timerfd_settime failed: %s", strerror(errno));
        return -1;
    }
    return boundary;
}
