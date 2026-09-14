package com.appforge.studio.ai

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal data class AppForgeAgentProjectCheckpoint(
    val id: String,
    val label: String,
    val createdAtEpochMs: Long,
    val fileCount: Int,
    val totalBytes: Long
)

internal data class AppForgeAgentProjectRollbackResult(
    val checkpoint: AppForgeAgentProjectCheckpoint,
    val restoredFiles: Int,
    val restoredBytes: Long
)

internal class AppForgeAgentProjectMemoryStore(
    private val filesDir: File,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val lock = Any()

    private val workspaceRoot: File
        get() = File(filesDir, "unified-agent-workspaces").canonicalFile

    private val memoryRoot: File
        get() = File(filesDir, "unified-agent-project-memory").canonicalFile

    fun createCheckpoint(
        sessionId: String,
        workspace: File,
        label: String
    ): AppForgeAgentProjectCheckpoint = synchronized(lock) {
        val safeSessionId = requireSafeId(sessionId, "sessionId")
        val safeWorkspace = requireWorkspace(workspace)
        val safeLabel = AppForgeAgentArtifactSafety
            .sanitize(label.trim(), 80)
            .ifBlank { "checkpoint" }

        val checkpointId =
            "${clock()}-${UUID.randomUUID().toString().replace("-", "").take(10)}"

        val checkpoints = checkpointRoot(safeSessionId)
        checkpoints.mkdirs()

        val temporary = File(checkpoints, ".tmp-$checkpointId")
        val target = File(checkpoints, checkpointId)

        require(
            temporary.canonicalFile.parentFile == checkpoints.canonicalFile &&
                target.canonicalFile.parentFile == checkpoints.canonicalFile
        ) {
            "Checkpoint yolu güvenli değil."
        }

        temporary.deleteRecursively()
        temporary.mkdirs()

        val payload = File(temporary, "payload").apply { mkdirs() }
        val entries = JSONArray()
        var fileCount = 0
        var totalBytes = 0L

        safeWorkspace.walkTopDown()
            .filter { it.isFile }
            .forEach { source ->
                require(!Files.isSymbolicLink(source.toPath())) {
                    "Sembolik link checkpoint'e alınamaz."
                }

                val canonical = source.canonicalFile
                require(canonical.toPath().startsWith(safeWorkspace.toPath())) {
                    "Workspace dışındaki dosya checkpoint'e alınamaz."
                }

                fileCount += 1
                require(fileCount <= MAX_FILES) {
                    "Checkpoint dosya limiti aşıldı."
                }

                totalBytes += canonical.length()
                require(totalBytes <= MAX_TOTAL_BYTES) {
                    "Checkpoint boyut limiti aşıldı."
                }

                val relative = safeWorkspace.toPath()
                    .relativize(canonical.toPath())
                    .toString()
                    .replace(File.separatorChar, '/')

                requireSafeRelativePath(relative)

                val destination = File(payload, relative)
                destination.parentFile?.mkdirs()
                canonical.copyTo(destination, overwrite = false)

                entries.put(
                    JSONObject().apply {
                        put("path", relative)
                        put("size", canonical.length())
                        put("sha256", sha256(canonical))
                    }
                )
            }

        val summary = AppForgeAgentProjectCheckpoint(
            id = checkpointId,
            label = safeLabel,
            createdAtEpochMs = clock(),
            fileCount = fileCount,
            totalBytes = totalBytes
        )

        val manifest = JSONObject().apply {
            put("format", FORMAT)
            put("checkpointId", summary.id)
            put("label", summary.label)
            put("createdAtEpochMs", summary.createdAtEpochMs)
            put("fileCount", summary.fileCount)
            put("totalBytes", summary.totalBytes)
            put("files", entries)
        }.toString().toByteArray(Charsets.UTF_8)

        require(manifest.size <= MAX_MANIFEST_BYTES) {
            "Checkpoint manifest limiti aşıldı."
        }

        syncWrite(File(temporary, "manifest.json"), manifest)

        require(temporary.renameTo(target)) {
            temporary.deleteRecursively()
            "Checkpoint etkinleştirilemedi."
        }

        prune(safeSessionId)
        summary
    }

    fun listCheckpoints(
        sessionId: String,
        limit: Int = MAX_CHECKPOINTS
    ): List<AppForgeAgentProjectCheckpoint> = synchronized(lock) {
        require(limit in 1..MAX_CHECKPOINTS) {
            "Checkpoint listesi limiti geçersiz."
        }

        val safeSessionId = requireSafeId(sessionId, "sessionId")
        checkpointRoot(safeSessionId)
            .listFiles()
            .orEmpty()
            .filter { it.isDirectory && !it.name.startsWith(".tmp-") }
            .mapNotNull(::readSummary)
            .sortedByDescending { it.createdAtEpochMs }
            .take(limit)
    }

    fun rollbackLatest(
        sessionId: String,
        workspace: File
    ): AppForgeAgentProjectRollbackResult = synchronized(lock) {
        val latest = listCheckpoints(sessionId, 1).firstOrNull()
            ?: error("Geri alınacak checkpoint bulunamadı.")

        rollbackTo(sessionId, latest.id, workspace)
    }

    fun rollbackTo(
        sessionId: String,
        checkpointId: String,
        workspace: File
    ): AppForgeAgentProjectRollbackResult = synchronized(lock) {
        val safeSessionId = requireSafeId(sessionId, "sessionId")
        val safeCheckpointId = requireSafeId(checkpointId, "checkpointId")
        val safeWorkspace = requireWorkspace(workspace)
        val root = checkpointRoot(safeSessionId).canonicalFile
        val checkpoint = File(root, safeCheckpointId).canonicalFile

        require(checkpoint.parentFile == root && checkpoint.isDirectory) {
            "Checkpoint bulunamadı."
        }

        val manifestFile = File(checkpoint, "manifest.json")
        require(
            manifestFile.isFile &&
                manifestFile.length() in 1..MAX_MANIFEST_BYTES.toLong()
        ) {
            "Checkpoint manifest geçersiz."
        }

        val manifest = JSONObject(manifestFile.readText(Charsets.UTF_8))
        require(manifest.getString("format") == FORMAT) {
            "Checkpoint formatı desteklenmiyor."
        }

        val summary = summaryFromManifest(manifest)
        require(summary.id == safeCheckpointId) {
            "Checkpoint kimliği uyuşmuyor."
        }

        val payload = File(checkpoint, "payload").canonicalFile
        require(payload.parentFile == checkpoint && payload.isDirectory) {
            "Checkpoint payload geçersiz."
        }

        val verified = mutableListOf<Triple<String, File, Long>>()
        val files = manifest.getJSONArray("files")
        var verifiedBytes = 0L

        require(files.length() <= MAX_FILES) {
            "Checkpoint dosya limiti geçersiz."
        }

        for (index in 0 until files.length()) {
            val item = files.getJSONObject(index)
            val relative = item.getString("path")
            requireSafeRelativePath(relative)

            val source = File(payload, relative).canonicalFile
            require(source.toPath().startsWith(payload.toPath()) && source.isFile) {
                "Checkpoint kaynak dosyası geçersiz."
            }
            require(!Files.isSymbolicLink(source.toPath())) {
                "Checkpoint sembolik link içeriyor."
            }

            val expectedSize = item.getLong("size")
            val expectedSha = item.getString("sha256")

            require(source.length() == expectedSize && sha256(source) == expectedSha) {
                "Checkpoint bütünlük doğrulaması başarısız: $relative"
            }

            verifiedBytes += expectedSize
            require(verifiedBytes <= MAX_TOTAL_BYTES) {
                "Checkpoint toplam boyutu geçersiz."
            }

            verified += Triple(relative, source, expectedSize)
        }

        safeWorkspace.listFiles().orEmpty().forEach { child ->
            require(child.canonicalFile.parentFile == safeWorkspace) {
                "Workspace temizleme sınırı ihlal edildi."
            }

            if (Files.isSymbolicLink(child.toPath())) {
                Files.deleteIfExists(child.toPath())
            } else {
                require(child.deleteRecursively()) {
                    "Workspace rollback için temizlenemedi."
                }
            }
        }

        var restoredFiles = 0
        var restoredBytes = 0L

        verified.forEach { (relative, source, size) ->
            val destination = File(safeWorkspace, relative).canonicalFile
            require(destination.toPath().startsWith(safeWorkspace.toPath())) {
                "Rollback hedef yolu workspace dışına çıkıyor."
            }

            destination.parentFile?.mkdirs()
            source.copyTo(destination, overwrite = false)
            restoredFiles += 1
            restoredBytes += size
        }

        AppForgeAgentProjectRollbackResult(
            checkpoint = summary,
            restoredFiles = restoredFiles,
            restoredBytes = restoredBytes
        )
    }

    fun clearSession(sessionId: String) = synchronized(lock) {
        val safeSessionId = requireSafeId(sessionId, "sessionId")
        val root = memoryRoot
        root.mkdirs()
        val session = File(root, safeSessionId).canonicalFile

        if (session.exists()) {
            require(session.parentFile == root) {
                "Project memory temizleme sınırı ihlal edildi."
            }
            session.deleteRecursively()
        }
    }

    private fun requireWorkspace(workspace: File): File {
        val root = workspaceRoot
        val canonical = workspace.canonicalFile

        require(root.isDirectory) {
            "Unified Agent workspace kökü bulunamadı."
        }
        require(canonical.parentFile == root && canonical.isDirectory) {
            "Project memory yalnız Unified Agent workspace'lerinde çalışabilir."
        }

        return canonical
    }

    private fun checkpointRoot(sessionId: String): File {
        val root = memoryRoot
        root.mkdirs()

        val sessionRoot = File(root, sessionId).canonicalFile
        require(sessionRoot.parentFile == root) {
            "Project memory session yolu güvenli değil."
        }

        return File(sessionRoot, "checkpoints")
    }

    private fun readSummary(checkpoint: File): AppForgeAgentProjectCheckpoint? =
        runCatching {
            val manifestFile = File(checkpoint, "manifest.json")
            require(manifestFile.isFile)
            val manifest = JSONObject(manifestFile.readText(Charsets.UTF_8))
            require(manifest.getString("format") == FORMAT)
            summaryFromManifest(manifest)
        }.getOrNull()

    private fun summaryFromManifest(
        manifest: JSONObject
    ): AppForgeAgentProjectCheckpoint =
        AppForgeAgentProjectCheckpoint(
            id = requireSafeId(
                manifest.getString("checkpointId"),
                "checkpointId"
            ),
            label = AppForgeAgentArtifactSafety.sanitize(
                manifest.optString("label", "checkpoint"),
                80
            ),
            createdAtEpochMs = manifest.getLong("createdAtEpochMs"),
            fileCount = manifest.getInt("fileCount"),
            totalBytes = manifest.getLong("totalBytes")
        )

    private fun prune(sessionId: String) {
        val root = checkpointRoot(sessionId)
        val keep = root.listFiles().orEmpty()
            .filter { it.isDirectory && !it.name.startsWith(".tmp-") }
            .mapNotNull { file ->
                readSummary(file)?.let { it to file }
            }
            .sortedByDescending { it.first.createdAtEpochMs }

        keep.drop(MAX_CHECKPOINTS).forEach { (_, file) ->
            if (file.canonicalFile.parentFile == root.canonicalFile) {
                file.deleteRecursively()
            }
        }

        root.listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith(".tmp-") }
            .forEach { file ->
                if (file.canonicalFile.parentFile == root.canonicalFile) {
                    file.deleteRecursively()
                }
            }
    }

    private fun requireSafeRelativePath(value: String) {
        require(
            value.isNotBlank() &&
                !value.startsWith("/") &&
                !value.startsWith("\\") &&
                value.split('/', '\\').none { it == ".." || it.isBlank() }
        ) {
            "Checkpoint relative path geçersiz."
        }
    }

    private fun requireSafeId(value: String, field: String): String {
        val clean = value.trim()
        require(
            clean.length in 8..96 &&
                clean.all { it.isLetterOrDigit() || it == '-' || it == '_' }
        ) {
            "$field geçersiz."
        }
        return clean
    }

    private fun syncWrite(file: File, bytes: ByteArray) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { output ->
            output.write(bytes)
            output.flush()
            output.fd.sync()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }

        return digest.digest().joinToString("") {
            "%02x".format(it)
        }
    }

    private companion object {
        const val FORMAT = "APPFORGE_PROJECT_CHECKPOINT_V1"
        const val MAX_CHECKPOINTS = 8
        const val MAX_FILES = 2_048
        const val MAX_TOTAL_BYTES = 64L * 1024L * 1024L
        const val MAX_MANIFEST_BYTES = 2 * 1024 * 1024
    }
}
