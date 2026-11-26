package com.appforge.studio.build

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Logical transaction: no caller visibility until return. Renames are per-file, with rollback. */
internal object AndroidArtifactPublication {
    fun collect(
        privateRoot: File,
        jobId: String,
        sourceRoot: File,
        sources: Map<String, File?>,
        checkCancelled: () -> Unit,
        verify: (String, File) -> Unit,
        copy: (File, File) -> Unit = { source, target ->
            source.inputStream().use { input -> target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    checkCancelled()
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
            } }
        },
        finalize: (File, File) -> Unit = { source, target -> check(source.renameTo(target)) { "Artifact finalize failed." } }
    ): Map<String, File> {
        require(jobId.matches(Regex("local-[a-zA-Z0-9]+")))
        require(sources.isNotEmpty() && sources.keys.all { it == "apk" || it == "aab" })
        val root = privateRoot.canonicalFile
        check(root.mkdirs() || root.isDirectory)
        fun bounded(parent: File, file: File): File = file.canonicalFile.also {
            require(it.path.startsWith(parent.canonicalPath + File.separator)) { "Artifact path escapes private bounds." }
        }
        val job = bounded(root, File(root, jobId))
        val stage = bounded(root, File(root, "$jobId-pending"))
        // A job identity is never reused for another build; remove interrupted private publication.
        fun cleanup() {
            for (directory in listOf(stage, job)) {
                check(!directory.exists() || directory.deleteRecursively()) { "Artifact rollback failed." }
            }
        }
        cleanup()
        try {
            checkCancelled()
            val selected = sources.mapValues { (_, file) ->
                val source = bounded(sourceRoot, requireNotNull(file) { "Requested Android artifact missing." })
                require(source.isFile && source.length() in 1..2_147_483_647L) { "Invalid Android artifact size." }
                source
            }
            check(stage.mkdir())
            val staged = selected.mapValues { (kind, source) ->
                checkCancelled()
                verifyFormat(kind, source)
                verify(kind, source)
                val digest = digest(source)
                val target = bounded(stage, File(stage, "output.$kind"))
                copy(source, target)
                checkCancelled()
                require(target.isFile && target.length() == source.length() && digest(target) == digest) { "Artifact copy mismatch." }
                verifyFormat(kind, target)
                verify(kind, target)
                target
            }
            check(job.mkdir())
            val result = staged.mapValues { (kind, source) ->
                checkCancelled()
                val target = bounded(job, File(job, "output.$kind"))
                finalize(source, target)
                require(target.isFile && target.length() > 0)
                target
            }
            checkCancelled()
            check(stage.delete())
            return result
        } catch (failure: Throwable) {
            try { cleanup() } catch (rollback: Throwable) { failure.addSuppressed(rollback) }
            throw failure
        }
    }

    private fun digest(file: File): List<Byte> {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val n = input.read(buffer); if (n < 0) break; hash.update(buffer, 0, n) }
        }
        return hash.digest().toList()
    }

    internal fun verifyFormat(kind: String, file: File) {
        ZipFile(file).use { zip ->
            val required = if (kind == "apk") listOf("AndroidManifest.xml")
                else listOf("BundleConfig.pb", "base/manifest/AndroidManifest.xml")
            required.forEach { name -> require(zip.getEntry(name)?.let { !it.isDirectory && it.size > 0 } == true) {
                "Invalid $kind structure."
            } }
            require(zip.entries().asSequence().map { it.name }.toList().let { it.size == it.distinct().size })
        }
    }
}
