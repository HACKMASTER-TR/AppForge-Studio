package com.appforge.studio.build

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.appforge.studio.terminal.LinuxArchitecture
import com.appforge.studio.terminal.LinuxDistribution
import com.appforge.studio.terminal.LinuxRuntimeLayout
import com.appforge.studio.terminal.LinuxShellEngine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID

internal data class OfflineBuildPackStatus(
    val androidCoreReady: Boolean,
    val nodeToolchainReady: Boolean,
    val pythonAndroidReady: Boolean,
    val windowsHostReady: Boolean,
    val windowsExeReady: Boolean
) {
    val currentAndroidEnginesReady: Boolean
        get() =
            androidCoreReady &&
                nodeToolchainReady &&
                pythonAndroidReady

    val completeTargetReady: Boolean
        get() =
            currentAndroidEnginesReady &&
                windowsExeReady
}

internal object OfflineBuildPackManager {

    const val REVISION =
        "offline-build-pack-v1"

    const val ESTIMATED_DOWNLOAD_BYTES =
        1_800_000_000L

    const val ESTIMATED_INSTALLED_BYTES =
        4_500_000_000L

    private const val PACK_DIRECTORY =
        "opt/appforge-device/offline-pack-v1"

    private val installMutex =
        Mutex()

    fun status(
        context: Context
    ): OfflineBuildPackStatus {

        val appContext =
            context.applicationContext

        val windowsHostReady =
            WindowsPortableHostStore
                .isInstalled(
                    appContext
                )

        val rootfs =
            runtimeRootfs(
                appContext
            )

        if (
            rootfs == null ||
            !rootfs.isDirectory
        ) {
            return OfflineBuildPackStatus(
                androidCoreReady = false,
                nodeToolchainReady = false,
                pythonAndroidReady = false,
                windowsHostReady =
                    windowsHostReady,
                windowsExeReady = false
            )
        }

        val pack =
            File(
                rootfs,
                PACK_DIRECTORY
            )

        return OfflineBuildPackStatus(
            androidCoreReady =
                markerReady(
                    File(
                        pack,
                        "android.ready"
                    )
                ),

            nodeToolchainReady =
                markerReady(
                    File(
                        pack,
                        "node.ready"
                    )
                ),

            pythonAndroidReady =
                markerReady(
                    File(
                        pack,
                        "python.ready"
                    )
                ),

            windowsHostReady =
                windowsHostReady,

            /*
             * Portable EXE deliberately remains false until
             * the Android-hosted Windows packager and a real
             * Windows execution acceptance test both pass.
             */
            windowsExeReady =
                false
        )
    }

    suspend fun installReadyComponents(
        context: Context,
        androidSdkLicenseAccepted: Boolean,
        onProgress: (String) -> Unit = {}
    ): OfflineBuildPackStatus =
        installMutex.withLock {

            val appContext =
                context.applicationContext

            require(
                androidSdkLicenseAccepted
            ) {
                "Android SDK lisansı kabul edilmeden kurulum başlatılamaz."
            }

            require(
                hasValidatedInternet(
                    appContext
                )
            ) {
                "İlk çevrimdışı paket kurulumu için internet bağlantısı gerekli."
            }

            onProgress(
                "Clean Device Build Runtime hazırlanıyor..."
            )

            val rootfs =
                DeviceBuildRuntimeV3
                    .ensureReady(
                        appContext
                    ) {
                        onProgress(
                            it
                        )
                    }

            val workspace =
                File(
                    appContext.cacheDir,
                    "offline-build-pack-v1/${UUID.randomUUID()}"
                )

            workspace.deleteRecursively()
            workspace.mkdirs()

            try {

                val runtime =
                    File(
                        workspace,
                        "runtime"
                    )

                copyAssetTree(
                    appContext,
                    "device-build",
                    runtime
                )

                val shell =
                    LinuxShellEngine(
                        appContext
                    )

                val packDirectory =
                    File(
                        rootfs,
                        PACK_DIRECTORY
                    ).apply {
                        mkdirs()
                    }

                onProgress(
                    "1/4 • Android SDK, JDK ve Gradle hazırlanıyor..."
                )

                execute(
                    shell = shell,
                    rootfs = rootfs,
                    workspace = workspace,
                    suffix = "android-toolchain",
                    command =
                        "chmod +x " +
                            "/workspace/runtime/install-toolchain.sh " +
                            "/workspace/runtime/prepare-offline-pack.sh && " +
                            "APPFORGE_ANDROID_SDK_LICENSE_ACCEPTED=1 " +
                            "APPFORGE_DEVICE_OFFLINE=0 " +
                            "/bin/sh /workspace/runtime/install-toolchain.sh " +
                            "webview-static"
                )

                onProgress(
                    "Android Gradle bağımlılıkları önbelleğe alınıyor..."
                )

                execute(
                    shell = shell,
                    rootfs = rootfs,
                    workspace = workspace,
                    suffix = "android-prewarm",
                    command =
                        "/bin/sh " +
                            "/workspace/runtime/prepare-offline-pack.sh " +
                            "android"
                )

                writeMarker(
                    File(
                        packDirectory,
                        "android.ready"
                    )
                )

                onProgress(
                    "2/4 • Node.js ve npm hazırlanıyor..."
                )

                execute(
                    shell = shell,
                    rootfs = rootfs,
                    workspace = workspace,
                    suffix = "node-toolchain",
                    command =
                        "APPFORGE_DEVICE_OFFLINE=0 " +
                            "/bin/sh /workspace/runtime/install-toolchain.sh " +
                            "node-web"
                )

                execute(
                    shell = shell,
                    rootfs = rootfs,
                    workspace = workspace,
                    suffix = "node-prewarm",
                    command =
                        "/bin/sh " +
                            "/workspace/runtime/prepare-offline-pack.sh " +
                            "node"
                )

                writeMarker(
                    File(
                        packDirectory,
                        "node.ready"
                    )
                )

                onProgress(
                    "3/4 • Python ve Chaquopy hazırlanıyor..."
                )

                execute(
                    shell = shell,
                    rootfs = rootfs,
                    workspace = workspace,
                    suffix = "python-toolchain",
                    command =
                        "APPFORGE_DEVICE_OFFLINE=0 " +
                            "/bin/sh /workspace/runtime/install-toolchain.sh " +
                            "python-android"
                )

                execute(
                    shell = shell,
                    rootfs = rootfs,
                    workspace = workspace,
                    suffix = "python-prewarm",
                    command =
                        "/bin/sh " +
                            "/workspace/runtime/prepare-offline-pack.sh " +
                            "python"
                )

                writeMarker(
                    File(
                        packDirectory,
                        "python.ready"
                    )
                )

                onProgress(
                    "4/4 • Windows Portable Host hazırlanıyor..."
                )

                WindowsPortableHostStore
                    .install(
                        appContext
                    ) {
                        detail ->

                        onProgress(
                            detail
                        )
                    }

                File(
                    packDirectory,
                    "revision"
                ).writeText(
                    "$REVISION\n",
                    Charsets.UTF_8
                )

                onProgress(
                    "Android, Node, Python ve Windows Portable Host " +
                        "cihazda doğrulandı. EXE paketleme kabulü bekleniyor."
                )

                status(
                    appContext
                )
            } finally {
                workspace.deleteRecursively()
            }
        }

    private suspend fun execute(
        shell: LinuxShellEngine,
        rootfs: File,
        workspace: File,
        suffix: String,
        command: String
    ) {
        val sessionId =
            "offline-pack-$suffix-${UUID.randomUUID()}"

        val result =
            shell.execute(
                sessionId = sessionId,
                rootfs = rootfs,
                workspace = workspace,
                command = command,
                confirmed = true,
                timeoutMs = 1_800_000L
            )

        check(
            !result.timedOut
        ) {
            "Çevrimdışı paket kurulumu zaman aşımına uğradı: $suffix"
        }

        check(
            result.exitCode ==
                0
        ) {
            result.output
                .takeLast(
                    4_000
                )
                .ifBlank {
                    "Çevrimdışı paket bileşeni kurulamadı: $suffix"
                }
        }
    }

    private fun writeMarker(
        file: File
    ) {
        file.parentFile?.mkdirs()

        file.writeText(
            "$REVISION\n",
            Charsets.UTF_8
        )
    }

    private fun markerReady(
        file: File
    ): Boolean =
        file.isFile &&
            file.length() <=
                128L &&
            runCatching {
                file.readText(
                    Charsets.UTF_8
                ).trim()
            }.getOrNull() ==
                REVISION

    private fun runtimeRootfs(
        context: Context
    ): File? {

        val architecture =
            LinuxArchitecture
                .fromAndroidAbis(
                    Build.SUPPORTED_ABIS.toList()
                )
                ?: return null

        return LinuxRuntimeLayout(
            baseDirectory =
                DeviceBuildRuntimeV3
                    .runtimeBaseDirectory(
                        context
                    ),
            distribution =
                LinuxDistribution.UBUNTU,
            architecture =
                architecture
        ).rootfsDirectory
    }

    private fun hasValidatedInternet(
        context: Context
    ): Boolean {

        val manager =
            context.getSystemService(
                ConnectivityManager::class.java
            )
                ?: return false

        val network =
            manager.activeNetwork
                ?: return false

        val capabilities =
            manager.getNetworkCapabilities(
                network
            )
                ?: return false

        return capabilities.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_INTERNET
        ) &&
            capabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED
            )
    }

    private fun copyAssetTree(
        context: Context,
        assetPath: String,
        target: File
    ) {
        val children =
            context.assets
                .list(
                    assetPath
                )
                .orEmpty()

        if (
            children.isEmpty()
        ) {
            target.parentFile?.mkdirs()

            context.assets
                .open(
                    assetPath
                )
                .use {
                    input ->

                    target.outputStream()
                        .use {
                            output ->

                            input.copyTo(
                                output
                            )
                        }
                }

            return
        }

        target.mkdirs()

        children.forEach {
            child ->

            copyAssetTree(
                context,
                "$assetPath/$child",
                File(
                    target,
                    child
                )
            )
        }
    }
}
