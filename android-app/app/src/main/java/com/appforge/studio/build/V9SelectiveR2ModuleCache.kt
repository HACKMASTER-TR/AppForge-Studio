package com.appforge.studio.build

import android.content.Context
import com.appforge.studio.security.V9ReleaseSignatureVerifier
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

/**
 * Opt-in V9 APT prefetch: selected module + dependency closure, shared content-addressed cache.
 * Does not install packages, mark modules READY, route production traffic, or bypass signed release trust.
 * The Android caller must run blocking prefetch off the main thread after explicit approval.
 */
internal object V9SelectiveR2ModuleCache {
    private const val CATALOG_ASSET = "device-build/apt-module-packages-v1.tsv"
    private const val PROFILE_ASSET = "device-build/apt-snapshot-profile.tsv"
    private const val MAX_CATALOG_BYTES = 512 * 1024
    private const val MAX_OBJECT_BYTES = 40L * 1024 * 1024
    private const val MAX_SELECTED_BYTES = 400L * 1024 * 1024
    private val SHA = Regex("[a-f0-9]{64}")
    private val SUPPORTED = setOf(
        OfflineModuleId.ANDROID_CORE, OfflineModuleId.NODE_WEB,
        OfflineModuleId.PYTHON_ANDROID, OfflineModuleId.EXPO_REACT_NATIVE,
        OfflineModuleId.WINDOWS_NATIVE
    )
    private val SUITES = listOf("noble", "noble-updates", "noble-security", "noble-backports")

    internal data class Package(val sha256: String, val bytes: Long, val kind: String) {
        val key: String get() = "objects/sha256/${sha256.substring(0, 2)}/$sha256"
    }
    internal data class Selection(
        val architecture: String,
        val selectedModules: Set<OfflineModuleId>,
        val packages: List<Package>,
        val totalBytes: Long,
        val estimatedGetRequests: Long
    )
    internal data class SignedSelection(
        val sequence: Long, val selection: Selection, val objects: List<Package>
    )
    internal data class CacheStatus(
        val existing: List<Package>, val missing: List<Package>,
        val existingBytes: Long, val networkBytes: Long, val networkGetRequests: Long
    )

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun requirePinned(raw: ByteArray, expected: String, max: Int) {
        require(raw.isNotEmpty() && raw.size <= max && SHA.matches(expected) && sha256(raw) == expected) {
            "V9 asset checksum/size mismatch"
        }
    }

    /** A preview is never installation authority. It is pinned to the app-bundled catalog. */
    internal fun selectRows(
        catalog: ByteArray, selected: Set<OfflineModuleId>, architecture: String,
        expectedPin: String = AptModuleLocks.CATALOG
    ): Selection {
        requirePinned(catalog, expectedPin, MAX_CATALOG_BYTES)
        require(architecture == "arm64" || architecture == "amd64") { "Unknown Ubuntu host architecture" }
        require(selected.isNotEmpty() && selected.size <= SUPPORTED.size && selected.all { it in SUPPORTED }) {
            "Unsupported or empty V9 APT module selection"
        }
        val closure = OfflineModules.resolve(selected).filter { it in SUPPORTED }.toSet()
        val seen = linkedMapOf<String, Package>()
        val moduleCounts = mutableMapOf<OfflineModuleId, Int>()
        for (line in catalog.toString(Charsets.UTF_8).lineSequence().filter { it.isNotEmpty() }) {
            val f = line.split('\t')
            require(f.size in 8..10) { "Malformed V9 APT catalog" }
            val module = runCatching { OfflineModuleId.valueOf(f[0]) }.getOrNull()
            require(module != null && module in SUPPORTED) { "Unrecognized V9 APT module" }
            require(f[1] == "arm64" || f[1] == "amd64")
            require(f[4] == "all" || f[4] == f[1])
            require(SHA.matches(f[5]))
            val bytes = f[6].toLongOrNull()
            require(bytes != null && bytes in 1..MAX_OBJECT_BYTES) { "Invalid V9 package length" }
            require(f[7].startsWith("https://snapshot.ubuntu.com/ubuntu/${AptModuleLocks.SNAPSHOT}/pool/"))
            if (module !in closure || f[1] != architecture) continue
            moduleCounts[module] = (moduleCounts[module] ?: 0) + 1
            val rec = Package(f[5], bytes, "deb")
            val former = seen.putIfAbsent(rec.sha256, rec)
            require(former == null || former.bytes == rec.bytes) { "Conflicting content-addressed packages" }
        }
        for (module in closure) require((moduleCounts[module] ?: 0) > 0) {
            "Missing pinned V9 packages for $module/$architecture"
        }
        require(seen.isNotEmpty() && seen.size <= 214)
        val sorted = seen.values.sortedBy { it.sha256 }
        val total = sorted.sumOf { it.bytes }
        require(total in 1..MAX_SELECTED_BYTES)
        return Selection(architecture, selected.toSet(), sorted, total,
            sorted.sumOf { chunkCount(it.bytes) })
    }

    internal fun chunkCount(size: Long): Long {
        require(size in 1..MAX_OBJECT_BYTES)
        val block = V9SignedR2CandidateDownload.CHUNK_BYTES
        return (size - 1) / block + 1
    }

    private fun pinnedProfile(profile: ByteArray, expectedPin: String = AptModuleLocks.PROFILE): List<String> {
        requirePinned(profile, expectedPin, 4096)
        val lines = profile.toString(Charsets.UTF_8).lineSequence().filter { it.isNotBlank() }.toList()
        require(lines.size == SUITES.size)
        return lines.mapIndexed { i, line ->
            val fields = line.split('\t')
            require(fields.size == 2 && fields[0] == SUITES[i] && SHA.matches(fields[1]))
            fields[1]
        }
    }

    private fun asset(context: Context, path: String, max: Int): ByteArray {
        val bytes = context.applicationContext.assets.open(path).use { it.readBytes() }
        require(bytes.isNotEmpty() && bytes.size <= max)
        return bytes
    }

    /** Catalog-only, offline and safe to display before a signed release exists. */
    fun preview(context: Context, selected: Set<OfflineModuleId>, architecture: String): Selection =
        selectRows(asset(context, CATALOG_ASSET, MAX_CATALOG_BYTES), selected, architecture)

    /** Only the pinned Ed25519-signed V9 release can authorize object transfer. */
    fun signedPlan(context: Context, manifest: ByteArray, signature: ByteArray,
                   persistedFloor: Long, selected: Set<OfflineModuleId>, architecture: String): SignedSelection {
        V9ReleaseCandidatePolicy.requireProductionDisabled()
        val verified = V9ReleaseCandidatePolicy.verify(manifest, signature, persistedFloor)
        val packages = preview(context, selected, architecture)
        val releaseDigests = pinnedProfile(asset(context, PROFILE_ASSET, 4096))
        val requested = (packages.packages + releaseDigests.map { sha ->
            val key = "objects/sha256/${sha.substring(0, 2)}/$sha"
            val record = verified.objects[key] ?: throw SecurityException("Signed V9 release missing Ubuntu InRelease")
            require(record.kind == "InRelease")
            Package(sha, record.bytes, "InRelease")
        }).distinctBy { it.sha256 }
        require(requested.size <= 218)
        for (item in requested) {
            val signed = verified.objects[item.key] ?: throw SecurityException("Object not in signed V9 release")
            require(signed.sha256 == item.sha256 && signed.bytes == item.bytes && signed.kind == item.kind) {
                "V9 catalog/release metadata mismatch"
            }
            require(item.bytes in 1..MAX_OBJECT_BYTES)
        }
        require(requested.sumOf { it.bytes } <= MAX_SELECTED_BYTES)
        return SignedSelection(verified.sequence, packages, requested)
    }

    private fun directory(context: Context): File =
        V9SignedR2CandidateDownload.privateCacheDirectory(
            context.applicationContext.noBackupFilesDir
        )

    /** Revalidates every reused byte. Invalid cache content fails closed, never becomes READY. */
    internal fun inspectCache(cacheDir: File, objects: List<Package>): CacheStatus {
        require(cacheDir.isDirectory && !Files.isSymbolicLink(cacheDir.toPath()))
        val existing = ArrayList<Package>()
        val missing = ArrayList<Package>()
        for (item in objects) {
            require(SHA.matches(item.sha256) && item.bytes in 1..MAX_OBJECT_BYTES)
            val cached = File(cacheDir, item.sha256)
            require(!Files.isSymbolicLink(cached.toPath()))
            if (!cached.exists()) { missing += item; continue }
            if (!cached.isFile || cached.length() != item.bytes) throw SecurityException("Corrupt V9 cache length")
            cached.inputStream().use { V9ReleaseSignatureVerifier.verifyObject(it, item.bytes, item.sha256) }
            existing += item
        }
        return CacheStatus(existing, missing, existing.sumOf { it.bytes },
            missing.sumOf { it.bytes }, missing.sumOf { chunkCount(it.bytes) })
    }

    fun status(context: Context, plan: SignedSelection): CacheStatus = inspectCache(directory(context), plan.objects)

    /** Blocking opt-in prefetch. One verified content-addressed file is shared across selected modules. */
    @Synchronized fun fetchSelectedWithConsent(
        context: Context, manifest: ByteArray, signature: ByteArray, persistedFloor: Long,
        selected: Set<OfflineModuleId>, architecture: String, userApproved: Boolean,
        canceled: () -> Boolean = { false }, onProgress: (Int, Int, Long, Long) -> Unit = { _, _, _, _ -> }
    ): Map<String, File> {
        check(userApproved) { "User approval required for V9 selective package prefetch" }
        val plan = signedPlan(context, manifest, signature, persistedFloor, selected, architecture)
        val cache = directory(context)
        // User explicitly restarted an authenticated plan. Preserve bad cache
        // entries separately before a clean re-download; inspection alone never mutates.
        for(item in plan.objects)V9R2ObjectTransfer.quarantineDamaged(File(cache,item.sha256),item.sha256,item.bytes,userApproved)
        val before = inspectCache(cache, plan.objects)
        val available = cache.usableSpace
        if (before.missing.isNotEmpty()) require(available > before.networkBytes + 64L * 1024 * 1024) {
            "Insufficient space for selected V9 packages"
        }
        val result = linkedMapOf<String, File>()
        val count = plan.objects.size
        for ((index, obj) in plan.objects.withIndex()) {
            if (canceled()) throw java.util.concurrent.CancellationException("V9 prefetch canceled")
            val file = V9SignedR2CandidateDownload.fetchWithConsent(
                context, manifest, signature, persistedFloor, obj.sha256, true,
                canceled = canceled,
                onProgress = { done, total -> onProgress(index + 1, count, done, total) }
            )
            // The canary verifies stored content as well, including on cache reuse.
            check(file.parentFile.canonicalFile == cache.canonicalFile && file.name == obj.sha256)
            check(file.length() == obj.bytes)
            result[obj.sha256] = file
        }
        // No package manager call or READY transition: installation remains separately gated.
        return result
    }
}
