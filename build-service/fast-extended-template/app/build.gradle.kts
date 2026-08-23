plugins {
    id("com.android.application")
}

android {
    namespace = "com.appforge.extended"

    compileSdk = 37

    defaultConfig {
        applicationId = "com.appforge.fasttemplate"

        minSdk = 26
        targetSdk = 37

        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
}

dependencies {

    // ======================================================
    // AppForge temel runtime
    // ======================================================

    implementation(
        "androidx.core:core-ktx:1.19.0"
    )

    implementation(
        "androidx.appcompat:appcompat:1.7.1"
    )

    implementation(
        "androidx.core:core-splashscreen:1.2.0"
    )

    implementation(
        "androidx.webkit:webkit:1.16.0"
    )

    // ======================================================
    // QR / Barcode
    // ======================================================

    implementation(
        "com.google.android.gms:" +
            "play-services-code-scanner:" +
            "16.1.0"
    )

    // ======================================================
    // AdMob
    // ======================================================

    implementation(
        "com.google.android.gms:" +
            "play-services-ads:" +
            "25.4.0"
    )

    // ======================================================
    // UMP Consent
    // ======================================================

    implementation(
        "com.google.android.ump:" +
            "user-messaging-platform:" +
            "4.0.0"
    )

    // ======================================================
    // Google Play Billing
    // ======================================================

    implementation(
        "com.android.billingclient:" +
            "billing-ktx:" +
            "9.1.0"
    )

    // ======================================================
    // Firebase
    // ======================================================

    implementation(
        platform(
            "com.google.firebase:" +
                "firebase-bom:" +
                "34.17.0"
        )
    )

    implementation(
        "com.google.firebase:" +
            "firebase-analytics"
    )

    implementation(
        "com.google.firebase:" +
            "firebase-crashlytics"
    )
}
