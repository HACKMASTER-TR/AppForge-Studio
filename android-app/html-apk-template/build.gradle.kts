plugins {
    id("com.android.application")
}

android {
    namespace = "com.appforge.htmlstub"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.appforge.htmlstub"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
}

// Decoder is compiled into the CI template; no Play Services or model download on device.
dependencies {
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.android.billingclient:billing:9.1.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
