# Native code resolves these by name at runtime — R8 must not rename them.

# JNI entry points are registered against these method names/signatures.
-keepclasseswithmembers class tech.ssemaj.ticker.TickerFd {
    native <methods>;
}

# nativeStart looks up "onTick(J)V" on the callback's class via GetMethodID.
-keepclassmembernames interface tech.ssemaj.ticker.TickerFd$OnTick {
    void onTick(long);
}
-keepclassmembers class * implements tech.ssemaj.ticker.TickerFd$OnTick {
    void onTick(long);
}
