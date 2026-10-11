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
