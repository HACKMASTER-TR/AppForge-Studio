// Separate diagnostic application: do not include in android-app/settings.gradle.kts.
// NEVER build the existing AppForge APK through this project.
plugins {
    id("com.android.application") version "9.1.1"
}

android {
    namespace = "com.appforge.v9.keystoreprobe"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.appforge.v9.keystoreprobe"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "5e6-diagnostics-only"
    }
    buildFeatures { buildConfig = false }
}
