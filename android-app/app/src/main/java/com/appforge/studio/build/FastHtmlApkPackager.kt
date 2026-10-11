package com.appforge.studio.build

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import com.appforge.studio.model.ProjectDraft
import com.appforge.studio.model.SourceMode
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Date
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.security.auth.x500.X500Principal

/** Aapt/Gradle-free on-device APK re-packaging using an app-bundled, CI-built shell. */
internal object FastHtmlApkPackager {
    private const val ASSET = "html-apk-template.apk"
    private const val SIGNING_ALIAS = "appforge-html-apk-signing-v1"
    private const val MAX_TEMPLATE_BYTES = 12L * 1024 * 1024
    private const val MAX_HTML_BYTES = 5L * 1024 * 1024
    private const val MAX_PROJECT_BYTES = 24L * 1024 * 1024
    private const val MAX_PROJECT_FILES = 250

    fun build(context: Context, draft: ProjectDraft, workspace: File): File {
        FastHtmlApkPolicy.requireSupported(draft)
        check(workspace.mkdirs() || workspace.isDirectory)
        val template = File(workspace, "html-template.apk")
        val unsigned = File(workspace, "html-unsigned.apk")
        val signed = File(workspace, "html-output.apk")
        val htmlProject: Pair<String, Map<String, ByteArray>>? =
            if (draft.sourceMode == SourceMode.LOCAL) readHtmlProject(draft) else null
        try {
            context.assets.open(ASSET).use { input ->
                FileOutputStream(template).use { output ->
                    copyLimited(input, output, MAX_TEMPLATE_BYTES)
                }
            }
            check(template.length() in 1024..MAX_TEMPLATE_BYTES) { "Gömülü HTML APK şablonu eksik veya geçersiz." }
            val config = JSONObject()
                .put("mode", draft.sourceMode.name)
                .put("url", if (draft.sourceMode == SourceMode.URL) draft.webUrl else "")
                .put("entry", htmlProject?.first ?: "")
                .toString().toByteArray(Charsets.UTF_8)
            val additions = mutableMapOf("assets/appforge-html-config.json" to config)
            htmlProject?.second?.let(additions::putAll)
            ZipFile(template).use { zip ->
                val manifest = zip.getEntry("AndroidManifest.xml")
                    ?: error("Şablon manifesti bulunamadı.")
                additions["AndroidManifest.xml"] = HtmlBinaryManifest.rewrite(
                    zip.getInputStream(manifest).use { it.readBytes() },
                    draft.packageName, draft.appName, draft.versionCode, draft.versionName,
                    draft.minSdk, draft.targetSdk
                )
                repackage(zip, unsigned, additions)
            }
            val signer = getOrCreateSigner()
            ApkSigner.Builder(listOf(signer))
                .setInputApk(unsigned)
                .setOutputApk(signed)
                .setMinSdkVersion(draft.minSdk)
                .setV1SigningEnabled(false)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .build().sign()
            val verification = ApkVerifier.Builder(signed).build().verify()
            check(verification.isVerified && verification.signerCertificates.isNotEmpty()) {
                "APK v2/v3 imza doğrulaması başarısız."
            }
            check(signed.length() in 1024..(MAX_TEMPLATE_BYTES + MAX_PROJECT_BYTES + 1_048_576))
            return signed
        } catch (failure: Throwable) {
            signed.delete()
            throw failure
        } finally {
            unsigned.delete()
            template.delete()
        }
    }

    /** Package only static web assets; never run Node, npm or JavaScript source builds. */
    private fun readHtmlProject(draft: ProjectDraft): Pair<String, Map<String, ByteArray>> {
        val root = File(draft.importedFolder!!).canonicalFile
        require(root.isDirectory) { "HTML kaynak klasörü geçersiz." }
        val chosen = draft.startPage?.let(::File)?.canonicalFile
        val html = if (chosen?.isFile == true) chosen else File(root, "index.html").canonicalFile
        require(html.isFile && html.name.endsWith(".html", true)) { "Bir .html dosyası seçin." }
        require(html.path.startsWith(root.path + File.separator)) { "HTML kaynak yolu proje dışına çıkamaz." }
        require(html.length() in 1..MAX_HTML_BYTES) { "HTML dosyası 5 MiB sınırında olmalı." }

        val allowed = setOf(
            "html", "css", "js", "mjs", "json", "txt", "svg", "png", "jpg", "jpeg",
            "webp", "gif", "ico", "woff", "woff2", "ttf", "otf"
        )
        val excludedDirs = setOf("node_modules", "build", "dist", ".git", ".gradle")
        val assets = LinkedHashMap<String, ByteArray>()
        var totalBytes = 0L
        val files = root.walkTopDown().maxDepth(12).onEnter { directory ->
            directory == root ||
                (directory.name !in excludedDirs && !directory.name.startsWith(".") &&
                    !java.nio.file.Files.isSymbolicLink(directory.toPath()))
        }
        for (file in files) {
            if (!file.isFile || file.name.startsWith(".")) continue
            // Symlinks, including links through directories, must never escape the project.
            require(!java.nio.file.Files.isSymbolicLink(file.toPath())) {
                "HTML kaynak projesindeki sembolik bağlantılar desteklenmiyor."
            }
            val canonical = file.canonicalFile
            require(canonical.path.startsWith(root.path + File.separator)) {
                "HTML kaynak dosyası proje klasörü dışında."
            }
            val extension = file.extension.lowercase()
            if (extension !in allowed) continue
            require(file.length() in 0..MAX_PROJECT_BYTES) { "HTML proje dosyası çok büyük." }
            totalBytes += file.length()
            require(totalBytes <= MAX_PROJECT_BYTES && assets.size < MAX_PROJECT_FILES) {
                "HTML + CSS + JavaScript proje boyutu veya dosya sayısı sınırı aşıldı."
            }
            val relative = file.relativeTo(root).invariantSeparatorsPath
            require(relative.isNotBlank() && !relative.split('/').any { it == ".." })
            assets["assets/site/$relative"] = file.readBytes()
        }
        val entry = html.relativeTo(root).invariantSeparatorsPath
        require("assets/site/$entry" in assets) { "Seçilen HTML başlangıç dosyası eklenmedi." }
        return entry to assets
    }

    private fun getOrCreateSigner(): ApkSigner.SignerConfig {
        val storage = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!storage.containsAlias(SIGNING_ALIAS)) {
            val spec = KeyGenParameterSpec.Builder(
                SIGNING_ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            ).setKeySize(2048)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setCertificateSubject(X500Principal("CN=AppForge Local HTML APK"))
                .setCertificateSerialNumber(BigInteger.ONE)
                .setCertificateNotBefore(Date(0L))
                .setCertificateNotAfter(Date(4_102_444_800_000L))
                .build()
            KeyPairGenerator.getInstance("RSA", "AndroidKeyStore").apply { initialize(spec) }.generateKeyPair()
        }
        val key = storage.getKey(SIGNING_ALIAS, null) as? java.security.PrivateKey
            ?: error("HTML APK imzalama anahtarı okunamıyor.")
        val cert = storage.getCertificate(SIGNING_ALIAS) as? X509Certificate
            ?: error("HTML APK imzalama sertifikası okunamıyor.")
        return ApkSigner.SignerConfig.Builder(SIGNING_ALIAS, key, listOf(cert)).build()
    }

    private fun repackage(zip: ZipFile, output: File, replacements: Map<String, ByteArray>) {
        val seen = HashSet<String>()
        val counter = CountedOutput(FileOutputStream(output))
        ZipOutputStream(counter).use { target ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val original = entries.nextElement()
                if (original.isDirectory || original.name.startsWith("META-INF/", true)) continue
                require(original.name !in seen) { "Yinelenen APK dosyası: ${original.name}" }
                seen += original.name
                val bytes = replacements[original.name] ?: zip.getInputStream(original).use { input ->
                    val memory = ByteArrayOutputStream()
                    copyLimited(input, memory, MAX_TEMPLATE_BYTES)
                    memory.toByteArray()
                }
                writeEntry(target, counter, original.name, bytes, original.name == "resources.arsc")
            }
            require("AndroidManifest.xml" in seen && "classes.dex" in seen && "resources.arsc" in seen)
            replacements.forEach { (name, bytes) ->
                if (name !in seen) writeEntry(target, counter, name, bytes, false)
            }
        }
    }

    private fun writeEntry(zip: ZipOutputStream, counter: CountedOutput, name: String, data: ByteArray, stored: Boolean) {
        require(!name.startsWith('/') && ".." !in name.split('/'))
        val entry = ZipEntry(name)
        entry.time = 315532800000L // deterministic DOS timestamp 1980-01-01
        if (stored) {
            entry.method = ZipEntry.STORED
            entry.size = data.size.toLong()
            entry.compressedSize = data.size.toLong()
            entry.crc = CRC32().apply { update(data) }.value
            // Android 11+ requires resources.arsc uncompressed and four-byte aligned.
            val nameSize = name.toByteArray(Charsets.UTF_8).size
            val padding = (4 - ((counter.written + 30L + nameSize + 4L) % 4L).toInt()) % 4
            entry.extra = byteArrayOf(0x50, 0x41, padding.toByte(), 0) + ByteArray(padding)
        }
        zip.putNextEntry(entry)
        zip.write(data)
        zip.closeEntry()
    }

    private fun copyLimited(input: java.io.InputStream, output: OutputStream, limit: Long) {
        val buf = ByteArray(8192)
        var count = 0L
        while (true) {
            val read = input.read(buf)
            if (read == -1) break
            count += read
            require(count <= limit) { "HTML APK şablonu boyut sınırı aşıldı." }
            output.write(buf, 0, read)
        }
    }

    private class CountedOutput(private val target: OutputStream): OutputStream() {
        var written = 0L
            private set
        override fun write(b: Int) { target.write(b); written++ }
        override fun write(b: ByteArray, off: Int, len: Int) { target.write(b, off, len); written += len }
        override fun flush() = target.flush()
        override fun close() = target.close()
    }
}
