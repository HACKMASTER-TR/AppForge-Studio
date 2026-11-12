package com.appforge.studio.build

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Transport primitive, not trust authority. Callers supply signed size/SHA-256.
 * R2's gateway caps ranges at 16 MiB; each request below is at most 8 MiB.
 */
internal object V9R2ObjectTransfer {
    const val MAX_BYTES = 128L * 1024 * 1024
    /** Only an explicitly started, authenticated acquisition may quarantine bad
     * cache bytes. Never remove a working runtime, valid object or user project. */
    @Synchronized fun quarantineDamaged(target:File,sha256:String,bytes:Long,userApproved:Boolean):Boolean {
        check(userApproved)
        require(sha256.matches(Regex("[a-f0-9]{64}")) && bytes in 1..MAX_BYTES)
        require(target.name==sha256 || target.name=="$sha256.archive")
        RecoveryPathAnchor.check(target)
        if(!target.exists())return false
        require(target.isFile && !Files.isSymbolicLink(target.toPath()))
        if(target.length()==bytes && VerifiedModuleDownload.sha(target)==sha256)return false
        val parent=requireNotNull(target.parentFile)
        val quarantine=File(parent,".r2-corrupt")
        require(!Files.isSymbolicLink(quarantine.toPath()))
        if(!quarantine.exists())require(quarantine.mkdir())
        require(quarantine.isDirectory && quarantine.canonicalFile.parentFile==parent.canonicalFile)
        require(quarantine.listFiles().orEmpty().size<16) { "R2 quarantine full; damaged bytes retained for review" }
        val preserved=File(quarantine,"$sha256-"+java.util.UUID.randomUUID())
        Files.move(target.toPath(),preserved.toPath(),StandardCopyOption.ATOMIC_MOVE)
        return true
    }
    fun download(target: File, sha256: String, bytes: Long,
                 canceled: () -> Boolean = { false },
                 open: (URL) -> HttpURLConnection = { DownloadNetwork.open(it) },
                 available: (File) -> Long = { it.usableSpace },
                 progress: (Long, Long) -> Unit = { _, _ -> }): File {
        require(bytes in 1..MAX_BYTES)
        val url = URL(V9SignedR2CandidateDownload.urlForSha(sha256))
        RecoveryPathAnchor.check(target)
        val parent = requireNotNull(target.parentFile)
        parent.mkdirs()
        require(parent.isDirectory && parent.canonicalFile == parent.absoluteFile)
        val partial = File(target.path + ".part")
        for (file in listOf(target, partial)) require(!Files.isSymbolicLink(file.toPath()))
        if (target.exists()) {
            require(target.isFile && target.length() == bytes && VerifiedModuleDownload.sha(target) == sha256) {
                "Corrupt R2 cache; existing object preserved"
            }
            progress(bytes, bytes)
            return target
        }
        require(!partial.exists() || partial.isFile && partial.length() <= bytes)
        while (partial.length() < bytes) {
            if (canceled()) throw java.util.concurrent.CancellationException("R2 paused at transfer checkpoint")
            DownloadNetwork.check()
            val start = partial.length()
            val end = minOf(bytes - 1, start + V9SignedR2CandidateDownload.CHUNK_BYTES - 1)
            require(available(parent) > end - start + 1 + 64L * 1024 * 1024) { "Insufficient R2 storage" }
            val connection = open(url)
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 20_000
                connection.readTimeout = 20_000
                connection.setRequestProperty("Accept-Encoding", "identity")
                connection.setRequestProperty("Range", "bytes=$start-$end")
                if (connection.responseCode != 206) throw IOException("R2 HTTP ${connection.responseCode}; no snapshot fallback")
                require(connection.getHeaderField("Content-Range") == "bytes $start-$end/$bytes") { "R2 Content-Range mismatch" }
                require(connection.contentLengthLong == end - start + 1) { "R2 Content-Length mismatch" }
                require(connection.getHeaderField("Content-Encoding") in listOf(null, "identity"))
                FileOutputStream(partial, true).use { output ->
                    try {
                        connection.inputStream.use { input ->
                            val buffer = ByteArray(64 * 1024)
                            var remaining = end - start + 1
                            var stalls = 0
                            while (remaining > 0) {
                                if (canceled()) throw java.util.concurrent.CancellationException("R2 paused")
                                DownloadNetwork.check()
                                val n = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
                                if (n < 0) throw java.io.EOFException("R2 interrupted; partial bytes retained")
                                if (n == 0) { require(++stalls <= 32); continue }
                                stalls = 0
                                require(available(parent) > n + 64L * 1024 * 1024) { "Insufficient R2 storage" }
                                output.write(buffer, 0, n)
                                remaining -= n
                                progress(start + (end - start + 1 - remaining), bytes)
                            }
                            require(input.read() == -1) { "R2 oversized body" }
                        }
                    } finally { output.fd.sync() }
                }
            } finally { connection.disconnect(); DownloadNetwork.release(connection) }
        }
        require(partial.length() == bytes)
        if (VerifiedModuleDownload.sha(partial) != sha256) {
            partial.delete() // Invalid complete prefixes must never poison retry or become READY.
            throw SecurityException("R2 SHA-256 mismatch; installation denied")
        }
        require(!target.exists() && !Files.isSymbolicLink(target.toPath()))
        Files.move(partial.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        return target
    }
}
