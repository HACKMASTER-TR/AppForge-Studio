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

    // QR / Barcode
    implementation(
        "com.google.android.gms:" +
            "play-services-code-scanner:" +
            "16.1.0"
    )

    // AdMob
    implementation(
        "com.google.android.gms:" +
            "play-services-ads:" +
            "25.4.0"
    )

    // UMP
    implementation(
        "com.google.android.ump:" +
            "user-messaging-platform:" +
            "4.0.0"
    )

    // Billing
    implementation(
        "com.android.billingclient:" +
            "billing:" +
            "9.1.0"
    )

    // Firebase
    implementation(
        "com.google.firebase:" +
            "firebase-analytics:" +
            "23.2.0"
    )

    implementation(
        "com.google.firebase:" +
            "firebase-crashlytics:" +
            "20.1.0"
    )
}
