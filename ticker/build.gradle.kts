plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "tech.ssemaj.ticker"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild {
            cmake {
                // No C++ stdlib: the native core is POSIX + JNI only, and
                // c++_static would embed ~240KB of libc++ per ABI.
                arguments += listOf("-DANDROID_STL=none")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        explicitApi()
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    // Flow appears in the public API surface, so coroutines is `api`.
    api(libs.kotlinx.coroutines.android)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.junit)
}
