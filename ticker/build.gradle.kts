plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.maven.publish.vanniktech)
}

version = findProperty("VERSION_NAME")?.toString() ?: "1.0.0"

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

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
    coordinates("tech.thessemaj", "ticker", version.toString())
    pom {
        name = "ticker"
        description = "Kernel-aligned wall-clock ticks for Android. Zero threads. One function."
        url = "https://github.com/iamjosephmj/ticker"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
        developers {
            developer {
                id = "iamjosephmj"
                name = "Joseph MJ"
                url = "https://github.com/iamjosephmj"
            }
        }
        scm {
            url = "https://github.com/iamjosephmj/ticker"
            connection = "scm:git:git://github.com/iamjosephmj/ticker.git"
            developerConnection = "scm:git:ssh://git@github.com/iamjosephmj/ticker.git"
        }
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/iamjosephmj/ticker")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: findProperty("gpr.user")?.toString()
                password = System.getenv("GITHUB_TOKEN") ?: findProperty("gpr.key")?.toString()
            }
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
