#pragma once

// Kernel wall-clock timer: a timerfd on CLOCK_REALTIME, armed absolutely on
// multiples of a period since the epoch, with TFD_TIMER_CANCEL_ON_SET so
// clock-set events surface as ECANCELED on read().

// Creates a non-blocking CLOCK_REALTIME timerfd. Returns -1 on failure.
int createWallClockTimer();

// Arms the timerfd to fire exactly on the next multiple of periodMs since the
// epoch, then every periodMs after. Returns the first boundary that will fire
// (ms since epoch), or -1 if timerfd_settime fails.
long long armToNextBoundary(int fd, long long periodMs);

// Current CLOCK_REALTIME in ms since epoch.
long long wallClockNowMs();
