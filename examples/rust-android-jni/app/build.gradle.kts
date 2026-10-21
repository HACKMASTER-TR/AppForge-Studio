plugins { id("com.android.application") }
android {
    namespace = "com.example.rustjni"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.example.rustjni"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        ndk { abiFilters += "arm64-v8a" }
    }
    // Production verifier deliberately checks the exact reviewed native payload.
    packaging { jniLibs { keepDebugSymbols += "**/libappforge_jni.so" } }
    buildTypes { getByName("release") { signingConfig = signingConfigs.getByName("debug") } }
}
