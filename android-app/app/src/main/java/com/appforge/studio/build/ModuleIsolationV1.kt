package com.appforge.studio.build

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

/**
 * Phase 1 of per-module isolation. This class does not activate modules, move a
 * rootfs, or mark an installation READY. Existing generations remain untouched.
 *
 * One user-requested module gets one private installation namespace. Its entire
 * dependency closure belongs to that namespace; dependency files are never
 * sourced from a different module's installed rootfs.
 */
internal object ModuleIsolationV1 {
    private const val ROOT = "isolated-modules-v1"
    private val SHA = Regex("[a-f0-9]{64}")
    private const val MAX_ARCHIVE = 40L * 1024 * 1024

    internal data class Request(
        val target: OfflineModuleId,
        val dependencyOrder: List<OfflineModuleId>,
        val requiresLinux: Boolean,
        val requiresSdkLicense: Boolean,
        val bundledOnly: Boolean
    )

    internal data class Layout(
        val owner: OfflineModuleId,
        val base: File,
        val rootfs: File,
        val downloads: File,
        val apt: File,
        val state: File,
        val stages: File
    )

    /** Demand planning never broadens a user's explicit module selection. */
    fun request(target: OfflineModuleId): Request {
        val order = OfflineModules.resolve(setOf(target))
        require(order.isNotEmpty() && order.last() == target && order.distinct().size == order.size)
        return Request(
            target = target,
            dependencyOrder = order,
            requiresLinux = order.any { it !in setOf(OfflineModuleId.FAST_HTML, OfflineModuleId.WINDOWS_PORTABLE) },
            requiresSdkLicense = order.any { it == OfflineModuleId.ANDROID_CORE },
            bundledOnly = order == listOf(OfflineModuleId.FAST_HTML)
        )
    }

    /** For multi-output projects each requested module has its OWN dependency copy. */
    fun requests(selected: Set<OfflineModuleId>): List<Request> {
        require(selected.isNotEmpty()) { "No user-selected module" }
        return selected.map(::request)
    }

    /** The existing manager retains its proven legacy dependency ordering. */
    fun legacyDependencyOrder(selected: Set<OfflineModuleId>): List<OfflineModuleId> =
        requests(selected).flatMap { it.dependencyOrder }.distinct()

    /** Only under app-private storage; never under public Downloads or a project. */
    fun prepare(privateData: File, target: OfflineModuleId): Layout {
        require(!request(target).bundledOnly) { "Bundled Fast HTML needs no downloaded runtime" }
        val original = privateData.absoluteFile
        require(original.isDirectory && !Files.isSymbolicLink(original.toPath()))
        val anchor = original.canonicalFile
        require(anchor.isDirectory && !Files.isSymbolicLink(anchor.toPath()))
        val top = directory(anchor, ROOT)
        val base = directory(top, target.name.lowercase())
        return Layout(
            target, base,
            directory(base, "rootfs"),
            directory(base, "downloads"),
            directory(base, "apt"),
            directory(base, "state"),
            directory(base, "stages")
        )
    }

    /**
     * Copy only an already-authorized, SHA-256-pinned artifact into its module's
     * independent namespace; never hard-link or borrow another module's files.
     * Caller must verify the signed release/catalog before passing its digest.
     */
    fun copyVerifiedArchive(layout: Layout, source: File, expectedSha256: String, expectedBytes: Long): File {
        require(SHA.matches(expectedSha256) && expectedBytes in 1..MAX_ARCHIVE)
        require(layout.owner.name.lowercase() == layout.base.name)
        require(layout.downloads.parentFile.canonicalFile == layout.base.canonicalFile)
        require(layout.downloads.isDirectory && !Files.isSymbolicLink(layout.downloads.toPath()))
        require(source.isFile && !Files.isSymbolicLink(source.toPath()))
        require(source.length() == expectedBytes && sha256(source) == expectedSha256) {
            "Source archive not verified"
        }
        val target = File(layout.downloads, expectedSha256)
        require(!Files.isSymbolicLink(target.toPath()))
        if (Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            require(target.isFile && target.length() == expectedBytes && sha256(target) == expectedSha256) {
                "Existing module archive is invalid"
            }
            return target
        }
        require(layout.downloads.usableSpace > expectedBytes + 64L * 1024 * 1024) {
            "Not enough free space for isolated module archive"
        }
        val temporary = File(layout.downloads, ".copy-${UUID.randomUUID()}")
        require(temporary.createNewFile())
        try {
            require(!Files.isSymbolicLink(temporary.toPath()))
            FileOutputStream(temporary).use { out ->
                source.inputStream().use { it.copyTo(out, 64 * 1024) }
                out.fd.sync()
            }
            require(temporary.length() == expectedBytes && sha256(temporary) == expectedSha256)
            require(!Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS))
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            return target
        } finally {
            if (Files.exists(temporary.toPath(), LinkOption.NOFOLLOW_LINKS) &&
                !Files.isSymbolicLink(temporary.toPath())) {
                Files.delete(temporary.toPath())
            }
        }
    }

    private fun directory(parent: File, name: String): File {
        require(name.matches(Regex("[a-z0-9_-]{1,64}")))
        require(parent.isDirectory && !Files.isSymbolicLink(parent.toPath()))
        val path = File(parent, name)
        require(!Files.isSymbolicLink(path.toPath())) { "Symlinked module namespace refused" }
        if (!Files.exists(path.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectory(path.toPath())
        }
        require(path.isDirectory && !Files.isSymbolicLink(path.toPath()))
        require(path.canonicalFile.parentFile == parent.canonicalFile)
        return path
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val bytes = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(bytes)
                if (n < 0) break
                if (n > 0) digest.update(bytes, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
