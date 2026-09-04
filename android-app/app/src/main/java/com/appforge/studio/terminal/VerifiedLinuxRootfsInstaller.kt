package com.appforge.studio.terminal

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.coroutineContext

internal enum class LinuxInstallStage {
    PREPARING,
    DOWNLOADING,
    VERIFYING,
    EXTRACTING,
    FINALIZING,
    COMPLETE
}

internal data class LinuxInstallProgress(
    val stage: LinuxInstallStage,
    val completedBytes: Long = 0L,
    val totalBytes: Long? = null,
    val detail: String
) {
    val percent: Int?
        get() =
            totalBytes
                ?.takeIf { it > 0L }
                ?.let {
                    ((completedBytes * 100L) / it)
                        .coerceIn(0L, 100L)
                        .toInt()
                }
}

internal class VerifiedLinuxRootfsInstaller(
    context: Context
) {
    private val appContext =
        context.applicationContext

    suspend fun install(
        manifest: LinuxRootfsManifest,
        layout: LinuxRuntimeLayout,
        onProgress: (LinuxInstallProgress) -> Unit = {}
    ) =
        withContext(Dispatchers.IO) {
            require(
                manifest.distribution ==
                    layout.distribution &&
                    manifest.architecture ==
                    layout.architecture
            ) {
                "Rootfs manifesti runtime düzeniyle eşleşmiyor."
            }

            onProgress(
                LinuxInstallProgress(
                    stage = LinuxInstallStage.PREPARING,
                    detail = "Güvenli Linux kurulum alanı hazırlanıyor."
                )
            )

            layout.runtimeDirectory.mkdirs()
            layout.stagingDirectory.mkdirs()
            layout.restoreDirectory.mkdirs()

            val transaction =
                File(
                    layout.stagingDirectory,
                    "install-${UUID.randomUUID()}"
                )

            val archive =
                File(
                    transaction,
                    manifest.archiveFileName
                )

            val extractedRootfs =
                File(
                    transaction,
                    "rootfs"
                )

            transaction.mkdirs()

            try {
                downloadVerifiedSource(
                    manifest = manifest,
                    target = archive,
                    onProgress = onProgress
                )

                coroutineContext.ensureActive()

                onProgress(
                    LinuxInstallProgress(
                        stage = LinuxInstallStage.VERIFYING,
                        completedBytes = archive.length(),
                        totalBytes = archive.length(),
                        detail = "Ubuntu Base SHA-256 doğrulanıyor."
                    )
                )

                check(
                    LinuxRuntimeIntegrity.matches(
                        archive,
                        manifest.archiveSha256
                    )
                ) {
                    "Rootfs SHA-256 doğrulaması başarısız. Kurulum iptal edildi."
                }

                coroutineContext.ensureActive()

                onProgress(
                    LinuxInstallProgress(
                        stage = LinuxInstallStage.EXTRACTING,
                        detail = "Doğrulanmış rootfs izole alana çıkarılıyor."
                    )
                )

                LinuxTarGzExtractor.extract(
                    archive = archive,
                    destination = extractedRootfs
                ) {
                    coroutineContext.ensureActive()
                }

                validateRootfs(
                    extractedRootfs,
                    manifest.distribution
                )

                coroutineContext.ensureActive()

                onProgress(
                    LinuxInstallProgress(
                        stage = LinuxInstallStage.FINALIZING,
                        detail = "Rootfs etkinleştiriliyor ve metadata yazılıyor."
                    )
                )

                activateRootfs(
                    layout = layout,
                    stagedRootfs = extractedRootfs,
                    manifest = manifest
                )

                onProgress(
                    LinuxInstallProgress(
                        stage = LinuxInstallStage.COMPLETE,
                        completedBytes = archive.length(),
                        totalBytes = archive.length(),
                        detail = "Rootfs kuruldu ve SHA-256 ile doğrulandı."
                    )
                )
            } finally {
                transaction.deleteRecursively()
            }
        }

    private suspend fun downloadVerifiedSource(
        manifest: LinuxRootfsManifest,
        target: File,
        onProgress: (LinuxInstallProgress) -> Unit
    ) {
        val source =
            validateSourceUri(
                manifest.sourceUri
            )

        val connection =
            source
                .toURL()
                .openConnection() as HttpsURLConnection

        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.setRequestProperty(
                "User-Agent",
                "AppForge-Terminal-Ultimate/2B"
            )
            connection.setRequestProperty(
                "Accept-Encoding",
                "identity"
            )
            connection.connect()

            check(
                connection.responseCode ==
                    HttpsURLConnection.HTTP_OK
            ) {
                "Rootfs indirilemedi: HTTP ${connection.responseCode}."
            }

            val declaredLength =
                connection.contentLengthLong
                    .takeIf { it >= 0L }

            if (
                declaredLength != null &&
                declaredLength > manifest.maxArchiveBytes
            ) {
                error(
                    "Rootfs arşivi izin verilen boyut sınırını aşıyor."
                )
            }

            target.parentFile?.mkdirs()

            connection.inputStream.buffered().use { input ->
                target.outputStream().buffered().use { output ->
                    val buffer =
                        ByteArray(DOWNLOAD_BUFFER_BYTES)

                    var total = 0L

                    while (true) {
                        coroutineContext.ensureActive()

                        val count =
                            input.read(buffer)

                        if (count < 0) {
                            break
                        }

                        if (count == 0) {
                            continue
                        }

                        total += count.toLong()

                        check(
                            total <=
                                manifest.maxArchiveBytes
                        ) {
                            "Rootfs arşivi izin verilen boyut sınırını aşıyor."
                        }

                        output.write(
                            buffer,
                            0,
                            count
                        )

                        onProgress(
                            LinuxInstallProgress(
                                stage = LinuxInstallStage.DOWNLOADING,
                                completedBytes = total,
                                totalBytes = declaredLength,
                                detail = "Doğrulanmış Ubuntu Base arşivi indiriliyor."
                            )
                        )
                    }
                }
            }

            check(target.isFile && target.length() > 0L) {
                "Rootfs indirmesi boş dosya üretti."
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun validateSourceUri(uri: URI): URI {
        require(
            uri.scheme.equals(
                "https",
                ignoreCase = true
            ) &&
                uri.host
                    ?.lowercase() in
                    LinuxRootfsManifest.TRUSTED_ROOTFS_HOSTS &&
                uri.userInfo == null &&
                uri.query == null &&
                uri.fragment == null
        ) {
            "Rootfs kaynağı güvenilir HTTPS izin listesinde değil."
        }

        return uri
    }

    private fun validateRootfs(
        rootfs: File,
        distribution: LinuxDistribution
    ) {
        check(rootfs.isDirectory) {
            "Çıkarılan rootfs bulunamadı."
        }

        for (required in REQUIRED_ROOTFS_PATHS) {
            check(
                File(
                    rootfs,
                    required
                ).exists()
            ) {
                "Rootfs eksik: /$required"
            }
        }

        val osRelease =
            File(
                rootfs,
                "etc/os-release"
            )
                .takeIf {
                    it.isFile &&
                        it.length() <=
                        MAX_OS_RELEASE_BYTES
                }
                ?.readText(Charsets.UTF_8)
                .orEmpty()

        val expectedId =
            "ID=${distribution.id}"

        check(
            osRelease
                .lineSequence()
                .map { it.trim() }
                .any {
                    it.equals(
                        expectedId,
                        ignoreCase = true
                    ) ||
                        it.equals(
                            "ID=\"${distribution.id}\"",
                            ignoreCase = true
                        )
                }
        ) {
            "Rootfs dağıtım kimliği manifest ile eşleşmiyor."
        }
    }

    private fun activateRootfs(
        layout: LinuxRuntimeLayout,
        stagedRootfs: File,
        manifest: LinuxRootfsManifest
    ) {
        val current =
            layout.rootfsDirectory

        val backup =
            File(
                layout.restoreDirectory,
                "rootfs-before-${System.currentTimeMillis()}"
            )

        var backupCreated = false

        try {
            if (current.exists()) {
                check(
                    current.renameTo(backup)
                ) {
                    "Mevcut rootfs güvenli geri yükleme alanına taşınamadı."
                }

                backupCreated = true
            }

            check(
                stagedRootfs.renameTo(current)
            ) {
                "Yeni rootfs etkinleştirilemedi."
            }

            LinuxRootfsMetadataCodec.write(
                layout.metadataFile,
                LinuxRootfsMetadata(
                    distribution = manifest.distribution,
                    architecture = manifest.architecture,
                    release = manifest.release,
                    archiveSha256 = manifest.archiveSha256
                )
            )

            pruneRestorePoints(
                layout.restoreDirectory
            )
        } catch (error: Throwable) {
            if (
                !current.exists() &&
                backupCreated &&
                backup.exists()
            ) {
                backup.renameTo(current)
            }

            throw error
        }
    }

    private fun pruneRestorePoints(
        restoreDirectory: File
    ) {
        restoreDirectory
            .listFiles()
            .orEmpty()
            .filter {
                it.isDirectory &&
                    it.name.startsWith(
                        "rootfs-before-"
                    )
            }
            .sortedByDescending {
                it.lastModified()
            }
            .drop(MAX_RESTORE_POINTS)
            .forEach {
                it.deleteRecursively()
            }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS =
            20_000

        private const val READ_TIMEOUT_MS =
            30_000

        private const val DOWNLOAD_BUFFER_BYTES =
            64 * 1024

        private const val MAX_OS_RELEASE_BYTES =
            64L * 1024L

        private const val MAX_RESTORE_POINTS =
            1

        private val REQUIRED_ROOTFS_PATHS =
            listOf(
                "etc",
                "usr",
                "bin",
                "etc/os-release"
            )
    }
}

internal object LinuxTarGzExtractor {
    private const val MAX_ENTRY_COUNT =
        250_000

    private const val MAX_TOTAL_EXTRACTED_BYTES =
        6L * 1024L * 1024L * 1024L

    private const val MAX_SINGLE_FILE_BYTES =
        1024L * 1024L * 1024L

    private const val UNIX_EXECUTE_BITS =
        73 // 0111

    private const val UNIX_WRITE_BITS =
        146 // 0222

    private const val UNIX_READ_BITS =
        292 // 0444

    fun extract(
        archive: File,
        destination: File,
        cancellationCheck: () -> Unit = {}
    ) {
        require(
            archive.isFile &&
                archive.canRead()
        ) {
            "Rootfs arşivi okunamıyor."
        }

        if (destination.exists()) {
            destination.deleteRecursively()
        }

        check(destination.mkdirs()) {
            "Rootfs çıkarma klasörü oluşturulamadı."
        }

        data class HardLinkRequest(
            val target: File,
            val linkName: String,
            val mode: Int
        )

        val hardLinks =
            mutableListOf<HardLinkRequest>()

        var entryCount = 0
        var totalExtracted = 0L

        archive.inputStream().buffered().use { fileInput ->
            GzipCompressorInputStream(fileInput).use { gzip ->
                TarArchiveInputStream(gzip).use { tar ->
                    while (true) {
                        cancellationCheck()

                        val entry =
                            tar.nextTarEntry
                                ?: break

                        entryCount += 1

                        check(
                            entryCount <=
                                MAX_ENTRY_COUNT
                        ) {
                            "Rootfs arşivi aşırı sayıda dosya içeriyor."
                        }

                        val target =
                            safeTarget(
                                destination,
                                entry.name
                            )

                        ensureNoSymlinkParents(
                            destination,
                            target.parentFile
                        )

                        when {
                            entry.isDirectory -> {
                                check(
                                    target.exists() ||
                                        target.mkdirs()
                                ) {
                                    "Rootfs klasörü oluşturulamadı: ${entry.name}"
                                }

                                applyMode(
                                    target,
                                    entry.mode
                                )
                            }

                            entry.isSymbolicLink -> {
                                target.parentFile?.mkdirs()
                                Files.deleteIfExists(
                                    target.toPath()
                                )
                                Files.createSymbolicLink(
                                    target.toPath(),
                                    Paths.get(
                                        entry.linkName
                                    )
                                )
                            }

                            entry.isLink -> {
                                hardLinks +=
                                    HardLinkRequest(
                                        target = target,
                                        linkName = entry.linkName,
                                        mode = entry.mode
                                    )
                            }

                            entry.isFile -> {
                                check(
                                    entry.size in
                                        0L..MAX_SINGLE_FILE_BYTES
                                ) {
                                    "Rootfs dosyası izin verilen boyutu aşıyor: ${entry.name}"
                                }

                                totalExtracted +=
                                    entry.size

                                check(
                                    totalExtracted <=
                                        MAX_TOTAL_EXTRACTED_BYTES
                                ) {
                                    "Rootfs çıkarma boyutu güvenlik sınırını aşıyor."
                                }

                                target.parentFile?.mkdirs()

                                target.outputStream()
                                    .buffered()
                                    .use { output ->
                                        copyEntry(
                                            tar,
                                            output,
                                            cancellationCheck
                                        )
                                    }

                                applyMode(
                                    target,
                                    entry.mode
                                )
                            }

                            else -> {
                                // Device/FIFO entries are intentionally not
                                // materialized. PRoot later supplies safe binds.
                            }
                        }
                    }
                }
            }
        }

        for (request in hardLinks) {
            cancellationCheck()

            val target =
                request.target

            val source =
                safeTarget(
                    destination,
                    request.linkName
                )

            ensureNoSymlinkParents(
                destination,
                target.parentFile
            )

            check(
                source.exists() &&
                    source.isFile &&
                    !Files.isSymbolicLink(
                        source.toPath()
                    )
            ) {
                "Rootfs hard-link hedefi bulunamadı: ${request.linkName}"
            }

            target.parentFile?.mkdirs()
            Files.deleteIfExists(
                target.toPath()
            )

            val linked =
                runCatching {
                    Files.createLink(
                        target.toPath(),
                        source.toPath()
                    )
                    true
                }.getOrDefault(false)

            if (!linked) {
                val copyBytes =
                    source.length()

                check(
                    copyBytes in
                        0L..MAX_SINGLE_FILE_BYTES &&
                        totalExtracted <=
                            MAX_TOTAL_EXTRACTED_BYTES -
                                copyBytes
                ) {
                    "Rootfs hard-link kopyalama sınırı aşıldı."
                }

                Files.copy(
                    source.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )

                totalExtracted +=
                    copyBytes
            }

            applyMode(
                target,
                request.mode
            )
        }
    }

    private fun safeTarget(
        root: File,
        rawName: String
    ): File {
        val clean =
            rawName
                .replace('\\', '/')
                .removePrefix("./")
                .trimStart('/')

        require(
            clean.isNotBlank() &&
                '\u0000' !in clean &&
                clean.length <= 4_096
        ) {
            "Rootfs arşiv yolu geçersiz."
        }

        val rootPath =
            root.toPath()
                .toAbsolutePath()
                .normalize()

        val targetPath =
            rootPath
                .resolve(clean)
                .normalize()

        require(
            targetPath.startsWith(
                rootPath
            ) &&
                targetPath != rootPath
        ) {
            "Rootfs arşivinde path traversal engellendi."
        }

        return targetPath.toFile()
    }

    private fun ensureNoSymlinkParents(
        root: File,
        parent: File?
    ) {
        if (parent == null) {
            return
        }

        val rootPath =
            root.toPath()
                .toAbsolutePath()
                .normalize()

        val parentPath =
            parent.toPath()
                .toAbsolutePath()
                .normalize()

        require(
            parentPath.startsWith(
                rootPath
            )
        ) {
            "Rootfs hedefi güvenli alan dışında."
        }

        var cursor = rootPath

        for (part in rootPath.relativize(parentPath)) {
            cursor = cursor.resolve(part)

            require(
                !Files.isSymbolicLink(cursor)
            ) {
                "Rootfs arşivinde symlink üzerinden yazma engellendi."
            }
        }
    }

    private fun copyEntry(
        input: TarArchiveInputStream,
        output: java.io.OutputStream,
        cancellationCheck: () -> Unit
    ) {
        val buffer =
            ByteArray(64 * 1024)

        while (true) {
            cancellationCheck()

            val count =
                input.read(buffer)

            if (count < 0) {
                break
            }

            if (count > 0) {
                output.write(
                    buffer,
                    0,
                    count
                )
            }
        }
    }

    private fun applyMode(
        file: File,
        mode: Int
    ) {
        file.setReadable(
            mode and UNIX_READ_BITS != 0,
            false
        )
        file.setWritable(
            mode and UNIX_WRITE_BITS != 0,
            false
        )
        file.setExecutable(
            mode and UNIX_EXECUTE_BITS != 0,
            false
        )
    }
}
