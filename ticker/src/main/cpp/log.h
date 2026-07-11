#pragma once

#include <android/log.h>

#define TICKERFD_TAG "TickerFd"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TICKERFD_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TICKERFD_TAG, __VA_ARGS__)
