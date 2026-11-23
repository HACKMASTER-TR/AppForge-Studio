import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipFile
import java.util.UUID
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar

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

val appForgeOfflineBuild = gradle.startParameter.isOffline
val appForgeProrootCache = File(gradle.gradleUserHomeDir,"appforge/proroot/$appForgeProrootVersion/arm64-v8a")
val appForgeVendoredProrootRoot = layout.projectDirectory.dir("src/main/proroot-pinned/arm64-v8a").asFile

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

                val cache=appForgeProrootCache.resolve(asset.name)
                fun verified(file:File)=file.isFile && file.length()==asset.size && appForgeSha256(file)==asset.sha256
                fun promote(source:File,destination:File) {
                    destination.parentFile.mkdirs()
                    require(!Files.isSymbolicLink(destination.toPath()))
                    val part=File(destination.path+".part-"+UUID.randomUUID())
                    try {
                        source.copyTo(part);require(verified(part))
                        Files.move(part.toPath(),destination.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
                    }finally{part.delete()}
                }
                // Prefer the exact upstream SHA-256 pinned Proroot assets from the last verified CI APK.
                // An invalid vendored asset fails closed; a valid one populates the usual Gradle cache.
                val vendored=appForgeVendoredProrootRoot.resolve(asset.name)
                require(vendored.isFile && !Files.isSymbolicLink(vendored.toPath()) && verified(vendored)) {
                    "APPFORGE_PROROOT_PINNED_SOURCE_INVALID: ${asset.name}"
                }
                if(!verified(cache)) promote(vendored,cache)
                logger.lifecycle("APPFORGE_PROROOT_PINNED=PASS: ${asset.name}")
                if(validExisting && !verified(cache))promote(target,cache)
                if(!validExisting) {
                    if(!verified(cache)) {
                        check(!appForgeOfflineBuild){"APPFORGE_OFFLINE_PINNED_ASSET_MISSING: ${asset.name}. Prepare the verified Proroot cache before disconnecting."}
                        cache.parentFile.mkdirs()
                        val part=File(cache.path+".download-"+UUID.randomUUID())
                        try {
                            // Retry only explicitly transient GitHub HTTP responses.
                            // HTTPS, trusted-host, size and SHA-256 checks stay unchanged.
                            var attempt = 1
                            while (true) {
                                try {
                                    appForgeDownloadPinnedAsset(
                                        initialUrl = "https://github.com/coderredlab/proroot/releases/download/$appForgeProrootVersion/${asset.name}",
                                        destination = part,expectedSize = asset.size,expectedSha256 = asset.sha256)
                                    break
                                } catch (failure: IllegalStateException) {
                                    val match = Regex("""^Proroot indirme HTTP (429|502|503|504) ile başarısız\.$""")
                                        .matchEntire(failure.message.orEmpty())
                                    if (match == null || attempt >= 4) throw failure
                                    val code = match.groupValues[1]
                                    logger.warn("APPFORGE_PROROOT_HTTP_RETRY=$attempt/4 STATUS=$code")
                                    Thread.sleep(2_000L * attempt)
                                    attempt++
                                }
                            }
                            promote(part,cache)
                        }finally{part.delete()}
                    }
                    promote(cache,target)
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

val appforgeV9R2DeviceAcceptance =
    providers.gradleProperty("appforgeV9R2DeviceAcceptance")
        .orNull == "true"

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

require(!appforgeV9R2DeviceAcceptance ||
    (!appforgeProProcessDeathTest && !appforgeProRecoveryTest && !appforgeProSecondDevice &&
     !appforgePlayIntegrityAcceptance)) {
    "V9 R2 physical acceptance must be isolated from all other debug modes."
}

require(!(appforgeProRecoveryTest && appforgeProSecondDevice)) {
    "Pro recovery and second-device modes cannot be combined."
}

// A new test identity never replaces production or an earlier acceptance APK/data.
val appforgeV9R2Issue78Acceptance = providers.gradleProperty("appforgeV9R2Issue78Acceptance").orNull == "true"
require(!appforgeV9R2Issue78Acceptance || (!appforgeV9R2DeviceAcceptance &&
    !appforgeProProcessDeathTest && !appforgeProRecoveryTest && !appforgeProSecondDevice &&
    !appforgePlayIntegrityAcceptance)) { "Issue78 acceptance must use its own package identity" }

val appforgeV9R2Isolated2Acceptance =
    providers.gradleProperty("appforgeV9R2Isolated2Acceptance").orNull == "true"
require(!appforgeV9R2Isolated2Acceptance ||
    (!appforgeV9R2Issue78Acceptance && !appforgeV9R2DeviceAcceptance &&
     !appforgeProProcessDeathTest && !appforgeProRecoveryTest &&
     !appforgeProSecondDevice && !appforgePlayIntegrityAcceptance)) {
    "Isolated2 must not share any other acceptance identity"
}

val appforgeV9R2Isolated3Acceptance =
    providers.gradleProperty("appforgeV9R2Isolated3Acceptance").orNull == "true"
require(!appforgeV9R2Isolated3Acceptance ||
    (!appforgeV9R2Isolated2Acceptance &&
     !appforgeV9R2Issue78Acceptance &&
     !appforgeV9R2DeviceAcceptance &&
     !appforgeProProcessDeathTest &&
     !appforgeProRecoveryTest &&
     !appforgeProSecondDevice &&
     !appforgePlayIntegrityAcceptance)) {
    "Isolated3 must use its own test identity"
}

val appforgeV9R2Isolated3Upgrade =
    providers.gradleProperty("appforgeV9R2Isolated3Upgrade").orNull == "true"
require(!appforgeV9R2Isolated3Upgrade ||
    (appforgeV9R2Isolated3Acceptance && appforgeIntegrationDebugVersionCode == null)) {
    "In-place upgrade requires the original isolated3 debug identity only."
}

val appforgeV9R2Isolated3UpgradeV542 =
    providers.gradleProperty("appforgeV9R2Isolated3UpgradeV542").orNull == "true"
require(!appforgeV9R2Isolated3UpgradeV542 ||
    (appforgeV9R2Isolated3Acceptance && !appforgeV9R2Isolated3Upgrade &&
     appforgeIntegrationDebugVersionCode == null)) {
    "V542 in-place upgrade requires original isolated3 debug identity and an exclusive version flag."
}

val appforgeV9R2Isolated3UpgradeV543 =
    providers.gradleProperty("appforgeV9R2Isolated3UpgradeV543").orNull == "true"
require(!appforgeV9R2Isolated3UpgradeV543 ||
    (appforgeV9R2Isolated3Acceptance && !appforgeV9R2Isolated3Upgrade &&
     !appforgeV9R2Isolated3UpgradeV542 && appforgeIntegrationDebugVersionCode == null)) {
    "V543 in-place upgrade requires the original isolated3 test identity and exclusive version flag."
}

val appforgeV9R2Isolated3UpgradeV544 =
    providers.gradleProperty("appforgeV9R2Isolated3UpgradeV544").orNull == "true"
require(!appforgeV9R2Isolated3UpgradeV544 ||
    (appforgeV9R2Isolated3Acceptance && !appforgeV9R2Isolated3Upgrade &&
     !appforgeV9R2Isolated3UpgradeV542 && !appforgeV9R2Isolated3UpgradeV543 &&
     appforgeIntegrationDebugVersionCode == null)) {
    "V544 in-place upgrade requires the original isolated3 test identity and exclusive version flag."
}

val appforgeV9R2Isolated3UpgradeV545 =
    providers.gradleProperty("appforgeV9R2Isolated3UpgradeV545").orNull == "true"
require(!appforgeV9R2Isolated3UpgradeV545 ||
    (appforgeV9R2Isolated3Acceptance && !appforgeV9R2Isolated3Upgrade &&
     !appforgeV9R2Isolated3UpgradeV542 && !appforgeV9R2Isolated3UpgradeV543 &&
     !appforgeV9R2Isolated3UpgradeV544 && appforgeIntegrationDebugVersionCode == null)) {
    "V545 in-place upgrade requires the original isolated3 test identity and exclusive version flag."
}

val appforgeV9R2Isolated3UpgradeV546 =
    providers.gradleProperty("appforgeV9R2Isolated3UpgradeV546").orNull == "true"
require(!appforgeV9R2Isolated3UpgradeV546 ||
    (appforgeV9R2Isolated3Acceptance && !appforgeV9R2Isolated3Upgrade &&
     !appforgeV9R2Isolated3UpgradeV542 && !appforgeV9R2Isolated3UpgradeV543 &&
     !appforgeV9R2Isolated3UpgradeV544 && !appforgeV9R2Isolated3UpgradeV545 &&
     appforgeIntegrationDebugVersionCode == null)) {
    "V546 in-place upgrade requires the original isolated3 test identity and exclusive version flag."
}


// V547 R18: isolated3-only CI APK candidate, never changes default/release versions.
val appforgeV9R2Isolated3UpgradeV547 =
    providers.gradleProperty("appforgeV9R2Isolated3UpgradeV547").orNull == "true"
require(!appforgeV9R2Isolated3UpgradeV547 ||
    (appforgeV9R2Isolated3Acceptance && !appforgeV9R2Isolated3Upgrade &&
     !appforgeV9R2Isolated3UpgradeV542 && !appforgeV9R2Isolated3UpgradeV543 &&
     !appforgeV9R2Isolated3UpgradeV544 && !appforgeV9R2Isolated3UpgradeV545 &&
     !appforgeV9R2Isolated3UpgradeV546 && appforgeIntegrationDebugVersionCode == null)) {
    "V547 in-place candidate requires original isolated3 test identity and exclusive version flag."
}

// V548 R20: isolated3-only CI upgrade; never changes release/default variants.
val appforgeV9R2Isolated3UpgradeV548 =
    providers.gradleProperty("appforgeV9R2Isolated3UpgradeV548").orNull == "true"
require(!appforgeV9R2Isolated3UpgradeV548 ||
    (appforgeV9R2Isolated3Acceptance && !appforgeV9R2Isolated3Upgrade &&
     !appforgeV9R2Isolated3UpgradeV542 && !appforgeV9R2Isolated3UpgradeV543 &&
     !appforgeV9R2Isolated3UpgradeV544 && !appforgeV9R2Isolated3UpgradeV545 &&
     !appforgeV9R2Isolated3UpgradeV546 && !appforgeV9R2Isolated3UpgradeV547 &&
     appforgeIntegrationDebugVersionCode == null)) {
    "V548 in-place candidate requires original isolated3 identity and exclusive version flag."
}


// V549 R22: isolated3-only signed CI upgrade based on R21 DNS repair.
// Never changes the production identity, default variants, or existing V548 APK.
val appforgeV9R2Isolated3UpgradeV549 =
    providers.gradleProperty("appforgeV9R2Isolated3UpgradeV549").orNull == "true"
require(!appforgeV9R2Isolated3UpgradeV549 ||
    (appforgeV9R2Isolated3Acceptance && !appforgeV9R2Isolated3Upgrade &&
     !appforgeV9R2Isolated3UpgradeV542 && !appforgeV9R2Isolated3UpgradeV543 &&
     !appforgeV9R2Isolated3UpgradeV544 && !appforgeV9R2Isolated3UpgradeV545 &&
     !appforgeV9R2Isolated3UpgradeV546 && !appforgeV9R2Isolated3UpgradeV547 &&
     !appforgeV9R2Isolated3UpgradeV548 && appforgeIntegrationDebugVersionCode == null)) {
    "V549 in-place candidate requires original isolated3 identity and an exclusive version flag."
}

// V550 R25: signed, isolated3-only CI update from the V549 device installation.
// Original package, Android signer and all existing user data must remain unchanged.
val appforgeV9R2Isolated3UpgradeV550 =
    providers.gradleProperty("appforgeV9R2Isolated3UpgradeV550").orNull == "true"
require(!appforgeV9R2Isolated3UpgradeV550 ||
    (appforgeV9R2Isolated3Acceptance && !appforgeV9R2Isolated3Upgrade &&
     !appforgeV9R2Isolated3UpgradeV542 && !appforgeV9R2Isolated3UpgradeV543 &&
     !appforgeV9R2Isolated3UpgradeV544 && !appforgeV9R2Isolated3UpgradeV545 &&
     !appforgeV9R2Isolated3UpgradeV546 && !appforgeV9R2Isolated3UpgradeV547 &&
     !appforgeV9R2Isolated3UpgradeV548 && !appforgeV9R2Isolated3UpgradeV549 &&
     appforgeIntegrationDebugVersionCode == null)) {
    "V550 in-place candidate requires original isolated3 identity and exclusive debug version flag."
}

// V551 R27: CI-only signed update over V550; original package/signature/data preserved.
val appforgeV9R2Isolated3UpgradeV551 =
    providers.gradleProperty("appforgeV9R2Isolated3UpgradeV551").orNull == "true"
require(!appforgeV9R2Isolated3UpgradeV551 ||
    (appforgeV9R2Isolated3Acceptance && !appforgeV9R2Isolated3Upgrade &&
     !appforgeV9R2Isolated3UpgradeV542 && !appforgeV9R2Isolated3UpgradeV543 &&
     !appforgeV9R2Isolated3UpgradeV544 && !appforgeV9R2Isolated3UpgradeV545 &&
     !appforgeV9R2Isolated3UpgradeV546 && !appforgeV9R2Isolated3UpgradeV547 &&
     !appforgeV9R2Isolated3UpgradeV548 && !appforgeV9R2Isolated3UpgradeV549 &&
     !appforgeV9R2Isolated3UpgradeV550 && appforgeIntegrationDebugVersionCode == null)) {
    "V551 in-place candidate requires original isolated3 identity and exclusive version flag."
}

// V551 R29: authenticated upgrade from installed V551 R27 (code 551).
// Increment internal Android versionCode to 552; keep original package/signature/data.
val appforgeV9R2Isolated3UpgradeV551R29 =
    providers.gradleProperty("appforgeV9R2Isolated3UpgradeV551R29").orNull == "true"
require(!appforgeV9R2Isolated3UpgradeV551R29 ||
    (appforgeV9R2Isolated3Acceptance && !appforgeV9R2Isolated3Upgrade &&
     !appforgeV9R2Isolated3UpgradeV542 && !appforgeV9R2Isolated3UpgradeV543 &&
     !appforgeV9R2Isolated3UpgradeV544 && !appforgeV9R2Isolated3UpgradeV545 &&
     !appforgeV9R2Isolated3UpgradeV546 && !appforgeV9R2Isolated3UpgradeV547 &&
     !appforgeV9R2Isolated3UpgradeV548 && !appforgeV9R2Isolated3UpgradeV549 &&
     !appforgeV9R2Isolated3UpgradeV550 && !appforgeV9R2Isolated3UpgradeV551 &&
     appforgeIntegrationDebugVersionCode == null)) {
    "R29 update requires original isolated3 identity and exclusive debug version flag."
}

// V551 R30: signed, verifiable in-place upgrade from V551 R29 (versionCode 552).
// Never change applicationId, the original signer, user data, module caches or R2.
val appforgeV9R2Isolated3UpgradeV551R30 =
    providers.gradleProperty("appforgeV9R2Isolated3UpgradeV551R30").orNull == "true"
require(!appforgeV9R2Isolated3UpgradeV551R30 ||
    (appforgeV9R2Isolated3Acceptance && !appforgeV9R2Isolated3Upgrade &&
     !appforgeV9R2Isolated3UpgradeV542 && !appforgeV9R2Isolated3UpgradeV543 &&
     !appforgeV9R2Isolated3UpgradeV544 && !appforgeV9R2Isolated3UpgradeV545 &&
     !appforgeV9R2Isolated3UpgradeV546 && !appforgeV9R2Isolated3UpgradeV547 &&
     !appforgeV9R2Isolated3UpgradeV548 && !appforgeV9R2Isolated3UpgradeV549 &&
     !appforgeV9R2Isolated3UpgradeV550 && !appforgeV9R2Isolated3UpgradeV551 &&
     !appforgeV9R2Isolated3UpgradeV551R29 &&
     appforgeIntegrationDebugVersionCode == null)) {
    "R30 requires original isolated3 identity and exclusive debug version flag."
}

// R31 original identity is opt-in and may NEVER use an isolated package suffix.
// Do not publish unless the actual signing certificate matches the
// pinned Google Cloud Android OAuth SHA-1. CI enforces the certificate.
val appforgeR31OriginalIdentity =
    providers.gradleProperty("appforgeR31OriginalIdentity").orNull == "true"
// R32 only: in-place original-identity upgrade from versionCode 554.
// R31 remains 554 unless this explicitly selected flag is enabled.
val appforgeR32OriginalIdentityUpgrade =
    providers.gradleProperty("appforgeR32OriginalIdentityUpgrade").orNull == "true"
require(!appforgeR32OriginalIdentityUpgrade || appforgeR31OriginalIdentity) {
    "R32 original identity upgrade requires the existing R31 signing contract."
}
// R33 only: retain R31 signing identity, upgrade R32 code 555 -> 556.
val appforgeR33OriginalIdentityUpgrade =
    providers.gradleProperty("appforgeR33OriginalIdentityUpgrade").orNull == "true"
require(!appforgeR33OriginalIdentityUpgrade ||
    (appforgeR31OriginalIdentity && appforgeR32OriginalIdentityUpgrade)) {
    "R33 requires original identity and the explicit R32 upgrade contract."
}
// R34 only: 556 -> 557 with unchanged R31/R32/R33 signature identity.
val appforgeR34OriginalIdentityUpgrade =
    providers.gradleProperty("appforgeR34OriginalIdentityUpgrade").orNull == "true"
require(!appforgeR34OriginalIdentityUpgrade ||
    (appforgeR31OriginalIdentity && appforgeR32OriginalIdentityUpgrade &&
     appforgeR33OriginalIdentityUpgrade)) {
    "R34 requires original signing identity and R33 upgrade contract."
}
// R35: diagnostic-only in-place original-identity upgrade 557 -> 558.
val appforgeR35OriginalIdentityUpgrade =
    providers.gradleProperty("appforgeR35OriginalIdentityUpgrade").orNull == "true"
require(!appforgeR35OriginalIdentityUpgrade ||
    (appforgeR31OriginalIdentity && appforgeR32OriginalIdentityUpgrade &&
     appforgeR33OriginalIdentityUpgrade && appforgeR34OriginalIdentityUpgrade)) {
    "R35 requires R34 original signing identity contract."
}
// R36: original-identity upgrade 558 -> 559; only diagnostic-persistence fix.
val appforgeR36OriginalIdentityUpgrade =
    providers.gradleProperty("appforgeR36OriginalIdentityUpgrade").orNull == "true"
require(!appforgeR36OriginalIdentityUpgrade ||
    (appforgeR31OriginalIdentity && appforgeR32OriginalIdentityUpgrade &&
     appforgeR33OriginalIdentityUpgrade && appforgeR34OriginalIdentityUpgrade &&
     appforgeR35OriginalIdentityUpgrade)) {
    "R36 requires the full original R35 signing identity chain."
}
// R37: isolated CMake early-patch candidate; preserve original package/signing identity.
val appforgeR37OriginalIdentityUpgrade =
    providers.gradleProperty("appforgeR37OriginalIdentityUpgrade").orNull == "true"
require(!appforgeR37OriginalIdentityUpgrade ||
    (appforgeR31OriginalIdentity && appforgeR32OriginalIdentityUpgrade &&
     appforgeR33OriginalIdentityUpgrade && appforgeR34OriginalIdentityUpgrade &&
     appforgeR35OriginalIdentityUpgrade && appforgeR36OriginalIdentityUpgrade)) {
    "R37 requires the complete R36 original signing identity chain."
}
// R38: guest-only signed librhash CMake candidate; original identity chain intact.
val appforgeR38OriginalIdentityUpgrade =
    providers.gradleProperty("appforgeR38OriginalIdentityUpgrade").orNull == "true"
require(!appforgeR38OriginalIdentityUpgrade ||
    (appforgeR31OriginalIdentity && appforgeR32OriginalIdentityUpgrade &&
     appforgeR33OriginalIdentityUpgrade && appforgeR34OriginalIdentityUpgrade &&
     appforgeR35OriginalIdentityUpgrade && appforgeR36OriginalIdentityUpgrade &&
     appforgeR37OriginalIdentityUpgrade)) {
    "R38 requires the complete R37 original signing identity chain."
}
// R39 diagnostic only: preserve R38 functionality and original certificate chain.
val appforgeR39OriginalIdentityUpgrade =
    providers.gradleProperty("appforgeR39OriginalIdentityUpgrade").orNull == "true"
require(!appforgeR39OriginalIdentityUpgrade ||
    (appforgeR31OriginalIdentity && appforgeR32OriginalIdentityUpgrade &&
     appforgeR33OriginalIdentityUpgrade && appforgeR34OriginalIdentityUpgrade &&
     appforgeR35OriginalIdentityUpgrade && appforgeR36OriginalIdentityUpgrade &&
     appforgeR37OriginalIdentityUpgrade && appforgeR38OriginalIdentityUpgrade)) {
    "R39 requires full R38 original signing identity chain."
}
// R40: signed CMake shared-library closure; keep complete original identity chain.
val appforgeR40OriginalIdentityUpgrade =
    providers.gradleProperty("appforgeR40OriginalIdentityUpgrade").orNull == "true"
require(!appforgeR40OriginalIdentityUpgrade ||
    (appforgeR31OriginalIdentity && appforgeR32OriginalIdentityUpgrade &&
     appforgeR33OriginalIdentityUpgrade && appforgeR34OriginalIdentityUpgrade &&
     appforgeR35OriginalIdentityUpgrade && appforgeR36OriginalIdentityUpgrade &&
     appforgeR37OriginalIdentityUpgrade && appforgeR38OriginalIdentityUpgrade &&
     appforgeR39OriginalIdentityUpgrade)) {
    "R40 requires full R39 original signing identity chain."
}
// R41: signed guest clang source-read probe, preserving R40's CMake closure.
val appforgeR41OriginalIdentityUpgrade =
    providers.gradleProperty("appforgeR41OriginalIdentityUpgrade").orNull == "true"
require(!appforgeR41OriginalIdentityUpgrade ||
    (appforgeR31OriginalIdentity && appforgeR32OriginalIdentityUpgrade &&
     appforgeR33OriginalIdentityUpgrade && appforgeR34OriginalIdentityUpgrade &&
     appforgeR35OriginalIdentityUpgrade && appforgeR36OriginalIdentityUpgrade &&
     appforgeR37OriginalIdentityUpgrade && appforgeR38OriginalIdentityUpgrade &&
     appforgeR39OriginalIdentityUpgrade && appforgeR40OriginalIdentityUpgrade)) {
    "R41 requires the complete R40 original signing identity chain."
}
// R42: verified bounded Clang SONAME symlink closure; same original identity.
val appforgeR42OriginalIdentityUpgrade =
    providers.gradleProperty("appforgeR42OriginalIdentityUpgrade").orNull == "true"
require(!appforgeR42OriginalIdentityUpgrade ||
    (appforgeR31OriginalIdentity && appforgeR32OriginalIdentityUpgrade &&
     appforgeR33OriginalIdentityUpgrade && appforgeR34OriginalIdentityUpgrade &&
     appforgeR35OriginalIdentityUpgrade && appforgeR36OriginalIdentityUpgrade &&
     appforgeR37OriginalIdentityUpgrade && appforgeR38OriginalIdentityUpgrade &&
     appforgeR39OriginalIdentityUpgrade && appforgeR40OriginalIdentityUpgrade &&
     appforgeR41OriginalIdentityUpgrade)) {
    "R42 requires the full R41 original certificate identity chain."
}
// R43: guest-native Clang must locate the verified private LLD at link time.
val appforgeR43OriginalIdentityUpgrade =
    providers.gradleProperty("appforgeR43OriginalIdentityUpgrade").orNull == "true"
require(!appforgeR43OriginalIdentityUpgrade ||
    (appforgeR31OriginalIdentity && appforgeR32OriginalIdentityUpgrade &&
     appforgeR33OriginalIdentityUpgrade && appforgeR34OriginalIdentityUpgrade &&
     appforgeR35OriginalIdentityUpgrade && appforgeR36OriginalIdentityUpgrade &&
     appforgeR37OriginalIdentityUpgrade && appforgeR38OriginalIdentityUpgrade &&
     appforgeR39OriginalIdentityUpgrade && appforgeR40OriginalIdentityUpgrade &&
     appforgeR41OriginalIdentityUpgrade && appforgeR42OriginalIdentityUpgrade)) {
    "R43 requires full R42 original signing certificate identity chain."
}
// R44: signed Ubuntu guest LLD runtime; no installed data migration.
val appforgeR44OriginalIdentityUpgrade =
    providers.gradleProperty("appforgeR44OriginalIdentityUpgrade").orNull == "true"
require(!appforgeR44OriginalIdentityUpgrade ||
    (appforgeR31OriginalIdentity && appforgeR32OriginalIdentityUpgrade &&
     appforgeR33OriginalIdentityUpgrade && appforgeR34OriginalIdentityUpgrade &&
     appforgeR35OriginalIdentityUpgrade && appforgeR36OriginalIdentityUpgrade &&
     appforgeR37OriginalIdentityUpgrade && appforgeR38OriginalIdentityUpgrade &&
     appforgeR39OriginalIdentityUpgrade && appforgeR40OriginalIdentityUpgrade &&
     appforgeR41OriginalIdentityUpgrade && appforgeR42OriginalIdentityUpgrade &&
     appforgeR43OriginalIdentityUpgrade)) {
    "R44 requires the complete R43 original-identity upgrade chain."
}
// R45: use R44's verified guest LLD from the actual Android NDK linker.
val appforgeR45OriginalIdentityUpgrade =
    providers.gradleProperty("appforgeR45OriginalIdentityUpgrade").orNull == "true"
require(!appforgeR45OriginalIdentityUpgrade ||
    (appforgeR31OriginalIdentity && appforgeR32OriginalIdentityUpgrade &&
     appforgeR33OriginalIdentityUpgrade && appforgeR34OriginalIdentityUpgrade &&
     appforgeR35OriginalIdentityUpgrade && appforgeR36OriginalIdentityUpgrade &&
     appforgeR37OriginalIdentityUpgrade && appforgeR38OriginalIdentityUpgrade &&
     appforgeR39OriginalIdentityUpgrade && appforgeR40OriginalIdentityUpgrade &&
     appforgeR41OriginalIdentityUpgrade && appforgeR42OriginalIdentityUpgrade &&
     appforgeR43OriginalIdentityUpgrade && appforgeR44OriginalIdentityUpgrade)) {
    "R45 requires the complete R44 original-identity upgrade chain."
}
// R46: select byte-verified guest Ubuntu LLD via Clang --ld-path.
val appforgeR46OriginalIdentityUpgrade =
    providers.gradleProperty("appforgeR46OriginalIdentityUpgrade").orNull == "true"
require(!appforgeR46OriginalIdentityUpgrade ||
    (appforgeR31OriginalIdentity && appforgeR32OriginalIdentityUpgrade &&
     appforgeR33OriginalIdentityUpgrade && appforgeR34OriginalIdentityUpgrade &&
     appforgeR35OriginalIdentityUpgrade && appforgeR36OriginalIdentityUpgrade &&
     appforgeR37OriginalIdentityUpgrade && appforgeR38OriginalIdentityUpgrade &&
     appforgeR39OriginalIdentityUpgrade && appforgeR40OriginalIdentityUpgrade &&
     appforgeR41OriginalIdentityUpgrade && appforgeR42OriginalIdentityUpgrade &&
     appforgeR43OriginalIdentityUpgrade && appforgeR44OriginalIdentityUpgrade &&
     appforgeR45OriginalIdentityUpgrade)) {
    "R46 requires the complete R45 original-identity upgrade chain."
}
// R47: enable byte-authenticated guest Clang C++ driver for real source paths.
val appforgeR47OriginalIdentityUpgrade =
    providers.gradleProperty("appforgeR47OriginalIdentityUpgrade").orNull == "true"
require(!appforgeR47OriginalIdentityUpgrade ||
    (appforgeR31OriginalIdentity && appforgeR32OriginalIdentityUpgrade &&
     appforgeR33OriginalIdentityUpgrade && appforgeR34OriginalIdentityUpgrade &&
     appforgeR35OriginalIdentityUpgrade && appforgeR36OriginalIdentityUpgrade &&
     appforgeR37OriginalIdentityUpgrade && appforgeR38OriginalIdentityUpgrade &&
     appforgeR39OriginalIdentityUpgrade && appforgeR40OriginalIdentityUpgrade &&
     appforgeR41OriginalIdentityUpgrade && appforgeR42OriginalIdentityUpgrade &&
     appforgeR43OriginalIdentityUpgrade && appforgeR44OriginalIdentityUpgrade &&
     appforgeR45OriginalIdentityUpgrade && appforgeR46OriginalIdentityUpgrade)) {
    "R47 requires the complete R46 original-identity upgrade chain."
}
// R48: byte-verified native guest llvm-ar in the translated PRoot namespace.
val appforgeR48OriginalIdentityUpgrade =
    providers.gradleProperty("appforgeR48OriginalIdentityUpgrade").orNull == "true"
require(!appforgeR48OriginalIdentityUpgrade ||
    (appforgeR31OriginalIdentity && appforgeR32OriginalIdentityUpgrade &&
     appforgeR33OriginalIdentityUpgrade && appforgeR34OriginalIdentityUpgrade &&
     appforgeR35OriginalIdentityUpgrade && appforgeR36OriginalIdentityUpgrade &&
     appforgeR37OriginalIdentityUpgrade && appforgeR38OriginalIdentityUpgrade &&
     appforgeR39OriginalIdentityUpgrade && appforgeR40OriginalIdentityUpgrade &&
     appforgeR41OriginalIdentityUpgrade && appforgeR42OriginalIdentityUpgrade &&
     appforgeR43OriginalIdentityUpgrade && appforgeR44OriginalIdentityUpgrade &&
     appforgeR45OriginalIdentityUpgrade && appforgeR46OriginalIdentityUpgrade &&
     appforgeR47OriginalIdentityUpgrade)) {
    "R48 requires the complete R47 original-identity upgrade chain."
}
// R49: discover only authenticated AppForge NDK ARM64 API26 linker.
val appforgeR49OriginalIdentityUpgrade =
    providers.gradleProperty("appforgeR49OriginalIdentityUpgrade").orNull == "true"
require(!appforgeR49OriginalIdentityUpgrade ||
    (appforgeR31OriginalIdentity && appforgeR32OriginalIdentityUpgrade &&
     appforgeR33OriginalIdentityUpgrade && appforgeR34OriginalIdentityUpgrade &&
     appforgeR35OriginalIdentityUpgrade && appforgeR36OriginalIdentityUpgrade &&
     appforgeR37OriginalIdentityUpgrade && appforgeR38OriginalIdentityUpgrade &&
     appforgeR39OriginalIdentityUpgrade && appforgeR40OriginalIdentityUpgrade &&
     appforgeR41OriginalIdentityUpgrade && appforgeR42OriginalIdentityUpgrade &&
     appforgeR43OriginalIdentityUpgrade && appforgeR44OriginalIdentityUpgrade &&
     appforgeR45OriginalIdentityUpgrade && appforgeR46OriginalIdentityUpgrade &&
     appforgeR47OriginalIdentityUpgrade && appforgeR48OriginalIdentityUpgrade)) {
    "R49 requires the complete R48 original-identity upgrade chain."
}
// R50: private Android API26 sysroot linker for real Rust JNI validation.
val appforgeR50OriginalIdentityUpgrade =
    providers.gradleProperty("appforgeR50OriginalIdentityUpgrade").orNull == "true"
require(!appforgeR50OriginalIdentityUpgrade ||
    (appforgeR31OriginalIdentity && appforgeR32OriginalIdentityUpgrade &&
     appforgeR33OriginalIdentityUpgrade && appforgeR34OriginalIdentityUpgrade &&
     appforgeR35OriginalIdentityUpgrade && appforgeR36OriginalIdentityUpgrade &&
     appforgeR37OriginalIdentityUpgrade && appforgeR38OriginalIdentityUpgrade &&
     appforgeR39OriginalIdentityUpgrade && appforgeR40OriginalIdentityUpgrade &&
     appforgeR41OriginalIdentityUpgrade && appforgeR42OriginalIdentityUpgrade &&
     appforgeR43OriginalIdentityUpgrade && appforgeR44OriginalIdentityUpgrade &&
     appforgeR45OriginalIdentityUpgrade && appforgeR46OriginalIdentityUpgrade &&
     appforgeR47OriginalIdentityUpgrade && appforgeR48OriginalIdentityUpgrade &&
     appforgeR49OriginalIdentityUpgrade)) {
    "R50 requires the complete R49 original-identity upgrade chain."
}
// R51: verify API26 startup objects and Bionic link libraries for Rust.
val appforgeR51OriginalIdentityUpgrade =
    providers.gradleProperty("appforgeR51OriginalIdentityUpgrade").orNull == "true"
require(!appforgeR51OriginalIdentityUpgrade ||
    (appforgeR31OriginalIdentity && appforgeR32OriginalIdentityUpgrade &&
     appforgeR33OriginalIdentityUpgrade && appforgeR34OriginalIdentityUpgrade &&
     appforgeR35OriginalIdentityUpgrade && appforgeR36OriginalIdentityUpgrade &&
     appforgeR37OriginalIdentityUpgrade && appforgeR38OriginalIdentityUpgrade &&
     appforgeR39OriginalIdentityUpgrade && appforgeR40OriginalIdentityUpgrade &&
     appforgeR41OriginalIdentityUpgrade && appforgeR42OriginalIdentityUpgrade &&
     appforgeR43OriginalIdentityUpgrade && appforgeR44OriginalIdentityUpgrade &&
     appforgeR45OriginalIdentityUpgrade && appforgeR46OriginalIdentityUpgrade &&
     appforgeR47OriginalIdentityUpgrade && appforgeR48OriginalIdentityUpgrade &&
     appforgeR49OriginalIdentityUpgrade && appforgeR50OriginalIdentityUpgrade)) {
    "R51 requires the complete R50 original-identity upgrade chain."
}
// R52: original-identity upgrade gated on the complete R51 chain.
val appforgeR52OriginalIdentityUpgrade =
    providers.gradleProperty("appforgeR52OriginalIdentityUpgrade").orNull == "true"
require(!appforgeR52OriginalIdentityUpgrade ||
    (appforgeR31OriginalIdentity && appforgeR32OriginalIdentityUpgrade &&
     appforgeR33OriginalIdentityUpgrade && appforgeR34OriginalIdentityUpgrade &&
     appforgeR35OriginalIdentityUpgrade && appforgeR36OriginalIdentityUpgrade &&
     appforgeR37OriginalIdentityUpgrade && appforgeR38OriginalIdentityUpgrade &&
     appforgeR39OriginalIdentityUpgrade && appforgeR40OriginalIdentityUpgrade &&
     appforgeR41OriginalIdentityUpgrade && appforgeR42OriginalIdentityUpgrade &&
     appforgeR43OriginalIdentityUpgrade && appforgeR44OriginalIdentityUpgrade &&
     appforgeR45OriginalIdentityUpgrade && appforgeR46OriginalIdentityUpgrade &&
     appforgeR47OriginalIdentityUpgrade && appforgeR48OriginalIdentityUpgrade &&
     appforgeR49OriginalIdentityUpgrade && appforgeR50OriginalIdentityUpgrade &&
     appforgeR51OriginalIdentityUpgrade)) {
    "R52 requires the complete R51 original-identity upgrade chain."
}
// R53: diagnostics-only original identity upgrade, retaining the R52 toolchain.
val appforgeR53OriginalIdentityUpgrade =
    providers.gradleProperty("appforgeR53OriginalIdentityUpgrade").orNull == "true"
require(!appforgeR53OriginalIdentityUpgrade ||
    (appforgeR31OriginalIdentity && appforgeR32OriginalIdentityUpgrade &&
     appforgeR33OriginalIdentityUpgrade && appforgeR34OriginalIdentityUpgrade &&
     appforgeR35OriginalIdentityUpgrade && appforgeR36OriginalIdentityUpgrade &&
     appforgeR37OriginalIdentityUpgrade && appforgeR38OriginalIdentityUpgrade &&
     appforgeR39OriginalIdentityUpgrade && appforgeR40OriginalIdentityUpgrade &&
     appforgeR41OriginalIdentityUpgrade && appforgeR42OriginalIdentityUpgrade &&
     appforgeR43OriginalIdentityUpgrade && appforgeR44OriginalIdentityUpgrade &&
     appforgeR45OriginalIdentityUpgrade && appforgeR46OriginalIdentityUpgrade &&
     appforgeR47OriginalIdentityUpgrade && appforgeR48OriginalIdentityUpgrade &&
     appforgeR49OriginalIdentityUpgrade && appforgeR50OriginalIdentityUpgrade &&
     appforgeR51OriginalIdentityUpgrade && appforgeR52OriginalIdentityUpgrade)) {
    "R53 requires the complete R52 original-identity upgrade chain."
}
// R54: retain the R53 original-identity chain while making bounded shell
// diagnostics include the end of long native toolchain output.
val appforgeR54OriginalIdentityUpgrade =
    providers.gradleProperty("appforgeR54OriginalIdentityUpgrade").orNull == "true"
require(!appforgeR54OriginalIdentityUpgrade ||
    (appforgeR31OriginalIdentity && appforgeR32OriginalIdentityUpgrade &&
     appforgeR33OriginalIdentityUpgrade && appforgeR34OriginalIdentityUpgrade &&
     appforgeR35OriginalIdentityUpgrade && appforgeR36OriginalIdentityUpgrade &&
     appforgeR37OriginalIdentityUpgrade && appforgeR38OriginalIdentityUpgrade &&
     appforgeR39OriginalIdentityUpgrade && appforgeR40OriginalIdentityUpgrade &&
     appforgeR41OriginalIdentityUpgrade && appforgeR42OriginalIdentityUpgrade &&
     appforgeR43OriginalIdentityUpgrade && appforgeR44OriginalIdentityUpgrade &&
     appforgeR45OriginalIdentityUpgrade && appforgeR46OriginalIdentityUpgrade &&
     appforgeR47OriginalIdentityUpgrade && appforgeR48OriginalIdentityUpgrade &&
     appforgeR49OriginalIdentityUpgrade && appforgeR50OriginalIdentityUpgrade &&
     appforgeR51OriginalIdentityUpgrade && appforgeR52OriginalIdentityUpgrade &&
     appforgeR53OriginalIdentityUpgrade)) {
    "R54 requires the complete R53 original-identity upgrade chain."
}
// R31 build server may package only an UNSIGNED candidate. The PKCS12 signer
// remains on the device and must be used before any installation.
val appforgeR31UnsignedCI =
    providers.gradleProperty("appforgeR31UnsignedCI").orNull == "true"
require(!appforgeR31UnsignedCI ||
    (appforgeR31OriginalIdentity &&
     System.getenv("GITHUB_ACTIONS") == "true" &&
     System.getenv("GITHUB_REPOSITORY") == "HACKMASTER-TR/AppForge-Studio")) {
    "R31_UNSIGNED_CI_REQUIRES_TRUSTED_GITHUB_ORIGINAL_IDENTITY"
}
require(!appforgeR31OriginalIdentity ||
    (!appforgeV9R2Isolated3Acceptance && !appforgeV9R2Isolated2Acceptance &&
     !appforgeV9R2Issue78Acceptance && !appforgeV9R2DeviceAcceptance &&
     !appforgeProRecoveryTest && !appforgeProSecondDevice &&
     !appforgeProProcessDeathTest && !appforgePlayIntegrityAcceptance &&
     !appforgeV9R2Isolated3UpgradeV551R30 &&
     appforgeIntegrationDebugVersionCode == null)) {
    "R31 original package cannot combine with isolated/suffixed test identities."
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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    // R31-only signer. The existing release JKS remains untouched.
    // R31 accepts only the dedicated PKCS12 key whose SHA-1 is checked by CI.
    val r31StorePath = System.getenv("APPFORGE_R31_KEYSTORE_PATH")
    val r31StorePassword = System.getenv("APPFORGE_R31_STORE_PASSWORD")
    val r31KeyPassword = System.getenv("APPFORGE_R31_KEY_PASSWORD")
        ?: r31StorePassword.orEmpty()
    val r31OriginalSigning =
        if (!r31StorePath.isNullOrBlank() && !r31StorePassword.isNullOrBlank()) {
            signingConfigs.create("r31Original") {
                storeFile = file(requireNotNull(r31StorePath))
                storePassword = r31StorePassword.orEmpty()
                keyAlias = "appforge-debug"
                keyPassword = r31KeyPassword
                storeType = "PKCS12"
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
            if (appforgeR31OriginalIdentity) {
                // Only the R31 PKCS12 signer is permitted for the original OAuth ID.
                // Never fall back to the unrelated release key or generated debug key.
                if (appforgeR31UnsignedCI) {
                    // Verified unsigned artifact only; not installable until local signing.
                    signingConfig = null
                } else {
                    signingConfig = requireNotNull(r31OriginalSigning) {
                        "R31_ORIGINAL_PKCS12_SIGNER_REQUIRED"
                    }
                }
            } else {
                ciDebugSigning?.let { signingConfig = it }
            }
            if (appforgeV9R2Isolated3Acceptance) {
                applicationIdSuffix = ".v9r2isolated3"
            } else if (appforgeV9R2Isolated2Acceptance) {
                applicationIdSuffix = ".v9r2isolated2"
            } else if (appforgeV9R2Issue78Acceptance) {
                applicationIdSuffix = ".v9r2issue78"
            } else if (appforgeV9R2DeviceAcceptance) {
                applicationIdSuffix = ".v9r2accept"
            } else if (appforgeProRecoveryTest) {
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

// Guest-JDK observer only: this class is never loaded by Android or packaged in the HTML shell.
val compileSdkNetworkObserver = tasks.register<JavaCompile>("compileSdkNetworkObserver") {
    source(fileTree("src/main/assets/device-build/jvm-telemetry") { include("*.java") })
    classpath = files()
    options.release.set(17)
    options.encoding = "UTF-8"
    destinationDirectory.set(layout.buildDirectory.dir("jvm-network-observer/classes"))
}
abstract class SdkNetworkObserverPackageTask : Jar() {
    @get:OutputDirectory abstract val assetsDirectory: DirectoryProperty
}
val packageSdkNetworkObserver = tasks.register<SdkNetworkObserverPackageTask>("packageSdkNetworkObserver") {
    dependsOn(compileSdkNetworkObserver)
    from(compileSdkNetworkObserver.map { it.destinationDirectory.get().asFile })
    archiveFileName.set("network-byte-agent.jar")
    assetsDirectory.set(layout.buildDirectory.dir("generated/jvm-transfer/assets"))
    destinationDirectory.set(assetsDirectory.dir("device-build/jvm-telemetry"))
    manifest { attributes(mapOf("Premain-Class" to "com.appforge.runtime.NetworkByteAgent")) }
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
androidComponents.onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(packageSdkNetworkObserver) { it.assetsDirectory }
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
        if (appforgeR31OriginalIdentity) {
            variant.outputs.forEach { output ->
                if (appforgeR54OriginalIdentityUpgrade) {
                    output.versionCode.set(577)
                    output.versionName.set("5.0.75-r54-shell-tail")
                } else if (appforgeR53OriginalIdentityUpgrade) {
                    output.versionCode.set(576)
                    output.versionName.set("5.0.74-r53-rust-link-trace")
                } else if (appforgeR52OriginalIdentityUpgrade) {
                    output.versionCode.set(575)
                    output.versionName.set("5.0.73-r52-crt-absolute")
                } else if (appforgeR51OriginalIdentityUpgrade) {
                    output.versionCode.set(574)
                    output.versionName.set("5.0.72-r51-rust-crt")
                } else if (appforgeR50OriginalIdentityUpgrade) {
                    output.versionCode.set(573)
                    output.versionName.set("5.0.71-r50-rust-sysroot")
                } else if (appforgeR49OriginalIdentityUpgrade) {
                    output.versionCode.set(572)
                    output.versionName.set("5.0.70-r49-rust-linker")
                } else if (appforgeR48OriginalIdentityUpgrade) {
                    output.versionCode.set(571)
                    output.versionName.set("5.0.69-r48-guest-llvm-ar")
                } else if (appforgeR47OriginalIdentityUpgrade) {
                    output.versionCode.set(570)
                    output.versionName.set("5.0.68-r47-guest-clangxx")
                } else if (appforgeR46OriginalIdentityUpgrade) {
                    output.versionCode.set(569)
                    output.versionName.set("5.0.67-r46-guest-ld-path")
                } else if (appforgeR45OriginalIdentityUpgrade) {
                    output.versionCode.set(568)
                    output.versionName.set("5.0.66-r45-ndk-lld-direct")
                } else if (appforgeR44OriginalIdentityUpgrade) {
                    output.versionCode.set(567)
                    output.versionName.set("5.0.65-r44-guest-lld")
                } else if (appforgeR43OriginalIdentityUpgrade) {
                    output.versionCode.set(566)
                    output.versionName.set("5.0.64-r43-ndk-lld-search")
                } else if (appforgeR42OriginalIdentityUpgrade) {
                    output.versionCode.set(565)
                    output.versionName.set("5.0.63-r42-guest-clang-links")
                } else if (appforgeR41OriginalIdentityUpgrade) {
                    output.versionCode.set(564)
                    output.versionName.set("5.0.62-r41-guest-clang")
                } else if (appforgeR40OriginalIdentityUpgrade) {
                    output.versionCode.set(563)
                    output.versionName.set("5.0.61-r40-guest-cmake-closure")
                } else if (appforgeR39OriginalIdentityUpgrade) {
                    output.versionCode.set(562)
                    output.versionName.set("5.0.60-r39-comparator-trace")
                } else if (appforgeR38OriginalIdentityUpgrade) {
                    output.versionCode.set(561)
                    output.versionName.set("5.0.59-r38-guest-rhash")
                } else if (appforgeR37OriginalIdentityUpgrade) {
                    output.versionCode.set(560)
                    output.versionName.set("5.0.58-r37-cmake-early-patch")
                } else if (appforgeR36OriginalIdentityUpgrade) {
                    output.versionCode.set(559)
                    output.versionName.set("5.0.57-r36-diagnostic-gate")
                } else if (appforgeR35OriginalIdentityUpgrade) {
                    output.versionCode.set(558)
                    output.versionName.set("5.0.56-r35-diagnostic-original-id")
                } else if (appforgeR34OriginalIdentityUpgrade) {
                    output.versionCode.set(557)
                    output.versionName.set("5.0.55-r34-original-id-test")
                } else if (appforgeR33OriginalIdentityUpgrade) {
                    output.versionCode.set(556)
                    output.versionName.set("5.0.54-r33-original-id-test")
                } else if (appforgeR32OriginalIdentityUpgrade) {
                    output.versionCode.set(555)
                    output.versionName.set("5.0.53-r32-original-id-test")
                } else {
                    output.versionCode.set(554)
                    output.versionName.set("5.0.52-r31-original-id-test")
                }
            }
        }
        if (appforgeV9R2Isolated3UpgradeV551R30) {
            variant.outputs.forEach { output ->
                output.versionCode.set(553)
                output.versionName.set("5.0.51-r30-isolated3-upgrade")
            }
        }
        if (appforgeV9R2Isolated3UpgradeV551R29) {
            variant.outputs.forEach { output ->
                output.versionCode.set(552)
                output.versionName.set("5.0.51-r29-isolated3-upgrade")
            }
        }
        if (appforgeV9R2Isolated3UpgradeV551) {
            variant.outputs.forEach { output ->
                output.versionCode.set(551)
                output.versionName.set("5.0.51-isolated3-upgrade")
            }
        }
        if (appforgeV9R2Isolated3UpgradeV550) {
            variant.outputs.forEach { output ->
                output.versionCode.set(550)
                output.versionName.set("5.0.50-isolated3-upgrade")
            }
        }
        if (appforgeV9R2Isolated3UpgradeV549) {
            variant.outputs.forEach { output ->
                output.versionCode.set(549)
                output.versionName.set("5.0.49-isolated3-upgrade")
            }
        }
        if (appforgeV9R2Isolated3UpgradeV548) {
            variant.outputs.forEach { output ->
                output.versionCode.set(548)
                output.versionName.set("5.0.48-isolated3-upgrade")
            }
        }
        if (appforgeV9R2Isolated3UpgradeV547) {
            variant.outputs.forEach { output ->
                output.versionCode.set(547)
                output.versionName.set("5.0.47-isolated3-upgrade")
            }
        }
        if (appforgeV9R2Isolated3UpgradeV546) {
            variant.outputs.forEach { output ->
                output.versionCode.set(546)
                output.versionName.set("5.0.46-isolated3-upgrade")
            }
        }
        if (appforgeV9R2Isolated3UpgradeV545) {
            variant.outputs.forEach { output ->
                output.versionCode.set(545)
                output.versionName.set("5.0.45-isolated3-upgrade")
            }
        }
        if (appforgeV9R2Isolated3UpgradeV544) {
            variant.outputs.forEach { output ->
                output.versionCode.set(544)
                output.versionName.set("5.0.44-isolated3-upgrade")
            }
        }
        if (appforgeV9R2Isolated3UpgradeV543) {
            variant.outputs.forEach { output ->
                output.versionCode.set(543)
                output.versionName.set("5.0.43-isolated3-upgrade")
            }
        }
        if (appforgeV9R2Isolated3UpgradeV542) {
            variant.outputs.forEach { output ->
                output.versionCode.set(542)
                output.versionName.set("5.0.42-isolated3-upgrade")
            }
        }
        if (appforgeV9R2Isolated3Upgrade) {
            variant.outputs.forEach { output ->
                output.versionCode.set(541)
                output.versionName.set("5.0.41-isolated3-upgrade")
            }
        }
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
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")


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

// Real bundled decoder acceptance, separate from Android camera hardware testing.
tasks.matching { it.name == "testDebugUnitTest" }.configureEach {
    dependsOn(":html-apk-template:testDebugUnitTest")
}
