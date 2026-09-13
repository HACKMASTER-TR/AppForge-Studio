plugins {
    id("com.android.library")
}

android {
    namespace = "com.termux.view"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.9.0")

    api(
        project(":termux-terminal-emulator")
    )

    testImplementation("junit:junit:4.13.2")
}
