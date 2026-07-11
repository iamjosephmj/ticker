cmake_minimum_required(VERSION 3.22.1)
project(tickerfd)

add_library(tickerfd SHARED
        tickerfd_jni.cpp
        wall_clock_timer.cpp
        tick_dispatcher.cpp)

# Tiny, cold-path-free binary: no exceptions/RTTI (nothing here throws),
# hidden visibility (JNIEXPORT re-exports the two entry points), size-first
# optimization, and dead-section GC.
target_compile_options(tickerfd PRIVATE
        -Oz
        -fno-exceptions
        -fno-rtti
        -fvisibility=hidden
        -ffunction-sections
        -fdata-sections)
target_link_options(tickerfd PRIVATE
        -Wl,--gc-sections)

target_link_libraries(tickerfd android log)
