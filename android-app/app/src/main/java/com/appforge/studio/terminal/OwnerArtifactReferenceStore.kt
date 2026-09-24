package com.appforge.studio.terminal

import android.content.Context
import com.appforge.studio.security.OwnerAccessPolicy
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

internal data class OwnerArtifactReference(
    val id: String,
    val displayName: String,
    val sourceFile: File,
    val sizeBytes: Long,
    val modifiedAt: Long
)

internal object OwnerArtifactReferenceStore {
    private const val STORE_NAME = "appforge-owner-artifact-refs-v1"
    private const val VERSION = 1
    private val referenceIdPattern = Regex("^[0-9a-f]{64}$")

    fun publishLocalExeReference(
        context: Context,
        sourceFile: File,
        displayName: String
    ): File? {
        OwnerAccessPolicy.requireActiveOwner(context)
        val safeName = safeDisplayName(displayName)
        if (!safeName.endsWith(".exe", ignoreCase = true)) return null

        val source = validatedDeviceExe(context, sourceFile) ?: return null
        val ownerTarget = File(OwnerAccessPolicy.apkRoot(context), safeName).canonicalFile
        val sourceDigest = sha256(source)

        if (ownerTarget.exists()) {
            val exactDuplicate =
                ownerTarget.isFile &&
                    ownerTarget.length() == source.length() &&
                    sha256(ownerTarget) == sourceDigest

            if (!exactDuplicate) {
                removeForDisplayName(context, safeName)
                return null
            }
        }

        if (!writeReference(context, source, safeName, sourceDigest)) return null

        if (ownerTarget.isFile && !ownerTarget.delete()) {
            removeForDisplayName(context, safeName)
            return null
        }

        return source
    }

    fun adoptDuplicateExeCopies(context: Context): Int {
        OwnerAccessPolicy.requireActiveOwner(context)
        val ownerApkRoot = OwnerAccessPolicy.apkRoot(context).canonicalFile
        val ownerExeFiles = ownerApkRoot.listFiles().orEmpty().filter {
            it.isFile && it.extension.equals("exe", ignoreCase = true)
        }
        if (ownerExeFiles.isEmpty()) return 0

        val candidates = artifactRoot(context)
            .listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isDirectory }
            .flatMap { it.listFiles().orEmpty().asSequence() }
            .mapNotNull { validatedDeviceExe(context, it) }
            .sortedByDescending { it.lastModified() }
            .toList()

        var adopted = 0
        ownerExeFiles.forEach { ownerExe ->
            val sameSize = candidates.filter { it.length() == ownerExe.length() }
            if (sameSize.isEmpty()) return@forEach

            val ownerDigest = sha256(ownerExe)
            val source = sameSize.firstOrNull { sha256(it) == ownerDigest } ?: return@forEach
            val safeName = safeDisplayName(ownerExe.name)

            if (writeReference(context, source, safeName, ownerDigest)) {
                if (ownerExe.delete()) {
                    adopted += 1
                } else {
                    removeForDisplayName(context, safeName)
                }
            }
        }
        return adopted
    }

    fun list(context: Context): List<OwnerArtifactReference> {
        OwnerAccessPolicy.requireActiveOwner(context)
        return storeRoot(context)
            .listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            .mapNotNull { readReference(context, it) }
            .sortedBy { it.displayName.lowercase() }
            .toList()
    }

    fun remove(context: Context, referenceId: String): Boolean {
        OwnerAccessPolicy.requireActiveOwner(context)
        require(referenceIdPattern.matches(referenceId)) {
            "Owner artifact reference ID invalid."
        }
        val root = storeRoot(context)
        val target = File(root, "$referenceId.json").canonicalFile
        require(target.parentFile == root) {
            "Owner artifact reference escaped private root."
        }
        return !target.exists() || target.delete()
    }

    fun removeForDisplayName(context: Context, displayName: String): Boolean {
        OwnerAccessPolicy.requireActiveOwner(context)
        val safeName = safeDisplayName(displayName)
        val target = referenceFile(context, safeName)
        return !target.exists() || target.delete()
    }

    private fun writeReference(
        context: Context,
        source: File,
        displayName: String,
        digest: String
    ): Boolean = runCatching {
        val root = artifactRoot(context)
        val relative = source.relativeTo(root).invariantSeparatorsPath
        val data = JSONObject()
            .put("version", VERSION)
            .put("displayName", displayName)
            .put("artifactRelativePath", relative)
            .put("sizeBytes", source.length())
            .put("modifiedAt", source.lastModified())
            .put("sha256", digest)

        val target = referenceFile(context, displayName)
        writeAtomic(target, data.toString())
        val resolved = readReference(context, target)
        require(resolved?.sourceFile == source)
        true
    }.getOrElse {
        runCatching { removeForDisplayName(context, displayName) }
        false
    }

    private fun readReference(
        context: Context,
        file: File
    ): OwnerArtifactReference? = runCatching {
        val root = storeRoot(context)
        val safeFile = file.canonicalFile
        require(safeFile.parentFile == root)
        require(safeFile.extension.equals("json", ignoreCase = true))
        require(referenceIdPattern.matches(safeFile.nameWithoutExtension))

        val data = JSONObject(safeFile.readText())
        require(data.optInt("version") == VERSION)

        val displayName = safeDisplayName(data.getString("displayName"))
        require(displayName.endsWith(".exe", ignoreCase = true))

        val relative = data.getString("artifactRelativePath")
        require(relative.isNotBlank() && !relative.startsWith('/') && !relative.startsWith('\\'))
        require(relative.split('/', '\\').none { it == ".." })

        val source = validatedDeviceExe(
            context,
            File(artifactRoot(context), relative)
        ) ?: error("Referenced EXE is unavailable.")

        val size = data.getLong("sizeBytes")
        require(size > 0L && source.length() == size)
        require(referenceIdPattern.matches(data.getString("sha256")))

        OwnerArtifactReference(
            id = safeFile.nameWithoutExtension,
            displayName = displayName,
            sourceFile = source,
            sizeBytes = size,
            modifiedAt = data.optLong("modifiedAt", source.lastModified())
        )
    }.getOrNull()

    private fun validatedDeviceExe(context: Context, candidate: File): File? = runCatching {
        val root = artifactRoot(context)
        val safe = candidate.canonicalFile
        require(safe.isFile && safe.length() > 0L)
        require(safe.extension.equals("exe", ignoreCase = true))

        val buildDirectory = safe.parentFile?.canonicalFile
            ?: error("Artifact build directory missing.")
        require(buildDirectory.parentFile == root)
        require(buildDirectory.name.startsWith("local-"))
        safe
    }.getOrNull()

    private fun artifactRoot(context: Context): File =
        File(context.filesDir, "device-build/artifacts").canonicalFile

    private fun storeRoot(context: Context): File {
        val noBackup = context.noBackupFilesDir.canonicalFile
        val root = File(noBackup, STORE_NAME).canonicalFile
        require(root.path.startsWith(noBackup.path + File.separator))
        require(root.exists() || root.mkdirs()) {
            "Owner artifact reference store create failed."
        }
        return root
    }

    private fun referenceFile(context: Context, displayName: String): File =
        File(
            storeRoot(context),
            "${sha256Text(displayName.lowercase())}.json"
        ).canonicalFile

    private fun safeDisplayName(value: String): String {
        val safe = File(value).name
        require(safe.isNotBlank() && safe == value && safe != "." && safe != "..") {
            "Owner artifact filename invalid."
        }
        return safe
    }

    private fun writeAtomic(target: File, content: String) {
        val root = target.parentFile?.canonicalFile
            ?: error("Owner artifact reference root missing.")
        val temporary = File(root, ".${target.name}.part").canonicalFile
        require(temporary.parentFile == root)

        temporary.delete()
        temporary.writeText(content, Charsets.UTF_8)
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
    }

    private fun sha256Text(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(1024 * 1024).use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") {
            "%02x".format(it.toInt() and 0xff)
        }
    }
}
