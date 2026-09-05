package com.appforge.studio.terminal

import android.content.Context
import android.os.Build
import android.net.ConnectivityManager
import java.io.File
import java.security.MessageDigest
import java.nio.file.Files
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal enum class LinuxDevelopmentStage {
    CHECKING,
    ROOTFS,
    TOOLCHAIN,
    READY
}

internal data class LinuxDevelopmentProgress(
    val stage: LinuxDevelopmentStage,
    val percent: Int? = null,
    val detail: String
)

internal class AndroidLinuxRuntimeManager(
    context: Context
) {
    private val appContext =
        context.applicationContext

    private val linuxBaseDirectory =
        File(
            appContext.noBackupFilesDir,
            "terminal/linux"
        )

    private val packagedLinuxEngine =
        PackagedLinuxEngine(
            appContext
        )

    fun inspect(
        distribution: LinuxDistribution
    ): LinuxRuntimeStatus {
        val architecture =
            LinuxArchitecture.fromAndroidAbis(
                Build.SUPPORTED_ABIS.toList()
            )

        if (architecture == null) {
            return LinuxRuntimeStatus(
                distribution = distribution,
                architecture = null,
                state = LinuxRuntimeState.UNSUPPORTED_ABI,
                engineBundled = false,
                rootfsInstalled = false,
                rootfsTrusted = false,
                detail =
                    "Bu Android ABI'si AppForge Linux için henüz desteklenmiyor."
            )
        }

        val layout =
            layout(
                distribution,
                architecture
            )

        val engineBundled =
            packagedLinuxEngine
                .inspect()
                .status ==
                PackagedLinuxEngineStatus.READY

        val rootfsInstalled =
            rootfsLooksInstalled(
                layout.rootfsDirectory
            )

        val pinnedManifest =
            LinuxRuntimeManifestRegistry.find(
                distribution,
                architecture
            )

        val metadata =
            LinuxRootfsMetadataCodec.read(
                layout.metadataFile
            )

        val rootfsTrusted =
            pinnedManifest != null &&
                metadata != null &&
                metadata.distribution == distribution &&
                metadata.architecture == architecture &&
                metadata.release == pinnedManifest.release &&
                constantTimeDigestEquals(
                    metadata.archiveSha256,
                    pinnedManifest.archiveSha256
                )

        val state =
            when {
                !engineBundled ->
                    LinuxRuntimeState.ENGINE_NOT_BUNDLED

                !rootfsInstalled ->
                    LinuxRuntimeState.ROOTFS_NOT_INSTALLED

                !rootfsTrusted ->
                    LinuxRuntimeState.ROOTFS_UNTRUSTED

                else ->
                    LinuxRuntimeState.READY
            }

        return LinuxRuntimeStatus(
            distribution = distribution,
            architecture = architecture,
            state = state,
            engineBundled = engineBundled,
            rootfsInstalled = rootfsInstalled,
            rootfsTrusted = rootfsTrusted,
            detail = detailFor(
                state,
                pinnedManifest == null
            )
        )
    }

    fun layout(
        distribution: LinuxDistribution,
        architecture: LinuxArchitecture
    ) =
        LinuxRuntimeLayout(
            baseDirectory = linuxBaseDirectory,
            distribution = distribution,
            architecture = architecture
        )


    fun installableManifest(
        distribution: LinuxDistribution
    ): LinuxRootfsManifest? {
        val architecture =
            LinuxArchitecture.fromAndroidAbis(
                Build.SUPPORTED_ABIS.toList()
            )
                ?: return null

        return LinuxRuntimeManifestRegistry.find(
            distribution,
            architecture
        )
    }

    suspend fun installVerifiedRootfs(
        distribution: LinuxDistribution,
        onProgress: (LinuxInstallProgress) -> Unit = {}
    ): LinuxRuntimeStatus {
        val architecture =
            LinuxArchitecture.fromAndroidAbis(
                Build.SUPPORTED_ABIS.toList()
            )
                ?: error(
                    "Bu Android ABI'si AppForge Linux için desteklenmiyor."
                )

        val manifest =
            LinuxRuntimeManifestRegistry.find(
                distribution,
                architecture
            )
                ?: error(
                    "Bu dağıtım/mimari için doğrulanmış rootfs manifesti henüz yok."
                )

        VerifiedLinuxRootfsInstaller(
            appContext
        ).install(
            manifest = manifest,
            layout =
                layout(
                    distribution,
                    architecture
                ),
            onProgress = onProgress
        )

        return inspect(distribution)
    }

    fun toolchainCommand(
        selected: Collection<LinuxToolchainId>
    ): String =
        LinuxToolchainCatalog.installCommand(
            selected
        )

    fun requireReadyRootfs(
        distribution: LinuxDistribution
    ): File {
        val status =
            inspect(
                distribution
            )

        check(status.ready) {
            status.detail
        }

        val architecture =
            requireNotNull(
                status.architecture
            )

        val rootfs =
            layout(
                distribution,
                architecture
            ).rootfsDirectory
                .canonicalFile

        check(
            rootfsLooksInstalled(
                rootfs
            )
        ) {
            "Linux rootfs hazır değil."
        }

        return rootfs
    }

    suspend fun ensureBaseEnvironment(
        distribution: LinuxDistribution = LinuxDistribution.UBUNTU,
        onProgress: (LinuxDevelopmentProgress) -> Unit = {}
    ): LinuxRuntimeStatus =
        baseEnvironmentMutex.withLock {
            onProgress(
                LinuxDevelopmentProgress(
                    stage = LinuxDevelopmentStage.CHECKING,
                    detail = "Geliştirme ortamı kontrol ediliyor…"
                )
            )

            var status =
                inspect(distribution)

            if (!status.ready) {
                status =
                    installVerifiedRootfs(
                        distribution
                    ) { progress ->
                        onProgress(
                            LinuxDevelopmentProgress(
                                stage = LinuxDevelopmentStage.ROOTFS,
                                percent = progress.percent,
                                detail =
                                    when (progress.stage) {
                                        LinuxInstallStage.PREPARING ->
                                            "Geliştirme ortamı hazırlanıyor…"

                                        LinuxInstallStage.DOWNLOADING ->
                                            "Geliştirme ortamı indiriliyor…"

                                        LinuxInstallStage.VERIFYING ->
                                            "İndirilen ortam doğrulanıyor…"

                                        LinuxInstallStage.EXTRACTING ->
                                            "Geliştirme ortamı kuruluyor…"

                                        LinuxInstallStage.FINALIZING ->
                                            "Kurulum tamamlanıyor…"

                                        LinuxInstallStage.COMPLETE ->
                                            "Temel geliştirme ortamı hazır."
                                    }
                            )
                        )
                    }
            }

            check(status.ready) {
                status.detail
            }

            configureResolver(
                requireReadyRootfs(
                    distribution
                )
            )

            onProgress(
                LinuxDevelopmentProgress(
                    stage = LinuxDevelopmentStage.READY,
                    percent = 100,
                    detail = "Terminal hazır."
                )
            )

            inspect(distribution)
        }

    suspend fun ensureDevelopmentTools(
        distribution: LinuxDistribution = LinuxDistribution.UBUNTU,
        workspace: File,
        onProgress: (LinuxDevelopmentProgress) -> Unit = {}
    ): Boolean =
        developmentToolsMutex.withLock {
            if (developmentProfileReady(distribution)) {
                return@withLock true
            }

            val rootfs =
                requireReadyRootfs(
                    distribution
                )

            configureResolver(rootfs)

            onProgress(
                LinuxDevelopmentProgress(
                    stage = LinuxDevelopmentStage.TOOLCHAIN,
                    detail = "Geliştirme araçları kuruluyor…"
                )
            )

            val shellEngine =
                LinuxShellEngine(
                    appContext
                )

            val primaryResult =
                shellEngine.execute(
                    sessionId =
                        "appforge-terminal-dev-tools",
                    rootfs = rootfs,
                    workspace = workspace,
                    command =
                        LinuxToolchainCatalog
                            .developmentProfileCommand(),
                    confirmed = true,
                    timeoutMs = 1_800_000L
                )

            /*
             * One optional package must not leave npm/java/editor missing.
             * If the complete suite fails, retry the essential standalone
             * workstation profile without the optional Android CLI group.
             */
            val finalResult =
                if (
                    !primaryResult.timedOut &&
                    primaryResult.exitCode == 0
                ) {
                    primaryResult
                } else {
                    onProgress(
                        LinuxDevelopmentProgress(
                            stage =
                                LinuxDevelopmentStage.TOOLCHAIN,
                            detail =
                                "Temel geliştirme araçları onarılıyor…"
                        )
                    )

                    shellEngine.execute(
                        sessionId =
                            "appforge-terminal-dev-tools-recovery",
                        rootfs = rootfs,
                        workspace = workspace,
                        command =
                            LinuxToolchainCatalog
                                .standaloneRecoveryCommand(),
                        confirmed = true,
                        timeoutMs = 1_800_000L
                    )
                }

            check(!finalResult.timedOut) {
                "Geliştirme araçlarının kurulumu zaman aşımına uğradı."
            }

            check(finalResult.exitCode == 0) {
                buildString {
                    append(
                        "Geliştirme araçları kurulamadı."
                    )

                    primaryResult.output
                        .takeLast(1_000)
                        .takeIf {
                            it.isNotBlank()
                        }
                        ?.let {
                            append("\nİlk deneme:\n")
                            append(it)
                        }

                    finalResult.output
                        .takeLast(1_500)
                        .takeIf {
                            it.isNotBlank()
                        }
                        ?.let {
                            append("\nOnarım denemesi:\n")
                            append(it)
                        }
                }
            }

            writeDevelopmentProfileMarker(
                rootfs
            )

            true
        }

    suspend fun ensureDevelopmentEnvironment(
        distribution: LinuxDistribution = LinuxDistribution.UBUNTU,
        workspace: File,
        onProgress: (LinuxDevelopmentProgress) -> Unit = {}
    ): LinuxRuntimeStatus =
        developmentEnvironmentMutex.withLock {
            val status =
                ensureBaseEnvironment(
                    distribution = distribution,
                    onProgress = onProgress
                )

            ensureDevelopmentTools(
                distribution = distribution,
                workspace = workspace,
                onProgress = onProgress
            )

            status
        }

    fun developmentProfileReady(
        distribution: LinuxDistribution =
            LinuxDistribution.UBUNTU
    ): Boolean {
        val status =
            inspect(distribution)

        if (!status.ready) {
            return false
        }

        val architecture =
            status.architecture
                ?: return false

        val rootfs =
            layout(
                distribution,
                architecture
            ).rootfsDirectory

        val marker =
            File(
                rootfs,
                DEV_SUITE_MARKER
            )

        return marker.isFile &&
            marker.length() <= 128L &&
            runCatching {
                marker
                    .readText(Charsets.UTF_8)
                    .trim() ==
                    DEV_SUITE_REVISION
            }.getOrDefault(false)
    }

    private fun writeDevelopmentProfileMarker(
        rootfs: File
    ) {
        val marker =
            File(
                rootfs,
                DEV_SUITE_MARKER
            )

        marker.parentFile?.mkdirs()
        marker.writeText(
            DEV_SUITE_REVISION + "\n",
            Charsets.UTF_8
        )
    }

    private fun configureResolver(
        rootfs: File
    ) {
        val connectivity =
            appContext.getSystemService(
                ConnectivityManager::class.java
            ) ?: return

        val activeNetwork =
            connectivity.activeNetwork
                ?: return

        val dnsServers =
            connectivity
                .getLinkProperties(
                    activeNetwork
                )
                ?.dnsServers
                .orEmpty()
                .mapNotNull {
                    it.hostAddress
                        ?.takeIf { address ->
                            address.isNotBlank()
                        }
                }
                .distinct()

        if (dnsServers.isEmpty()) {
            return
        }

        val resolv =
            File(
                rootfs,
                "etc/resolv.conf"
            )

        runCatching {
            if (
                Files.isSymbolicLink(
                    resolv.toPath()
                )
            ) {
                Files.deleteIfExists(
                    resolv.toPath()
                )
            }

            resolv.parentFile?.mkdirs()
            resolv.writeText(
                dnsServers.joinToString(
                    separator = "\n",
                    postfix = "\n"
                ) {
                    "nameserver $it"
                },
                Charsets.UTF_8
            )
        }
    }

    private fun rootfsLooksInstalled(
        rootfs: File
    ): Boolean =
        rootfs.isDirectory &&
            File(rootfs, "etc").isDirectory &&
            File(rootfs, "usr").isDirectory &&
            File(rootfs, "bin").exists()

    private fun constantTimeDigestEquals(
        first: String,
        second: String
    ): Boolean {
        if (
            !LinuxRuntimeIntegrity.isValidSha256(first) ||
            !LinuxRuntimeIntegrity.isValidSha256(second)
        ) {
            return false
        }

        return MessageDigest.isEqual(
            first
                .lowercase()
                .toByteArray(Charsets.US_ASCII),
            second
                .lowercase()
                .toByteArray(Charsets.US_ASCII)
        )
    }

    private fun detailFor(
        state: LinuxRuntimeState,
        manifestMissing: Boolean
    ): String =
        when (state) {
            LinuxRuntimeState.UNSUPPORTED_ABI ->
                "Cihaz mimarisi desteklenmiyor."

            LinuxRuntimeState.ENGINE_NOT_BUNDLED ->
                "Native PRoot/PTY motoru APK ile henüz paketlenmedi. İndirilen rastgele binary çalıştırılmayacak."

            LinuxRuntimeState.ROOTFS_NOT_INSTALLED ->
                "Doğrulanmış Debian/Ubuntu rootfs henüz kurulmadı."

            LinuxRuntimeState.ROOTFS_UNTRUSTED ->
                if (manifestMissing) {
                    "Bu dağıtım ve mimari için sabitlenmiş rootfs SHA-256 manifesti henüz APK'ya eklenmedi."
                } else {
                    "Rootfs metadata bilgisi sabitlenmiş manifest ile eşleşmiyor."
                }

            LinuxRuntimeState.READY ->
                "Native engine ve doğrulanmış rootfs hazır."
        }

    companion object {
        private val baseEnvironmentMutex =
            Mutex()

        private val developmentToolsMutex =
            Mutex()

        private val developmentEnvironmentMutex =
            Mutex()

        private const val DEV_SUITE_REVISION =
            "appforge-dev-suite-v3"

        private const val DEV_SUITE_MARKER =
            "var/lib/appforge/dev-suite-v3"
    }
}
