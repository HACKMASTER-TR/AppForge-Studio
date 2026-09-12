plugins {
    id("com.android.library")
}

android {
    namespace = "com.termux.emulator"
    compileSdk = 37
    ndkVersion = "28.2.13676358"

    defaultConfig {
        minSdk = 26
        targetSdk = 37

        externalNativeBuild {
            ndkBuild {
                cFlags += listOf(
                    "-std=c11",
                    "-Wall",
                    "-Wextra",
                    "-Werror",
                    "-Os",
                    "-fno-stack-protector",
                    "-Wl,--gc-sections"
                )
            }
        }

        ndk {
            abiFilters += listOf(
                "x86",
                "x86_64",
                "armeabi-v7a",
                "arm64-v8a"
            )
        }
    }

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.9.0")
    testImplementation("junit:junit:4.13.2")
}
