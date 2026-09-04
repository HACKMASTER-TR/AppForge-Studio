package com.appforge.studio.terminal

import android.content.Context
import android.os.Build
import java.io.File
import java.security.MessageDigest

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

}
