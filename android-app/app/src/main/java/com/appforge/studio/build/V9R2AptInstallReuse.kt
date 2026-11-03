package com.appforge.studio.build

import android.content.Context
import android.os.Build
import com.appforge.studio.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

/**
 * Debug-only, offline reuse of already downloaded and pinned R2 .deb objects.
 * The module installer still owns authorization, signed APT indexes, dpkg and READY.
 * No R2 request, new download, permission escalation or production URL change.
 */
internal object V9R2AptInstallReuse {
    private val SHA = Regex("[a-f0-9]{64}")
    private const val LIMIT = 40L * 1024 * 1024
    internal data class Result(val seeded: Int, val alreadyPresent: Int, val missing: Int, val verifiedBytes: Long) {
        val available: Int get() = seeded + alreadyPresent
    }

    /** Only pinned APK catalog digests are eligible. User-provided manifests cannot change this list. */
    fun seedForDebug(context: Context, stagedRoot: File, module: OfflineModuleId): Result {
        check(BuildConfig.DEBUG) { "R2 candidate APT reuse disabled in release" }
        val architecture = when {
            Build.SUPPORTED_ABIS.contains("arm64-v8a") -> "arm64"
            Build.SUPPORTED_ABIS.contains("x86_64") -> "amd64"
            else -> error("Unsupported Ubuntu R2 architecture")
        }
        val eligible = V9SelectiveR2ModuleCache.preview(
            context.applicationContext, setOf(module), architecture
        ).packages
        val privateCache = V9SignedR2CandidateDownload.privateCacheDirectory(
            context.applicationContext.noBackupFilesDir
        )
        return seedExisting(privateCache, stagedRoot, architecture, eligible)
    }

    /** Package-level test seam. Caller MUST first obtain expected records from pinned APK catalog. */
    internal fun seedExisting(
        privateCache: File,
        stagedRoot: File,
        architecture: String,
        expected: List<V9SelectiveR2ModuleCache.Package>
    ): Result {
        require(architecture == "arm64" || architecture == "amd64")
        require(privateCache.isDirectory && !Files.isSymbolicLink(privateCache.toPath()))
        val safeCache = privateCache.canonicalFile
        require(safeCache.isDirectory && !Files.isSymbolicLink(safeCache.toPath()))
        require(stagedRoot.isDirectory && !Files.isSymbolicLink(stagedRoot.toPath()))
        val safeRoot = stagedRoot.canonicalFile
        require(safeRoot.isDirectory && !Files.isSymbolicLink(safeRoot.toPath()))
        require(expected.size <= 214)
        require(expected.all { SHA.matches(it.sha256) && it.kind == "deb" && it.bytes in 1..LIMIT })
        require(expected.distinctBy { it.sha256 }.size == expected.size)
        require(expected.sumOf { it.bytes } <= 400L * 1024 * 1024)

        var destDir: File? = null
        var seeded = 0
        var alreadyPresent = 0
        var missing = 0
        var verifiedBytes = 0L
        for (item in expected) {
            val source = File(safeCache, item.sha256)
            require(!Files.isSymbolicLink(source.toPath())) { "Symlinked R2 source" }
            if (!source.exists()) { missing++; continue }
            require(source.isFile && source.length() == item.bytes) { "Invalid R2 source size" }
            require(source.canonicalFile.parentFile == safeCache) { "R2 source escaped cache" }
            require(sha256(source) == item.sha256) { "Tampered R2 cache object" }

            val archives = destDir ?: makeArchives(safeRoot, architecture).also { destDir = it }
            val target = File(archives, item.sha256 + ".deb")
            require(!Files.isSymbolicLink(target.toPath())) { "Symlinked APT target" }
            if (target.exists()) {
                require(target.isFile && target.length() == item.bytes && sha256(target) == item.sha256) {
                    "Existing APT CAS object has different bytes"
                }
                alreadyPresent++
            } else {
                val temporary = File(archives, ".r2-" + UUID.randomUUID() + ".part")
                try {
                    require(temporary.createNewFile())
                    FileOutputStream(temporary).use { output ->
                        source.inputStream().use { input -> input.copyTo(output, 64 * 1024) }
                        output.fd.sync()
                    }
                    require(temporary.length() == item.bytes && sha256(temporary) == item.sha256) {
                        "R2 staged copy integrity failure"
                    }
                    require(!target.exists() && !Files.isSymbolicLink(target.toPath()))
                    Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
                    seeded++
                } finally {
                    if (temporary.exists()) temporary.delete()
                }
            }
            verifiedBytes += item.bytes
        }
        return Result(seeded, alreadyPresent, missing, verifiedBytes)
    }

    private fun makeArchives(root: File, architecture: String): File {
        var current = root
        for (component in listOf(
            "opt", "appforge-device", "apt-snapshot-v1",
            architecture + "-" + AptModuleLocks.SNAPSHOT, "archives"
        )) {
            val next = File(current, component)
            require(!Files.isSymbolicLink(next.toPath())) { "Symlink in APT cache parent" }
            if (!next.exists()) require(next.mkdir()) { "Cannot create isolated APT CAS" }
            require(next.isDirectory && !Files.isSymbolicLink(next.toPath()))
            require(next.canonicalFile.parentFile == current.canonicalFile) { "APT CAS path escaped staging" }
            current = next.canonicalFile
        }
        return current
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                if (n > 0) digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
