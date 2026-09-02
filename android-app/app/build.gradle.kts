plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val appForgeStudioFirebaseConfigured =
    file("google-services.json").isFile

if (appForgeStudioFirebaseConfigured) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.appforge.studio"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.appforge.studio"
        minSdk = 26
        targetSdk = 37
        versionCode = 512
        versionName = "5.0.12"
    }

    val releaseCertSha256 =
        providers.gradleProperty("APPFORGE_RELEASE_CERT_SHA256")
            .orElse("")
            .get()

    val ciDebugKeystorePath =
        System.getenv("APPFORGE_DEBUG_KEYSTORE_PATH")

    val ciDebugStorePassword =
        System.getenv("APPFORGE_DEBUG_STORE_PASSWORD")

    val ciDebugKeyAlias =
        System.getenv("APPFORGE_DEBUG_KEY_ALIAS")
            ?: "appforge-debug"

    val ciDebugKeyPassword =
        System.getenv("APPFORGE_DEBUG_KEY_PASSWORD")
            ?: ciDebugStorePassword.orEmpty()

    val ciDebugSigning =
        if (
            !ciDebugKeystorePath.isNullOrBlank() &&
            !ciDebugStorePassword.isNullOrBlank()
        ) {
            signingConfigs.create("ciDebug") {
                storeFile = file(requireNotNull(ciDebugKeystorePath))
                storePassword = ciDebugStorePassword.orEmpty()
                keyAlias = ciDebugKeyAlias
                keyPassword = ciDebugKeyPassword
                storeType = "PKCS12"
            }
        } else {
            null
        }


    val releaseKeystorePath =
        System.getenv("APPFORGE_RELEASE_KEYSTORE_PATH")

    val releaseStorePassword =
        System.getenv("APPFORGE_RELEASE_STORE_PASSWORD")

    val releaseKeyAlias =
        System.getenv("APPFORGE_RELEASE_KEY_ALIAS")
            ?: "appforge-release"

    val releaseKeyPassword =
        System.getenv("APPFORGE_RELEASE_KEY_PASSWORD")
            ?: releaseStorePassword.orEmpty()

    val ciReleaseSigning =
        if (
            !releaseKeystorePath.isNullOrBlank() &&
            !releaseStorePassword.isNullOrBlank()
        ) {
            signingConfigs.create("ciRelease") {
                storeFile =
                    file(
                        requireNotNull(
                            releaseKeystorePath
                        )
                    )

                storePassword =
                    releaseStorePassword

                keyAlias =
                    releaseKeyAlias

                keyPassword =
                    releaseKeyPassword

                storeType =
                    "JKS"
            }
        } else {
            null
        }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    defaultConfig {
        buildConfigField(
            "String",
            "RELEASE_CERT_SHA256",
            "\"${releaseCertSha256}\""
        )
    }

    buildTypes {
        getByName("debug") {
            ciDebugSigning?.let {
                signingConfig = it
            }
        }

        getByName("release") {
            ciReleaseSigning?.let {
                signingConfig = it
            }

            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    val composeBom =
        platform("androidx.compose:compose-bom:2026.08.00")

    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation(
        "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2"
    )
    implementation(
        platform(
            "com.google.firebase:firebase-bom:34.17.0"
        )
    )
    implementation(
        "com.google.firebase:firebase-messaging"
    )

    implementation(
        "com.google.android.play:integrity:1.6.0"
    )
    implementation(
        "com.android.billingclient:billing-ktx:9.1.0"
    )
    implementation(
        "com.google.ai.edge.litertlm:litertlm-android:0.11.0"
    )


    // VideoForge V4.1.2
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("com.google.mlkit:language-id:17.0.6")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.github.k2-fsa:sherpa-onnx:v1.13.4")
    implementation("org.apache.commons:commons-compress:1.27.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
