import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

data class AppForgeProrootAsset(
    val name: String,
    val size: Long,
    val sha256: String
)

abstract class AppForgeProrootPrepareTask : DefaultTask() {
    @get:OutputDirectory
    abstract val jniLibsDirectory: DirectoryProperty
}

val appForgeProrootVersion =
    "v1.2.8"

val appForgeProrootAssets =
    listOf(
        AppForgeProrootAsset(
            name = "libproroot.so",
            size = 43_624L,
            sha256 = "a4e74d75b66cdc02b080adfe863dbf9951c3b30610d77beddc95488d5fe5de01"
        ),
        AppForgeProrootAsset(
            name = "libproroot-runtime.so",
            size = 375_120L,
            sha256 = "8c47a0a7db32d84c179ebb5bf3640f655a3181860ece5886ae44d92858730c34"
        ),
        AppForgeProrootAsset(
            name = "libproroot-bridge.so",
            size = 23_624L,
            sha256 = "1c5bc9537a270e8bf8b1c70222813f57b60b828bfb5503ddf8fe37685092de2f"
        ),
        AppForgeProrootAsset(
            name = "libproroot-linker.so",
            size = 79_408L,
            sha256 = "51a0ec5bfed00e572a0de09e22d9057e2befc386b78e426613d3e0ab03f4ecee"
        ),
        AppForgeProrootAsset(
            name = "libproroot-stub-loader.so",
            size = 144_144L,
            sha256 = "06c6624db3bdc45b9ced151cd781df439a37b47731d244b93e9d6a58cd48cde0"
        )
    )

fun appForgeSha256(file: File): String {
    val digest =
        MessageDigest.getInstance("SHA-256")

    file.inputStream()
        .buffered()
        .use { input ->
            val buffer =
                ByteArray(64 * 1024)

            while (true) {
                val count =
                    input.read(buffer)

                if (count < 0) {
                    break
                }

                digest.update(
                    buffer,
                    0,
                    count
                )
            }
        }

    return digest.digest()
        .joinToString("") {
            "%02x".format(it)
        }
}

fun appForgeTrustedProrootHost(
    host: String
): Boolean =
    host == "github.com" ||
        host.endsWith(
            ".githubusercontent.com"
        )

fun appForgeDownloadPinnedAsset(
    initialUrl: String,
    destination: File,
    expectedSize: Long,
    expectedSha256: String
) {
    var current =
        URI(initialUrl)

    repeat(6) {
        require(
            current.scheme == "https" &&
                appForgeTrustedProrootHost(
                    current.host.orEmpty()
                )
        ) {
            "Proroot indirme adresi güvenilir değil: $current"
        }

        val connection =
            current.toURL()
                .openConnection() as
                HttpURLConnection

        connection.instanceFollowRedirects =
            false

        connection.connectTimeout =
            15_000

        connection.readTimeout =
            45_000

        connection.setRequestProperty(
            "User-Agent",
            "AppForge-Studio-Build"
        )

        try {
            when (
                val code =
                    connection.responseCode
            ) {
                in 300..399 -> {
                    val location =
                        connection.getHeaderField(
                            "Location"
                        ) ?: error(
                            "Proroot yönlendirme adresi yok."
                        )

                    current =
                        current.resolve(
                            location
                        )
                }

                HttpURLConnection.HTTP_OK -> {
                    val temporary =
                        File(
                            destination.parentFile,
                            "${destination.name}.part"
                        )

                    temporary.delete()

                    var total =
                        0L

                    connection.inputStream
                        .buffered()
                        .use { input ->
                            temporary.outputStream()
                                .buffered()
                                .use { output ->
                                    val buffer =
                                        ByteArray(
                                            64 * 1024
                                        )

                                    while (true) {
                                        val count =
                                            input.read(
                                                buffer
                                            )

                                        if (count < 0) {
                                            break
                                        }

                                        total +=
                                            count

                                        require(
                                            total <=
                                                expectedSize
                                        ) {
                                            "Proroot dosyası beklenenden büyük."
                                        }

                                        output.write(
                                            buffer,
                                            0,
                                            count
                                        )
                                    }
                                }
                        }

                    require(
                        total == expectedSize
                    ) {
                        "Proroot dosya boyutu doğrulanamadı."
                    }

                    require(
                        appForgeSha256(
                            temporary
                        ) ==
                            expectedSha256
                    ) {
                        "Proroot SHA-256 doğrulaması başarısız."
                    }

                    if (destination.exists()) {
                        require(
                            destination.delete()
                        ) {
                            "Eski Proroot dosyası silinemedi."
                        }
                    }

                    require(
                        temporary.renameTo(
                            destination
                        )
                    ) {
                        "Proroot dosyası etkinleştirilemedi."
                    }

                    return
                }

                else ->
                    error(
                        "Proroot indirme HTTP $code ile başarısız."
                    )
            }
        } finally {
            connection.disconnect()
        }
    }

    error(
        "Proroot indirme çok fazla yönlendirme içeriyor."
    )
}

val appForgeProrootJniRoot =
    layout.buildDirectory.dir(
        "generated/proroot/jniLibs"
    )

val prepareAppForgeProrootRuntime =
    tasks.register<AppForgeProrootPrepareTask>(
        "prepareAppForgeProrootRuntime"
    ) {
        group =
            "appforge"

        description =
            "Downloads and verifies the pinned AppForge rootless Linux engine."

        jniLibsDirectory.set(
            appForgeProrootJniRoot
        )

        outputs.upToDateWhen {
            false
        }

        doLast {
            val abiDirectory =
                jniLibsDirectory
                    .get()
                    .asFile
                    .resolve(
                        "arm64-v8a"
                    )
                    .apply {
                        mkdirs()
                    }

            appForgeProrootAssets
                .forEach { asset ->
                    val target =
                        abiDirectory.resolve(
                            asset.name
                        )

                    val validExisting =
                        target.isFile &&
                            target.length() ==
                                asset.size &&
                            appForgeSha256(
                                target
                            ) ==
                                asset.sha256

                    if (!validExisting) {
                        target.delete()

                        appForgeDownloadPinnedAsset(
                            initialUrl =
                                "https://github.com/coderredlab/proroot/releases/download/$appForgeProrootVersion/${asset.name}",
                            destination =
                                target,
                            expectedSize =
                                asset.size,
                            expectedSha256 =
                                asset.sha256
                        )
                    }

                    require(
                        target.isFile &&
                            target.length() ==
                                asset.size &&
                            appForgeSha256(
                                target
                            ) ==
                                asset.sha256
                    ) {
                        "Paketlenecek Proroot dosyası doğrulanamadı: ${asset.name}"
                    }
                }

            logger.lifecycle(
                "AppForge Proroot $appForgeProrootVersion verified for arm64-v8a."
            )
        }
    }

val appForgeStudioFirebaseConfigured =
    file("google-services.json").isFile

if (appForgeStudioFirebaseConfigured) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.appforge.studio"
    compileSdk = 37
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.appforge.studio"
        minSdk = 26
        targetSdk = 37
        versionCode = 520
        versionName = "5.0.20"
    }

    fun oauthClientId(name: String): String {
        val value =
            providers.gradleProperty(name)
                .orElse(
                    providers.environmentVariable(name)
                )
                .orElse("")
                .get()
                .trim()

        require(
            value.length <= 512 &&
                value.none {
                    it == '\n' ||
                        it == '\r' ||
                        it == '\u0000'
                }
        ) {
            "$name geçersiz bir OAuth istemci kimliği içeriyor."
        }

        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
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

        buildConfigField(
            "String",
            "APPFORGE_GITHUB_OAUTH_CLIENT_ID",
            "\"${oauthClientId("APPFORGE_GITHUB_OAUTH_CLIENT_ID")}\""
        )

        buildConfigField(
            "String",
            "APPFORGE_RAILWAY_OAUTH_CLIENT_ID",
            "\"${oauthClientId("APPFORGE_RAILWAY_OAUTH_CLIENT_ID")}\""
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
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols +=
                "**/libproroot*.so"
        }

        resources {
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*"
            )
        }
    }

    externalNativeBuild {
        cmake {
            path =
                file(
                    "src/main/cpp/CMakeLists.txt"
                )

            version =
                "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(
            prepareAppForgeProrootRuntime,
            AppForgeProrootPrepareTask::jniLibsDirectory
        )
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
    implementation(
        "org.eclipse.jgit:org.eclipse.jgit:7.7.1.202607240634-r"
    )
    implementation(
        "com.github.mwiede:jsch:2.28.7"
    )
    implementation(
        "net.i2p.crypto:eddsa:0.3.0"
    )
    implementation(
        "org.slf4j:slf4j-nop:2.0.17"
    )

    testImplementation("junit:junit:4.13.2")


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

tasks.matching {
    it.name == "preBuild"
}.configureEach {
    dependsOn(
        prepareAppForgeProrootRuntime
    )
}
