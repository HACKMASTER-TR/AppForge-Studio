package com.appforge.studio.build

import android.content.Context
import com.appforge.studio.security.V9ReleaseSignatureVerifier
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption

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

    /** Downloads only into app-private canary storage; does not install or set module READY. */
    @Synchronized fun fetchWithConsent(context: Context, manifest: ByteArray, signature: ByteArray,
                         persistedFloor: Long, sha256: String, userApproved: Boolean,
                         canceled: () -> Boolean = { false },
                         onProgress: (Long, Long) -> Unit = { _, _ -> }): File {
        check(userApproved) { "Explicit V9 canary download consent required" }
        val plan = planSignedObject(manifest, signature, persistedFloor, sha256)
        val privateRoot = context.applicationContext.noBackupFilesDir
        check(privateRoot.isDirectory && !Files.isSymbolicLink(privateRoot.toPath()))
        val directory = File(privateRoot, "v9-signed-r2-canary")
        if (!directory.exists()) check(directory.mkdir())
        check(directory.isDirectory && !Files.isSymbolicLink(directory.toPath()))
        check(directory.canonicalFile == directory.absoluteFile)
        val target = File(directory, plan.sha256)
        val partial = File(directory, plan.sha256 + ".part")
        for (f in listOf(target, partial)) check(!Files.isSymbolicLink(f.toPath()))

        // A previously completed object is reusable only after an exact size+digest check.
        if (target.exists()) {
            check(target.isFile && target.length() == plan.bytes)
            target.inputStream().use { V9ReleaseSignatureVerifier.verifyObject(it, plan.bytes, plan.sha256) }
            return target
        }
        check(!partial.exists() || partial.isFile && partial.length() <= plan.bytes)
        while (partial.length() < plan.bytes) {
            if (canceled()) throw java.util.concurrent.CancellationException("V9 canary paused")
            DownloadNetwork.check()
            check(directory.usableSpace > minOf(CHUNK_BYTES, plan.bytes - partial.length()) + 64L * 1024 * 1024) {
                "Insufficient private storage for V9 canary"
            }
            val chunk = nextChunk(partial.length(), plan.bytes)
            val connection = DownloadNetwork.open(URL(plan.url))
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 20_000
                connection.readTimeout = 20_000
                connection.setRequestProperty("Accept-Encoding", "identity")
                connection.setRequestProperty("Range", "bytes=${chunk.start}-${chunk.end}")
                val status = connection.responseCode
                if (status != HttpURLConnection.HTTP_PARTIAL) throw IOException("V9 expected HTTP 206; got $status")
                check(connection.getHeaderField("Content-Range") ==
                    "bytes ${chunk.start}-${chunk.end}/${plan.bytes}") { "V9 Content-Range mismatch" }
                check(connection.contentLengthLong == chunk.length) { "V9 Content-Length mismatch" }
                check(connection.getHeaderField("Content-Encoding") in listOf(null, "identity")) {
                    "V9 compressed range prohibited"
                }
                FileOutputStream(partial, true).use { out ->
                    var remaining = chunk.length
                    var zeroReads = 0
                    val buffer = ByteArray(64 * 1024)
                    connection.inputStream.use { input ->
                        while (remaining > 0) {
                            if (canceled()) throw java.util.concurrent.CancellationException("V9 canary paused")
                            DownloadNetwork.check()
                            val n = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                            if (n < 0) throw IOException("V9 incomplete chunk")
                            if (n == 0) {
                                if (++zeroReads > 32) throw IOException("V9 stalled response")
                                continue
                            }
                            zeroReads = 0
                            out.write(buffer, 0, n)
                            remaining -= n
                        }
                        check(input.read() == -1) { "V9 extra response body" }
                    }
                    out.fd.sync()
                }
                onProgress(partial.length(), plan.bytes)
            } finally {
                connection.disconnect()
                DownloadNetwork.release(connection)
            }
        }
        check(partial.length() == plan.bytes)
        try {
            partial.inputStream().use { V9ReleaseSignatureVerifier.verifyObject(it, plan.bytes, plan.sha256) }
        } catch (failure: Exception) {
            partial.delete() // Never retain an invalid complete object as resumable data.
            throw failure
        }
        check(!target.exists())
        Files.move(partial.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        return target
    }
}
