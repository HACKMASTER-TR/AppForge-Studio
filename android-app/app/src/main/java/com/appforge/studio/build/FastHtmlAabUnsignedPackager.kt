package com.appforge.studio.build

import android.content.Context
import com.appforge.studio.model.ProjectDraft
import com.appforge.studio.model.SourceMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Internal unsigned AAB candidate only. No signing, routing, publishing or runtime invocation. */
internal object FastHtmlAabUnsignedPackager {
    private const val LIMIT = 40 * 1024 * 1024
    private const val ENTRY_LIMIT = 24 * 1024 * 1024
    private const val PINNED_TEMPLATE_SHA256 = "17ef1e5eb5d5a54edd03b62afe9acc61d34b7380cc7f0b512b130b593d94efad"
    private val required = setOf(
        "BundleConfig.pb", "base/manifest/AndroidManifest.xml",
        "base/dex/classes.dex", "base/resources.pb"
    )

    fun buildUnsignedCandidate(context: Context, draft: ProjectDraft, workspace: File): File {
        val snapshot = draft.copy()
        requireSource(snapshot)
        require(workspace.isDirectory && !java.nio.file.Files.isSymbolicLink(workspace.toPath())) {
            "Existing internal workspace required."
        }
        val project = FastHtmlApkPackager.readHtmlProject(snapshot)
        val additions = LinkedHashMap<String, ByteArray>()
        for ((path, value) in project.second) {
            require(path.startsWith("assets/site/"))
            val name = "base/$path"
            FastHtmlAabVerification.requireSafeName(name, false)
            require(additions.put(name, value) == null) { "Duplicate source ZIP path." }
        }
        val jsonEntry = project.first
        require(additions.containsKey("base/assets/site/$jsonEntry")) {
            "Missing HTML entry in AAB additions."
        }
        val config = org.json.JSONObject().put("mode", "LOCAL").put("url", "")
            .put("entry", jsonEntry).toString().toByteArray(Charsets.UTF_8)
        additions["base/assets/appforge-html-config.json"] = config

        val template = context.assets.open("html-aab-template.aab").use { readLimited(it, LIMIT) }
        require(digest(template) == PINNED_TEMPLATE_SHA256) {
            "Unknown CI HTML AAB template. Offline unsigned build blocked."
        }
        val entries = readTemplate(template)
        val manifest = FastHtmlAabProtoManifest.rewrite(
            entries.getValue("base/manifest/AndroidManifest.xml"), snapshot
        )
        require(additions.keys.none { it in entries }) { "Template/source ZIP entry collision." }
        val outputEntries = LinkedHashMap(entries)
        outputEntries["base/manifest/AndroidManifest.xml"] = manifest
        outputEntries.putAll(additions)
        require(outputEntries.size <= 2048 && outputEntries.values.sumOf { it.size.toLong() } <= LIMIT) {
            "Unsigned AAB candidate exceeds entry or expanded size budget."
        }
        val temp = File.createTempFile("appforge-internal-unsigned-", ".aab", workspace)
        var ready = false
        try {
            ZipOutputStream(temp.outputStream().buffered()).use { zip ->
                for ((name, data) in outputEntries) {
                    FastHtmlAabVerification.requireSafeName(name, false)
                    require(data.size <= ENTRY_LIMIT)
                    val entry = ZipEntry(name).apply { time = 315532800000L }
                    zip.putNextEntry(entry)
                    zip.write(data)
                    zip.closeEntry()
                }
            }
            require(temp.length() in 1024..LIMIT.toLong()) { "Invalid unsigned AAB candidate length." }
            // Independently read the *written central directory* and decompressed bytes.
            ZipFile(temp).use { written ->
                val names = HashSet<String>()
                val all = written.entries()
                var total = 0L
                while (all.hasMoreElements()) {
                    val item = all.nextElement()
                    FastHtmlAabVerification.requireSafeName(item.name, item.isDirectory)
                    require(!item.isDirectory && names.add(item.name))
                    val expected = outputEntries[item.name] ?: error("Unexpected unsigned candidate ZIP entry.")
                    require(item.size == expected.size.toLong())
                    val actual = written.getInputStream(item).use { readLimited(it, ENTRY_LIMIT) }
                    total += actual.size
                    require(total <= LIMIT && actual.contentEquals(expected)) {
                        "Unsigned candidate payload mismatch."
                    }
                }
                require(names == outputEntries.keys) { "Missing unsigned candidate ZIP entry." }
            }
            ready = true
            return temp
        } finally {
            if (!ready) check(temp.delete() || !temp.exists()) { "Cannot remove failed temporary candidate." }
        }
    }

    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

    internal fun requireSource(draft: ProjectDraft) {
        require(draft.sourceMode == SourceMode.LOCAL) { "URL AAB is unsupported." }
        require(draft.buildOutput.trim().lowercase() in setOf("aab", "both", "apk+aab")) {
            "Explicit AAB/BOTH request required."
        }
        require(draft.sourceBuildEngine.trim().lowercase() == "webview-static" &&
            draft.sourceTechnology.trim().lowercase() in setOf("html", "web-static", "webview"))
        require(draft.signingMode == com.appforge.studio.model.SigningMode.DEBUG ||
            draft.signingMode == com.appforge.studio.model.SigningMode.CUSTOM) {
            "Hızlı AAB yalnız debug veya seçilmiş yükleme anahtarıyla üretilir."
        }
        FastHtmlApkPolicy.requireSupported(draft.copy(
            buildOutput = "apk", signingMode = com.appforge.studio.model.SigningMode.DEBUG
        ))
        require(draft.minSdk == 26 && draft.targetSdk == 37) {
            "Unsigned static AAB requires pinned SDK 26/37."
        }
        val supplied = File(requireNotNull(draft.importedFolder))
        require(!java.nio.file.Files.isSymbolicLink(supplied.toPath()))
        val root = supplied.canonicalFile
        require(root.isDirectory)
        val files = root.walkTopDown().maxDepth(13).onEnter { directory ->
            require(!java.nio.file.Files.isSymbolicLink(directory.toPath())) { "Symlink directory unsupported." }
            require(directory.name != "node_modules") { "Dependency projects unsupported." }
            require(directory.canonicalPath == root.path ||
                directory.canonicalPath.startsWith(root.path + File.separator))
            require(directory.relativeTo(root).invariantSeparatorsPath.split('/').size <= 12) {
                "Source nesting limit exceeded."
            }
            true
        }
        var count = 0
        for (file in files) {
            require(++count <= 4096) { "Source traversal limit exceeded." }
            require(!java.nio.file.Files.isSymbolicLink(file.toPath())) { "Source symlink unsupported." }
            require(file.name != "package.json") { "Package-based asset builds unsupported." }
        }
    }

    /** ZIP Local Header validation only; exact pinned CI digest eliminates alias ambiguity. */
    internal fun readTemplate(bytes: ByteArray): Map<String, ByteArray> {
        require(bytes.size in 1024..LIMIT)
        require(bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte() &&
            bytes[2] == 3.toByte() && bytes[3] == 4.toByte()) { "Invalid ZIP header." }
        val result = LinkedHashMap<String, ByteArray>()
        val seen = HashSet<String>()
        var total = 0L
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                FastHtmlAabVerification.requireSafeName(entry.name, entry.isDirectory)
                require(seen.add(entry.name) && seen.size <= 2048) { "Duplicate/excessive ZIP entries." }
                require(!entry.name.startsWith("META-INF/", true)) { "Template signature metadata unsupported." }
                val data = readLimited(zip, minOf(ENTRY_LIMIT.toLong(), LIMIT - total).toInt())
                total += data.size
                if (entry.isDirectory) require(data.isEmpty())
                else result[entry.name] = data
                zip.closeEntry()
            }
        }
        require(result.keys.containsAll(required)) { "Incomplete HTML AAB template." }
        return result
    }

    private fun readLimited(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "AAB compressed/expanded size limit exceeded." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
