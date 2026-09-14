package com.appforge.studio.ai

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

internal data class AppForgeWorkspaceCheckpoint(
    val id: String,
    val workspacePath: String,
    val manifestPath: String,
    val projectDigestSha256: String
)

internal data class AppForgeWorkspaceApplyResult(
    val checkpoint: AppForgeWorkspaceCheckpoint,
    val writtenPaths: List<String>
)

internal object AppForgeAgentWorkspaceTransaction {
    private const val CONTROL_DIR = ".appforge-agent-v4"
    private const val MAX_BACKUP_FILE_BYTES = 4L * 1024L * 1024L
    private const val MAX_BACKUP_TOTAL_BYTES = 16L * 1024L * 1024L

    private val forbiddenLeafNames = setOf(
        ".env",
        "id_rsa",
        "id_ed25519",
        "google-services.json"
    )

    private val forbiddenExtensions = setOf(
        "jks",
        "keystore",
        "p12",
        "pfx",
        "pem",
        "key"
    )

    fun apply(
        workspace: File,
        project: AppForgeGeneratedProject
    ): AppForgeWorkspaceApplyResult {
        val root = requireWorkspace(workspace)
        require(project.files.isNotEmpty()) { "Uygulanacak proje dosyası yok." }

        val files = project.files.map { generated ->
            val relative = validateRelativePath(generated.path)
            relative to generated
        }

        val duplicate = files
            .groupBy { it.first.lowercase() }
            .entries
            .firstOrNull { it.value.size > 1 }
        require(duplicate == null) {
            "Workspace transaction yinelenen yol içeriyor: ${duplicate?.key}"
        }

        val id = UUID.randomUUID().toString()
        val control = File(root, CONTROL_DIR).apply { mkdirs() }
        val staging = File(control, "staging/$id")
        val checkpointDir = File(control, "checkpoints/$id")
        val backupRoot = File(checkpointDir, "backup")
        require(staging.mkdirs()) { "Staging klasörü oluşturulamadı." }
        require(backupRoot.mkdirs()) { "Checkpoint klasörü oluşturulamadı." }

        try {
            files.forEach { (relative, generated) ->
                val staged = safeResolve(staging, relative)
                staged.parentFile?.mkdirs()
                staged.writeText(generated.content, Charsets.UTF_8)
                require(sha256(staged.readBytes()) == sha256(generated.content.toByteArray(Charsets.UTF_8))) {
                    "Staging doğrulaması başarısız: $relative"
                }
            }

            var backupBytes = 0L
            val manifestLines = mutableListOf<String>()

            files.forEach { (relative, _) ->
                val target = safeResolve(root, relative)
                val existed = target.exists()
                require(!existed || target.isFile) {
                    "Hedef normal dosya değil: $relative"
                }

                if (existed) {
                    require(target.length() <= MAX_BACKUP_FILE_BYTES) {
                        "Checkpoint için dosya çok büyük: $relative"
                    }
                    backupBytes += target.length()
                    require(backupBytes <= MAX_BACKUP_TOTAL_BYTES) {
                        "Checkpoint toplam yedek sınırını aşıyor."
                    }

                    val backup = safeResolve(backupRoot, relative)
                    backup.parentFile?.mkdirs()
                    Files.copy(
                        target.toPath(),
                        backup.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES
                    )
                    manifestLines += "$relative\t1\t${sha256(backup.readBytes())}"
                } else {
                    manifestLines += "$relative\t0\t-"
                }
            }

            val manifest = File(checkpointDir, "manifest.tsv")
            manifest.writeText(manifestLines.joinToString("\n", postfix = "\n"), Charsets.UTF_8)

            files.forEach { (relative, generated) ->
                val staged = safeResolve(staging, relative)
                val target = safeResolve(root, relative)
                target.parentFile?.mkdirs()

                val temporary = File(target.parentFile, ".${target.name}.appforge-${id.take(8)}.tmp")
                temporary.writeText(generated.content, Charsets.UTF_8)
                require(sha256(temporary.readBytes()) == sha256(staged.readBytes())) {
                    "Commit öncesi içerik doğrulaması başarısız: $relative"
                }
                moveReplacing(temporary, target)
            }

            val checkpoint = AppForgeWorkspaceCheckpoint(
                id = id,
                workspacePath = root.canonicalPath,
                manifestPath = manifest.canonicalPath,
                projectDigestSha256 = project.digestSha256
            )

            File(checkpointDir, "state.txt").writeText("APPLIED\n", Charsets.UTF_8)
            return AppForgeWorkspaceApplyResult(
                checkpoint = checkpoint,
                writtenPaths = files.map { it.first }
            )
        } catch (error: Throwable) {
            staging.deleteRecursively()
            checkpointDir.deleteRecursively()
            throw error
        } finally {
            staging.deleteRecursively()
        }
    }

    fun rollback(checkpoint: AppForgeWorkspaceCheckpoint) {
        val root = requireWorkspace(File(checkpoint.workspacePath))
        val manifest = File(checkpoint.manifestPath)
        require(manifest.isFile) { "Rollback manifesti bulunamadı." }

        val checkpointDir = manifest.parentFile
        val expectedParent = safeResolve(File(root, "$CONTROL_DIR/checkpoints"), checkpoint.id)
        require(checkpointDir.canonicalFile == expectedParent.canonicalFile) {
            "Checkpoint workspace sınırları dışında."
        }

        val backupRoot = File(checkpointDir, "backup")
        val rows = manifest.readLines(Charsets.UTF_8)
            .filter { it.isNotBlank() }
            .map { line ->
                val parts = line.split('\t')
                require(parts.size == 3) { "Rollback manifesti bozuk." }
                Triple(validateRelativePath(parts[0]), parts[1], parts[2])
            }

        rows.asReversed().forEach { (relative, existedFlag, digest) ->
            val target = safeResolve(root, relative)
            when (existedFlag) {
                "1" -> {
                    val backup = safeResolve(backupRoot, relative)
                    require(backup.isFile) { "Rollback yedeği eksik: $relative" }
                    require(sha256(backup.readBytes()) == digest) {
                        "Rollback yedeği bütünlük kontrolünden geçmedi: $relative"
                    }
                    target.parentFile?.mkdirs()
                    val temporary = File(target.parentFile, ".${target.name}.rollback-${checkpoint.id.take(8)}.tmp")
                    Files.copy(backup.toPath(), temporary.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    moveReplacing(temporary, target)
                }

                "0" -> {
                    if (target.exists()) {
                        require(target.isFile) { "Rollback silme hedefi dosya değil: $relative" }
                        require(target.delete()) { "Rollback dosyayı silemedi: $relative" }
                    }
                }

                else -> error("Rollback manifest bayrağı geçersiz.")
            }
        }

        File(checkpointDir, "state.txt").writeText("ROLLED_BACK\n", Charsets.UTF_8)
    }

    private fun requireWorkspace(workspace: File): File {
        require(workspace.exists() && workspace.isDirectory) {
            "Workspace klasörü bulunamadı."
        }
        return workspace.canonicalFile
    }

    private fun validateRelativePath(raw: String): String {
        val path = raw.trim().replace('\\', '/')
        require(path.isNotBlank()) { "Dosya yolu boş olamaz." }
        require(!path.startsWith('/')) { "Mutlak yol yasak: $path" }
        require(!Regex("^[A-Za-z]:/").containsMatchIn(path)) { "Windows mutlak yolu yasak: $path" }

        val parts = path.split('/')
        require(parts.none { it.isBlank() || it == "." || it == ".." }) {
            "Path traversal yasak: $path"
        }
        require(parts.first().lowercase() !in setOf(".git", CONTROL_DIR.lowercase())) {
            "Korunan AppForge/Git yolu yasak: $path"
        }

        val leaf = parts.last().lowercase()
        require(leaf !in forbiddenLeafNames) { "Hassas dosya hedefi yasak: $path" }
        val extension = leaf.substringAfterLast('.', missingDelimiterValue = "")
        require(extension !in forbiddenExtensions) { "Hassas dosya uzantısı yasak: $path" }

        return parts.joinToString("/")
    }

    private fun safeResolve(root: File, relative: String): File {
        val canonicalRoot = root.canonicalFile
        val candidate = File(canonicalRoot, relative).canonicalFile
        val prefix = canonicalRoot.path.trimEnd(File.separatorChar) + File.separator
        require(candidate.path.startsWith(prefix)) {
            "Workspace dışına yazma girişimi engellendi: $relative"
        }
        return candidate
    }

    private fun moveReplacing(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
}
