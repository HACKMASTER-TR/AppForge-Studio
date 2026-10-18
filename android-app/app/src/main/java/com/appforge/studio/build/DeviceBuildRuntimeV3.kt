package com.appforge.studio.build

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import com.appforge.studio.terminal.LinuxArchitecture
import com.appforge.studio.terminal.LinuxDistribution
import com.appforge.studio.terminal.LinuxRootfsManifest
import com.appforge.studio.terminal.LinuxRootfsMetadataCodec
import com.appforge.studio.terminal.LinuxRuntimeLayout
import com.appforge.studio.terminal.LinuxRuntimeManifestRegistry
import com.appforge.studio.terminal.PackagedLinuxEngine
import com.appforge.studio.terminal.PackagedLinuxEngineStatus
import com.appforge.studio.terminal.VerifiedLinuxRootfsInstaller
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

/**
 * Dedicated disposable/versioned Linux environment for project builds.
 *
 * IMPORTANT:
 *
 * - It does not reuse Terminal's Linux rootfs.
 * - User project source is mounted only through the build workspace.
 * - A runtime revision mismatch destroys only the dedicated build runtime.
 * - Terminal files and Terminal package state are never repaired/purged here.
 */
internal object DeviceBuildRuntimeV3 {

    const val REVISION =
        "device-build-runtime-v3"

    private const val MARKER_NAME =
        ".appforge-device-build-runtime"

    private val installMutex =
        Mutex()

    fun platformError():String? {
        val architecture=LinuxArchitecture.fromAndroidAbis(Build.SUPPORTED_ABIS.toList())?:return "Bu cihaz mimarisi Linux derleme ortamını desteklemiyor; paket eksikliği değil."
        return if(LinuxRuntimeManifestRegistry.find(LinuxDistribution.UBUNTU,architecture)==null)"Bu mimari için doğrulanmış Linux rootfs yok." else null
    }
    fun isReady(context:Context):Boolean = runCatching {
        val architecture=LinuxArchitecture.fromAndroidAbis(Build.SUPPORTED_ABIS.toList())?:return@runCatching false
        val manifest=LinuxRuntimeManifestRegistry.find(LinuxDistribution.UBUNTU,architecture)?:return@runCatching false
        val layout=LinuxRuntimeLayout(runtimeBaseDirectory(context),LinuxDistribution.UBUNTU,architecture)
        PackagedLinuxEngine(context).inspect().status==PackagedLinuxEngineStatus.READY && isTrustedRuntime(layout,manifest)
    }.getOrDefault(false)

    suspend fun ensureReady(
        context: Context,
        onBytes: (com.appforge.studio.terminal.LinuxInstallProgress) -> Unit = {},
        onProgress: (String) -> Unit = {}
    ): File =
        installMutex.withLock {

            val appContext =
                context.applicationContext

            val architecture =
                LinuxArchitecture
                    .fromAndroidAbis(
                        Build.SUPPORTED_ABIS.toList()
                    )
                    ?: error(
                        "Bu cihaz mimarisi Device Build Runtime V3 tarafından desteklenmiyor."
                    )

            val distribution =
                LinuxDistribution.UBUNTU

            val manifest =
                LinuxRuntimeManifestRegistry
                    .find(
                        distribution,
                        architecture
                    )
                    ?: error(
                        "Device Build Runtime V3 için doğrulanmış Ubuntu rootfs manifesti yok."
                    )

            val packagedEngine =
                PackagedLinuxEngine(
                    appContext
                ).inspect()

            check(
                packagedEngine.status ==
                    PackagedLinuxEngineStatus.READY
            ) {
                packagedEngine.detail
            }

            val layout =
                LinuxRuntimeLayout(
                    baseDirectory =
                        runtimeBaseDirectory(
                            appContext
                        ),
                    distribution =
                        distribution,
                    architecture =
                        architecture
                )

            RuntimeGenerationStore(layout.rootfsDirectory).recover()
            if (
                !isTrustedRuntime(
                    layout,
                    manifest
                )
            ) {
                onProgress(
                    "🧼 Clean Device Build Runtime V3 hazırlanıyor."
                )

                /*
                 * This is intentionally destructive only inside:
                 *
                 * noBackupFilesDir/device-build/runtime-v3
                 *
                 * Terminal Linux is outside this tree.
                 */
                layout
                    .runtimeDirectory
                    .deleteRecursively()

                VerifiedLinuxRootfsInstaller(
                    appContext
                ).install(
                    manifest =
                        manifest,
                    layout =
                        layout
                ) { progress ->
                    onBytes(progress)

                    val percent =
                        progress.percent
                            ?.let {
                                " • %$it"
                            }
                            .orEmpty()

                    onProgress(
                        "🐧 Build Runtime V3 • " +
                            progress.detail +
                            percent
                    )
                }

                marker(
                    layout
                ).apply {
                    parentFile?.mkdirs()

                    writeText(
                        "$REVISION\n",
                        Charsets.UTF_8
                    )
                }
            } else {
                onProgress(
                    "✅ Clean Device Build Runtime V3 hazır."
                )
            }

            configureResolver(
                appContext,
                layout.rootfsDirectory
            )

            check(
                isTrustedRuntime(
                    layout,
                    manifest
                )
            ) {
                "Device Build Runtime V3 bütünlük kontrolünden geçmedi."
            }

            layout
                .rootfsDirectory
                .canonicalFile
        }


    internal fun runtimeBaseDirectory(
        context: Context
    ): File =
        File(
            context.noBackupFilesDir,
            "device-build/runtime-v3"
        )


    private fun marker(
        layout: LinuxRuntimeLayout
    ): File =
        File(
            layout.runtimeDirectory,
            MARKER_NAME
        )


    private fun isTrustedRuntime(
        layout: LinuxRuntimeLayout,
        manifest: LinuxRootfsManifest
    ): Boolean {

        val rootfs =
            layout.rootfsDirectory

        if (
            !rootfs.isDirectory ||
            !File(rootfs, "etc").isDirectory ||
            !File(rootfs, "usr").isDirectory ||
            !File(rootfs, "bin").exists()
        ) {
            return false
        }

        val marker =
            marker(
                layout
            )

        if (
            !marker.isFile ||
            marker.length() >
                128L ||
            runCatching {
                marker
                    .readText(
                        Charsets.UTF_8
                    )
                    .trim()
            }.getOrNull() !=
                REVISION
        ) {
            return false
        }

        val metadata =
            LinuxRootfsMetadataCodec
                .read(
                    layout.metadataFile
                )
                ?: return false

        if (
            metadata.distribution !=
                manifest.distribution ||
            metadata.architecture !=
                manifest.architecture ||
            metadata.release !=
                manifest.release
        ) {
            return false
        }

        return MessageDigest.isEqual(
            metadata.archiveSha256
                .lowercase()
                .toByteArray(
                    Charsets.US_ASCII
                ),
            manifest.archiveSha256
                .lowercase()
                .toByteArray(
                    Charsets.US_ASCII
                )
        )
    }


    private fun configureResolver(
        context: Context,
        rootfs: File
    ) {
        val connectivity =
            context.getSystemService(
                ConnectivityManager::class.java
            )
                ?: return

        val network =
            connectivity.activeNetwork
                ?: return

        val dnsServers =
            connectivity
                .getLinkProperties(
                    network
                )
                ?.dnsServers
                .orEmpty()
                .mapNotNull {
                    it.hostAddress
                }
                .filter {
                    it.isNotBlank()
                }
                .distinct()

        if (
            dnsServers.isEmpty()
        ) {
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

            resolv
                .parentFile
                ?.mkdirs()

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
}
