package com.appforge.studio.terminal

import android.content.Context
import android.os.Build
import java.io.File
import java.security.MessageDigest

internal enum class PackagedLinuxEngineStatus {
    UNSUPPORTED_ABI,
    NATIVE_LIBRARY_DIR_MISSING,
    ASSET_MISSING,
    ASSET_INVALID,
    READY
}

internal data class PackagedLinuxEngineInspection(
    val status: PackagedLinuxEngineStatus,
    val detail: String,
    val launcher: File? = null
)

internal class PackagedLinuxEngine(
    context: Context
) {
    private val applicationContext =
        context.applicationContext

    fun inspect():
        PackagedLinuxEngineInspection {
        if (
            ProrootPinnedRuntime.supportedAbi !in
            Build.SUPPORTED_ABIS
        ) {
            return PackagedLinuxEngineInspection(
                status =
                    PackagedLinuxEngineStatus
                        .UNSUPPORTED_ABI,
                detail =
                    "Yerel Linux motoru şu anda arm64-v8a gerektiriyor."
            )
        }

        val nativeDirectory =
            applicationContext
                .applicationInfo
                .nativeLibraryDir
                ?.takeIf {
                    it.isNotBlank()
                }
                ?.let(::File)
                ?.let {
                    runCatching {
                        it.canonicalFile
                    }.getOrNull()
                }
                ?.takeIf {
                    it.isDirectory
                }
                ?: return
                    PackagedLinuxEngineInspection(
                        status =
                            PackagedLinuxEngineStatus
                                .NATIVE_LIBRARY_DIR_MISSING,
                        detail =
                            "Android native library klasörü bulunamadı."
                    )

        ProrootPinnedRuntime.assets
            .forEach { asset ->
                val file =
                    File(
                        nativeDirectory,
                        asset.name
                    )

                val safeFile =
                    runCatching {
                        file.canonicalFile
                    }.getOrNull()

                if (
                    safeFile == null ||
                    safeFile.parentFile !=
                    nativeDirectory ||
                    !safeFile.isFile
                ) {
                    return
                        PackagedLinuxEngineInspection(
                            status =
                                PackagedLinuxEngineStatus
                                    .ASSET_MISSING,
                            detail =
                                "Paketlenmiş Linux motoru eksik: ${asset.name}"
                        )
                }

                if (
                    !safeFile.canRead() ||
                    (
                        asset.name ==
                            ProrootPinnedRuntime.launcherName &&
                            !safeFile.canExecute()
                        ) ||
                    safeFile.length() !=
                    asset.size ||
                    sha256(safeFile) !=
                    asset.sha256
                ) {
                    return
                        PackagedLinuxEngineInspection(
                            status =
                                PackagedLinuxEngineStatus
                                    .ASSET_INVALID,
                            detail =
                                "Paketlenmiş Linux motoru bütünlük kontrolünden geçmedi: ${asset.name}"
                        )
                }
            }

        val launcher =
            File(
                nativeDirectory,
                ProrootPinnedRuntime.launcherName
            ).canonicalFile

        return
            PackagedLinuxEngineInspection(
                status =
                    PackagedLinuxEngineStatus.READY,
                detail =
                    "Proroot ${ProrootPinnedRuntime.version} doğrulandı.",
                launcher =
                    launcher
            )
    }

    fun requireLauncher(): File {
        val inspection =
            inspect()

        check(
            inspection.status ==
                PackagedLinuxEngineStatus.READY &&
                inspection.launcher != null
        ) {
            inspection.detail
        }

        return requireNotNull(
            inspection.launcher
        )
    }

    private fun sha256(
        file: File
    ): String {
        val digest =
            MessageDigest.getInstance(
                "SHA-256"
            )

        file.inputStream()
            .buffered()
            .use { input ->
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
}
