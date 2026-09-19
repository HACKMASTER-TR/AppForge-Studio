plugins {
    id("com.android.application")
    id("com.chaquo.python")
}

android {
    namespace = "com.appforge.pythonruntime"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.appforge.pythonruntime"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters += listOf(
                "arm64-v8a",
                "x86_64"
            )
        }
    }
}

chaquopy {
    defaultConfig {
        version = "3.11"

        buildPython(
            "/usr/bin/python3"
        )

        pip {
            install(
                "-r",
                "requirements.txt"
            )
        }
    }
}
