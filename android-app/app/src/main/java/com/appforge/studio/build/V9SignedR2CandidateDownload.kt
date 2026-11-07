package com.appforge.studio.build

import android.content.Context
import java.io.File
import java.nio.file.Files

/** Explicit-consent, read-only V9 canary. This never authorizes package installation. */
internal object V9SignedR2CandidateDownload {
    private const val ORIGIN = "https://appforge-v9-packages-readonly.28550040284a.workers.dev"
    internal const val CHUNK_BYTES = 8L * 1024 * 1024
    private const val MAX_CANARY_BYTES = 40L * 1024 * 1024
    private val HASH = Regex("[a-f0-9]{64}")

    internal data class Chunk(val start: Long, val end: Long) {
        val length: Long get() = end - start + 1
    }

    // Publicly accessible URLs are NOT authorization; only a verified signed manifest is.
    internal fun urlForSha(sha256: String): String {
        require(HASH.matches(sha256)) { "Invalid V9 content address" }
        return "$ORIGIN/v9/objects/sha256/${sha256.substring(0, 2)}/$sha256"
    }

    internal fun nextChunk(offset: Long, total: Long): Chunk {
        require(total in 1..MAX_CANARY_BYTES && offset in 0 until total)
        return Chunk(offset, minOf(total - 1, offset + CHUNK_BYTES - 1))
    }

    internal data class Plan(val sha256: String, val bytes: Long, val url: String,
                             val kind: String, val sequence: Long)

    /** Reject malformed/forged metadata before any network or filesystem operation. */
    internal fun planSignedObject(manifest: ByteArray, signature: ByteArray,
                                  persistedFloor: Long, sha256: String): Plan {
        V9ReleaseCandidatePolicy.requireProductionDisabled()
        val url = urlForSha(sha256)
        val verified = V9ReleaseCandidatePolicy.verify(manifest, signature, persistedFloor)
        val key = "objects/sha256/${sha256.substring(0, 2)}/$sha256"
        val record = verified.objects[key] ?: throw SecurityException("V9 object absent from signed release")
        require(record.sha256 == sha256 && record.bytes in 1..MAX_CANARY_BYTES)
        require(record.kind == "deb" || record.kind == "InRelease")
        return Plan(sha256, record.bytes, url, record.kind, verified.sequence)
    }

    /**
     * Context.noBackupFilesDir can have Android's /data/user/0 parent alias.
     * Canonicalize only the trusted platform root. Reject leaf symlinks and escapes.
     */
    internal fun privateCacheDirectory(privateRoot: File): File {
        check(privateRoot.isDirectory && !Files.isSymbolicLink(privateRoot.toPath())) {
            "Untrusted V9 private root"
        }
        val canonicalRoot = privateRoot.canonicalFile
        check(canonicalRoot.isDirectory && !Files.isSymbolicLink(canonicalRoot.toPath()))
        val directory = File(canonicalRoot, "v9-signed-r2-canary")
        if (!directory.exists()) check(directory.mkdir())
        check(directory.isDirectory && !Files.isSymbolicLink(directory.toPath())) {
            "Symlinked V9 cache directory"
        }
        check(directory.canonicalFile == directory.absoluteFile &&
              directory.canonicalFile.parentFile == canonicalRoot) {
            "V9 private cache escaped the Context root"
        }
        return directory
    }

    /** Downloads only into app-private canary storage; does not install or set module READY. */
    @Synchronized fun fetchWithConsent(context: Context, manifest: ByteArray, signature: ByteArray,
                         persistedFloor: Long, sha256: String, userApproved: Boolean,
                         canceled: () -> Boolean = { false },
                         onProgress: (Long, Long) -> Unit = { _, _ -> }): File {
        check(userApproved) { "Explicit V9 canary download consent required" }
        val plan = planSignedObject(manifest, signature, persistedFloor, sha256)
        val directory = privateCacheDirectory(context.applicationContext.noBackupFilesDir)
        V9R2ObjectTransfer.quarantineDamaged(File(directory,plan.sha256),plan.sha256,plan.bytes,userApproved)
        return V9R2ObjectTransfer.download(File(directory, plan.sha256), plan.sha256, plan.bytes,
            canceled = canceled, progress = onProgress)
    }
}
