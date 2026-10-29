package com.appforge.studio.build

import java.io.File
import java.net.URI
import java.nio.file.Files
import java.security.MessageDigest

/** Reviewed, packaged metadata only. This preview never installs a Termux package
 * into the managed Ubuntu runtime or the interactive terminal. SPDX identifiers
 * and notice hashes establish identity, not a legal review of package contents. */
internal data class OfflineToolPackage(
    val name: String, val version: String, val abi: String, val sha256: String,
    val upstream: String, val license: String, val licenseSha256: String, val size: Long
)
internal data class OfflineToolCatalogCheck(val complete: Boolean, val missingOrInvalid: List<String>)

internal object OfflineToolCatalog {
    private const val HEADER = "APPFORGE_OFFLINE_TOOL_CATALOG_V1"
    private val hash = Regex("[a-f0-9]{64}")
    private val token = Regex("[A-Za-z0-9][A-Za-z0-9.+_:-]{0,127}")
    // Deliberately limited to reviewed license families. New families require a
    // source review; a remote manifest cannot expand this policy.
    private val licenses = setOf("MIT", "Apache-2.0", "BSD-2-Clause", "BSD-3-Clause", "ISC", "Zlib")
    private fun validate(pack: OfflineToolPackage) {
        require(token.matches(pack.name) && token.matches(pack.version) && token.matches(pack.abi)) { "Invalid package identity" }
        require(hash.matches(pack.sha256) && hash.matches(pack.licenseSha256)) { "Invalid package or notice hash" }
        require(pack.license in licenses) { "Unreviewed license family" }
        require(pack.size in 1..(512L * 1024 * 1024)) { "Package size exceeds policy" }
        val uri = URI(pack.upstream)
        require(uri.scheme == "https" && uri.host in setOf("packages.termux.dev", "github.com") &&
            uri.userInfo == null && uri.fragment == null && uri.query == null &&
            pack.upstream.none { it.isWhitespace() || it.isISOControl() }) { "Invalid upstream" }
    }
    fun encode(packages: List<OfflineToolPackage>): String {
        require(packages.size in 1..1000) { "Catalog size exceeds policy" }
        packages.forEach(::validate)
        require(packages.map { it.name to it.abi }.distinct().size == packages.size) { "Duplicate package identity" }
        return HEADER + "\n" + packages.sortedWith(compareBy({ it.name }, { it.abi })).joinToString("\n", postfix = "\n") {
            listOf(it.name, it.version, it.abi, it.sha256, it.upstream, it.license, it.licenseSha256, it.size.toString()).joinToString("\t")
        }
    }
    fun decode(manifest: String): List<OfflineToolPackage> {
        require(manifest.length <= 1024 * 1024 && manifest.startsWith(HEADER + "\n")) { "Invalid catalog" }
        val lines = manifest.removeSuffix("\n").split('\n')
        val packages = lines.drop(1).map { line ->
            val fields = line.split('\t')
            require(fields.size == 8) { "Invalid catalog fields" }
            OfflineToolPackage(fields[0], fields[1], fields[2], fields[3], fields[4], fields[5], fields[6], fields[7].toLong())
        }
        require(encode(packages) == manifest) { "Noncanonical catalog" }
        return packages
    }
    fun inspect(packages: List<OfflineToolPackage>, cache: File, expectedAbi: String): OfflineToolCatalogCheck {
        encode(packages) // Validate even when the cache is absent. No mkdir/write/network.
        require(token.matches(expectedAbi)) { "Invalid expected ABI" }
        var ancestor: File? = cache.absoluteFile
        while (ancestor != null) {
            require(!Files.isSymbolicLink(ancestor.toPath())) { "Symlink cache boundary" }
            ancestor = ancestor.parentFile
        }
        fun matches(file: File, sha: String, size: Long? = null): Boolean {
            if (Files.isSymbolicLink(file.toPath()) || !file.isFile ||
                file.canonicalFile.parentFile != file.parentFile.canonicalFile ||
                file.length() > 512L * 1024 * 1024 || (size != null && file.length() != size)) return false
            val digest = MessageDigest.getInstance("SHA-256")
            var count = 0L
            file.inputStream().use { stream ->
                val block = ByteArray(64 * 1024)
                while (true) {
                    val read = stream.read(block)
                    if (read < 0) break
                    count += read
                    if (count > (size ?: (1024 * 1024L))) return false
                    digest.update(block, 0, read)
                }
            }
            return (size == null || count == size) && digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == sha
        }
        val invalid = packages.mapNotNull { pack ->
            val notices = File(cache, "licenses")
            when {
                pack.abi != expectedAbi -> pack.name + ":ABI_MISMATCH"
                !matches(File(cache, pack.sha256), pack.sha256, pack.size) -> pack.name + ":PACKAGE_MISSING_OR_INVALID"
                Files.isSymbolicLink(notices.toPath()) || !matches(File(notices, pack.licenseSha256), pack.licenseSha256) -> pack.name + ":NOTICE_MISSING_OR_INVALID"
                else -> null
            }
        }
        return OfflineToolCatalogCheck(invalid.isEmpty(), invalid)
    }
}
