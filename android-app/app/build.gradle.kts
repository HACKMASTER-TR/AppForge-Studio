import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipFile
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

val appForgeProrootVersion = "v1.2.8"

val appForgeProrootAssets = listOf(
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
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun appForgeTrustedProrootHost(host: String): Boolean =
    host == "github.com" || host.endsWith(".githubusercontent.com")

fun appForgeDownloadPinnedAsset(
    initialUrl: String,
    destination: File,
    expectedSize: Long,
    expectedSha256: String
) {
    var current = URI(initialUrl)

    repeat(6) {
        require(
            current.scheme == "https" &&
                appForgeTrustedProrootHost(current.host.orEmpty())
        ) {
            "Proroot indirme adresi güvenilir değil: $current"
        }

        val connection = current.toURL().openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 15_000
        connection.readTimeout = 45_000
        connection.setRequestProperty("User-Agent", "AppForge-Studio-Build")

        try {
            when (val code = connection.responseCode) {
                in 300..399 -> {
                    val location = connection.getHeaderField("Location")
                        ?: error("Proroot yönlendirme adresi yok.")
                    current = current.resolve(location)
                }

                HttpURLConnection.HTTP_OK -> {
                    val temporary = File(destination.parentFile, "${destination.name}.part")
                    temporary.delete()
                    var total = 0L

                    connection.inputStream.buffered().use { input ->
                        temporary.outputStream().buffered().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                require(total <= expectedSize) {
                                    "Proroot dosyası beklenenden büyük."
                                }
                                output.write(buffer, 0, count)
                            }
                        }
                    }

                    require(total == expectedSize) {
                        "Proroot dosya boyutu doğrulanamadı."
                    }
                    require(appForgeSha256(temporary) == expectedSha256) {
                        "Proroot SHA-256 doğrulaması başarısız."
                    }

                    if (destination.exists()) {
                        require(destination.delete()) {
                            "Eski Proroot dosyası silinemedi."
                        }
                    }
                    require(temporary.renameTo(destination)) {
                        "Proroot dosyası etkinleştirilemedi."
                    }
                    return
                }

                else -> error("Proroot indirme HTTP $code ile başarısız.")
            }
        } finally {
            connection.disconnect()
        }
    }

    error("Proroot indirme çok fazla yönlendirme içeriyor.")
}

val appForgeProrootJniRoot = layout.buildDirectory.dir("generated/proroot/jniLibs")

val prepareAppForgeProrootRuntime =
    tasks.register<AppForgeProrootPrepareTask>("prepareAppForgeProrootRuntime") {
        group = "appforge"
        description = "Downloads and verifies the pinned AppForge rootless Linux engine."
        jniLibsDirectory.set(appForgeProrootJniRoot)
        outputs.upToDateWhen { false }

        doLast {
            val abiDirectory = jniLibsDirectory
                .get()
                .asFile
                .resolve("arm64-v8a")
                .apply { mkdirs() }

            appForgeProrootAssets.forEach { asset ->
                val target = abiDirectory.resolve(asset.name)
                val validExisting =
                    target.isFile &&
                        target.length() == asset.size &&
                        appForgeSha256(target) == asset.sha256

                if (!validExisting) {
                    target.delete()
                    appForgeDownloadPinnedAsset(
                        initialUrl = "https://github.com/coderredlab/proroot/releases/download/$appForgeProrootVersion/${asset.name}",
                        destination = target,
                        expectedSize = asset.size,
                        expectedSha256 = asset.sha256
                    )
                }

                require(
                    target.isFile &&
                        target.length() == asset.size &&
                        appForgeSha256(target) == asset.sha256
                ) {
                    "Paketlenecek Proroot dosyası doğrulanamadı: ${asset.name}"
                }
            }

            logger.lifecycle("AppForge Proroot $appForgeProrootVersion verified for arm64-v8a.")
        }
    }


val appforgeProRecoveryTest = providers.gradleProperty("appforgeProRecoveryTest").orNull == "true"
val appforgeProSecondDevice = providers.gradleProperty("appforgeProSecondDevice").orNull == "true"
val appforgeProProcessDeathTest = providers.gradleProperty("appforgeProProcessDeathTest").orNull == "true"

val appforgeBaseVersionCode =
    534

// Debug-only, explicit opt-in for the B05+V3 upgrade acceptance APK.
// Does not change normal debug, release, or Play Integrity version policy.
val appforgeIntegrationDebugVersionCode =
    providers.gradleProperty("appforgeIntegrationDebugVersionCode")
        .orNull
        ?.trim()
        ?.let { requested ->
            require(requested == "533") {
                "B05+V3 isolated debug upgrade only permits versionCode 533."
            }
            533
        }

val appforgePlayIntegrityAcceptance =
    providers.gradleProperty("appforgePlayIntegrityAcceptance")
        .orNull == "true"

val appforgePlayIntegrityAcceptanceBaseUrl =
    providers.gradleProperty("appforgePlayIntegrityAcceptanceBaseUrl")
        .orNull
        ?.trim()
        .orEmpty()

val appforgePlayIntegrityAcceptanceVersionCode =
    providers.gradleProperty(
        "appforgePlayIntegrityAcceptanceVersionCode"
    )
        .orNull
        ?.trim()
        ?.takeIf {
            it.isNotEmpty()
        }
        ?.toIntOrNull()


val appforgePlayIntegrityStagingUrl =
    Regex(
        "^https://appforge-integrity-staging\\.[A-Za-z0-9.-]+\\.workers\\.dev$"
    )

require(
    appforgePlayIntegrityAcceptanceBaseUrl.isBlank() ||
        appforgePlayIntegrityStagingUrl.matches(
            appforgePlayIntegrityAcceptanceBaseUrl
        )
) {
    "Play Integrity acceptance URL must target only the isolated staging Worker."
}

require(
    !appforgePlayIntegrityAcceptance ||
        appforgePlayIntegrityAcceptanceBaseUrl.isNotBlank()
) {
    "Play Integrity acceptance harness requires the isolated staging URL."
}

require(
    appforgePlayIntegrityAcceptanceVersionCode == null ||
        appforgePlayIntegrityAcceptanceVersionCode in
            1..2_100_000_000
) {
    "Play Integrity acceptance versionCode is invalid."
}



require(
    !appforgeProProcessDeathTest ||
        (!appforgeProRecoveryTest && !appforgeProSecondDevice)
) {
    "Process-death mode must be isolated from other Pro test modes."
}

require(!(appforgeProRecoveryTest && appforgeProSecondDevice)) {
    "Pro recovery and second-device modes cannot be combined."
}

android {
    namespace = "com.appforge.studio"
    compileSdk = 37
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.appforge.studio"
        minSdk = 26
        targetSdk = 37
        versionCode =
            if (
                appforgePlayIntegrityAcceptance
            ) {
                appforgePlayIntegrityAcceptanceVersionCode
                    ?: appforgeBaseVersionCode
            } else {
                appforgeBaseVersionCode
            }

        versionName = "5.0.34"
    }

    fun oauthClientId(name: String): String {
        val value = providers.gradleProperty(name)
            .orElse(providers.environmentVariable(name))
            .orElse("")
            .get()
            .trim()

        require(
            value.length <= 512 &&
                value.none { it == '\n' || it == '\r' || it == '\u0000' }
        ) {
            "$name geçersiz bir OAuth istemci kimliği içeriyor."
        }

        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
    }

    val releaseCertSha256 = providers.gradleProperty("APPFORGE_RELEASE_CERT_SHA256")
        .orElse("")
        .get()

    val ciDebugKeystorePath = System.getenv("APPFORGE_DEBUG_KEYSTORE_PATH")
    val ciDebugStorePassword = System.getenv("APPFORGE_DEBUG_STORE_PASSWORD")
    val ciDebugKeyAlias = System.getenv("APPFORGE_DEBUG_KEY_ALIAS") ?: "appforge-debug"
    val ciDebugKeyPassword = System.getenv("APPFORGE_DEBUG_KEY_PASSWORD")
        ?: ciDebugStorePassword.orEmpty()

    val ciDebugSigning =
        if (!ciDebugKeystorePath.isNullOrBlank() && !ciDebugStorePassword.isNullOrBlank()) {
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

    val releaseKeystorePath = System.getenv("APPFORGE_RELEASE_KEYSTORE_PATH")
    val releaseStorePassword = System.getenv("APPFORGE_RELEASE_STORE_PASSWORD")
    val releaseKeyAlias = System.getenv("APPFORGE_RELEASE_KEY_ALIAS") ?: "appforge-release"
    val releaseKeyPassword = System.getenv("APPFORGE_RELEASE_KEY_PASSWORD")
        ?: releaseStorePassword.orEmpty()

    val ciReleaseSigning =
        if (!releaseKeystorePath.isNullOrBlank() && !releaseStorePassword.isNullOrBlank()) {
            signingConfigs.create("ciRelease") {
                storeFile = file(requireNotNull(releaseKeystorePath))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                storeType = "JKS"
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
            "APPFORGE_GOOGLE_WEB_CLIENT_ID",
            "\"564043752274-ortqs7ep6cbhh7ji3taf4s41g8trnome.apps.googleusercontent.com\""
        )

        buildConfigField(
            "boolean",
            "PLAY_INTEGRITY_ACCEPTANCE_HARNESS",
            appforgePlayIntegrityAcceptance.toString()
        )

        buildConfigField(
            "String",
            "PLAY_INTEGRITY_ACCEPTANCE_BASE_URL",
            "\"$appforgePlayIntegrityAcceptanceBaseUrl\""
        )
    }

    buildTypes {
        getByName("debug") {
            ciDebugSigning?.let { signingConfig = it }
            if (appforgeProRecoveryTest) {
                applicationIdSuffix = ".prorecovery"
            } else if (appforgeProSecondDevice) {
                applicationIdSuffix = ".prodevice2"
            } else if (appforgeProProcessDeathTest) {
                applicationIdSuffix = ".prodeath"
            }
            buildConfigField(
                "boolean",
                "PRO_RECOVERY_TEST",
                appforgeProRecoveryTest.toString()
            )
            buildConfigField(
                "boolean",
                "PRO_PROCESS_DEATH_TEST",
                appforgeProProcessDeathTest.toString()
            )
        }

        getByName("release") {
            buildConfigField(
                "boolean",
                "PRO_RECOVERY_TEST",
                "false"
            )
            buildConfigField(
                "boolean",
                "PRO_PROCESS_DEATH_TEST",
                "false"
            )
            ciReleaseSigning?.let { signingConfig = it }
            /*
             * PLAY_INTEGRITY_PHYSICAL_ACCEPTANCE_V4_7_4
             *
             * The special Internal App Sharing acceptance build must remain
             * unminified. A physical Play-installed release exposed a
             * pre-Activity AndroidX Startup / WorkManager / WorkDatabase
             * initialization crash only after R8 processing.
             *
             * Normal release builds do not pass
             * appforgePlayIntegrityAcceptance and therefore remain minified
             * and resource-shrunk exactly as before.
             */
            isMinifyEnabled =
                !appforgePlayIntegrityAcceptance

            isShrinkResources =
                !appforgePlayIntegrityAcceptance
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += "**/libproroot*.so"
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
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Build a tiny unsigned shell at AppForge CI build time; users never download toolchains.
val generateFastHtmlTemplate = tasks.register<Copy>("generateFastHtmlTemplate") {
    dependsOn(":html-apk-template:assembleRelease")
    from(project(":html-apk-template").layout.buildDirectory.file("outputs/apk/release/html-apk-template-release-unsigned.apk"))
    into(layout.buildDirectory.dir("generated/html-template/assets"))
    rename { "html-apk-template.apk" }
    doLast {
        val built = layout.buildDirectory.file("generated/html-template/assets/html-apk-template.apk").get().asFile
        check(built.isFile && built.length() > 1_024L) { "CI şablonu hazırlanamadı: $built" }
    }
}

// P1 only: CI-prebuilt static HTML AAB shell asset; runtime bundling is gated.
val generateFastHtmlAabTemplate = tasks.register<Copy>("generateFastHtmlAabTemplate") {
    dependsOn(":html-apk-template:bundleRelease")
    val bundleDirectory =
        project(":html-apk-template").layout.buildDirectory.dir("outputs/bundle/release")
    from(bundleDirectory) {
        include("*.aab")
    }
    into(layout.buildDirectory.dir("generated/html-aab-template/assets"))
    rename { "html-aab-template.aab" }
    doFirst {
        val bundles =
            bundleDirectory.get().asFile.listFiles()
                ?.filter { it.isFile && it.extension == "aab" }
                .orEmpty()
        check(bundles.size == 1) {
            "Expected exactly one built HTML shell AAB, found ${bundles.size}."
        }
    }
    doLast {
        val file =
            layout.buildDirectory.file(
                "generated/html-aab-template/assets/html-aab-template.aab"
            ).get().asFile
        check(file.isFile && file.length() > 1_024L) {
            "Gömülü HTML AAB şablonu üretilemedi: $file"
        }
    }
}

// Selected source-input fingerprint, not an artifact hash or a source attestation.
// No environment, local.properties, signing inputs, timestamps or generated outputs.
fun fastHtmlSourceFingerprint(): String {
    val template = rootProject.file("html-apk-template")
    val selected = template.resolve("src/main").walkTopDown().filter { it.isFile }.toList() +
        listOf("html-apk-template/build.gradle.kts", "app/build.gradle.kts", "build.gradle.kts",
            "settings.gradle.kts", "gradle/libs.versions.toml", "gradle/wrapper/gradle-wrapper.properties")
            .map { rootProject.file(it) }.filter { it.isFile }
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update("APPFORGE_TEMPLATE_SELECTED_SOURCE_V1\n".toByteArray(Charsets.UTF_8))
    selected.distinct().sortedBy { it.relativeTo(rootProject.projectDir).invariantSeparatorsPath }.forEach { file ->
        val bytes = file.readBytes()
        val path = file.relativeTo(rootProject.projectDir).invariantSeparatorsPath
        digest.update((path + "\u0000" + bytes.size + "\u0000").toByteArray(Charsets.UTF_8))
        digest.update(bytes)
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
}
val fastHtmlSourceRecord = layout.buildDirectory.file("generated/html-source-inputs/fingerprint.txt")
val captureFastHtmlSourceInputs = tasks.register("captureFastHtmlSourceInputs") {
    outputs.file(fastHtmlSourceRecord)
    outputs.upToDateWhen { false }
    doLast {
        val record = fastHtmlSourceRecord.get().asFile
        check(record.parentFile.mkdirs() || record.parentFile.isDirectory)
        record.writeText(fastHtmlSourceFingerprint() + "\n", Charsets.US_ASCII)
    }
}
project(":html-apk-template").tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(captureFastHtmlSourceInputs)
}

// Exact template digests are generated only from this build's compiled artifacts.
// The resulting metadata and templates are covered together by the Studio APK signature.
val generateFastHtmlIntegrity = tasks.register("generateFastHtmlIntegrity") {
    dependsOn(generateFastHtmlTemplate, generateFastHtmlAabTemplate)
    val output = layout.buildDirectory.dir("generated/html-integrity/assets")
    outputs.dir(output)
    // Always recompute; stale metadata may never accompany a rebuilt template.
    outputs.upToDateWhen { false }
    doLast {
        val apk = layout.buildDirectory.file("generated/html-template/assets/html-apk-template.apk").get().asFile
        val aab = layout.buildDirectory.file("generated/html-aab-template/assets/html-aab-template.aab").get().asFile
        fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        val manifest = ZipFile(aab).use { zip ->
            zip.getInputStream(requireNotNull(zip.getEntry("base/manifest/AndroidManifest.xml"))).use { it.readBytes() }
        }
        val directory = output.get().asFile
        check(directory.mkdirs() || directory.isDirectory)
        val selectedSource = fastHtmlSourceRecord.get().asFile.readText(Charsets.US_ASCII).trim()
        check(selectedSource == fastHtmlSourceFingerprint()) { "Template sources changed during compilation." }
        directory.resolve("html-template-source-inputs.txt").writeText(
            "APPFORGE_TEMPLATE_SELECTED_SOURCE_V1 " + selectedSource + "\n", Charsets.US_ASCII
        )
        directory.resolve("html-template-integrity.txt").writeText(
            "html-apk-template.apk " + hash(apk.readBytes()) + "\n" +
            "html-aab-template.aab " + hash(aab.readBytes()) + "\n" +
            "html-aab-manifest " + hash(manifest) + "\n", Charsets.US_ASCII
        )
    }
}
android.sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/html-integrity/assets").get().asFile)
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(generateFastHtmlIntegrity)
}

android.sourceSets.getByName("main").assets.srcDir(
    layout.buildDirectory.dir("generated/html-aab-template/assets").get().asFile
)

android.sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/html-template/assets").get().asFile)
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(generateFastHtmlTemplate)
    dependsOn(generateFastHtmlAabTemplate)
}

// Unit checks consume the same compiled templates that are embedded in Studio.
tasks.withType<Test>().configureEach {
    dependsOn(generateFastHtmlIntegrity)
    inputs.files(generateFastHtmlTemplate, generateFastHtmlAabTemplate, generateFastHtmlIntegrity)
    systemProperty("appforge.templateAssets", layout.buildDirectory.get().asFile.absolutePath)
}

androidComponents {
    onVariants { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(
            prepareAppForgeProrootRuntime,
            AppForgeProrootPrepareTask::jniLibsDirectory
        )
    }

    onVariants(selector().withBuildType("debug")) { variant ->
        appforgeIntegrationDebugVersionCode?.let { code ->
            variant.outputs.forEach { output ->
                output.versionCode.set(code)
                output.versionName.set("5.0.33-b05-v3-debug")
            }
        }
    }
}

dependencies {
    implementation("com.android.tools.build:apksig:9.1.1")
    implementation(project(":termux-terminal-view"))

    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")

    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation("com.google.android.play:integrity:1.6.0")
    implementation("com.google.android.play:app-update:2.1.0")
    implementation("com.google.android.play:app-update-ktx:2.1.0")
    implementation("com.google.android.play:review:2.0.2")
    implementation("com.google.android.play:review-ktx:2.0.2")
    implementation("com.android.installreferrer:installreferrer:2.2")
    implementation("com.google.android.play:feature-delivery:2.1.0")
    implementation("com.google.android.play:feature-delivery-ktx:2.1.0")
    implementation("com.google.android.play:asset-delivery:2.3.0")
    implementation("com.google.android.play:asset-delivery-ktx:2.3.0")
    implementation("com.android.billingclient:billing-ktx:9.1.0")
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.1")
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.11.0")
    implementation("org.eclipse.jgit:org.eclipse.jgit:7.7.1.202607240634-r")
    implementation("com.github.mwiede:jsch:2.28.7")
    implementation("net.i2p.crypto:eddsa:0.3.0")
    implementation("org.slf4j:slf4j-nop:2.0.17")

    testImplementation("junit:junit:4.13.2")
    // Local JVM unit tests exercise the real org.json codec used by
    // Unified Agent persistence. android.jar only provides throwing stubs
    // for org.json during host-side unit tests, so use the real JVM library.
    testImplementation("org.json:json:20250517")


    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("org.apache.commons:commons-compress:1.27.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}

tasks.matching {
    it.name == "preBuild"
}.configureEach {
    dependsOn(prepareAppForgeProrootRuntime)
}
