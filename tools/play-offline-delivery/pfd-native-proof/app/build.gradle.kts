plugins { id("com.android.application") }
android {
    namespace = "com.appforge.v155proof"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.appforge.v155proof"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    dynamicFeatures += setOf(":nativeprobe")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation("com.google.android.play:feature-delivery:2.1.0")
}
