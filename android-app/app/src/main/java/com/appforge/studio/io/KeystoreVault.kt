package com.appforge.studio.io

import android.content.Context
import android.net.Uri
import com.appforge.studio.build.DeviceBuildRuntimeV3
import com.appforge.studio.terminal.LinuxShellEngine
import com.appforge.studio.terminal.ShellEscaper
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class ManagedKeystore(
    val id: String,
    val name: String,
    val originalFileName: String,
    val savedPath: String,
    val algorithm: String,
    val sha1: String,
    val sha256: String,
    val addedAt: Long,
    val alias: String = "",
    val generated: Boolean = false
)

object KeystoreVault {
    private fun metaFile(context: Context) =
        File(context.filesDir, "managed_keystores.json")

    private fun vaultDir(context: Context) =
        File(context.filesDir, "keystore_vault").apply { mkdirs() }

    fun load(context: Context): List<ManagedKeystore> {
        val file = metaFile(context)
        if (!file.exists()) return emptyList()

        return runCatching {
            val arr = JSONArray(file.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        ManagedKeystore(
                            id = o.optString("id"),
                            name = o.optString("name"),
                            originalFileName = o.optString("originalFileName"),
                            savedPath = o.optString("savedPath"),
                            algorithm = o.optString("algorithm", "RSA / imported"),
                            sha1 = o.optString("sha1"),
                            sha256 = o.optString("sha256"),
                            addedAt = o.optLong("addedAt"),
                            alias = o.optString("alias"),
                            generated = o.optBoolean("generated", false)
                        )
                    )
                }
            }.sortedByDescending { it.addedAt }
        }.getOrDefault(emptyList())
    }

    fun importFromUri(context: Context, uri: Uri): ManagedKeystore {
        val fileName = (uri.lastPathSegment ?: "keystore.jks").substringAfterLast('/')
        val ext = fileName.substringAfterLast('.', "jks")
        val id = UUID.randomUUID().toString()
        val target = File(vaultDir(context), "$id.$ext")

        context.contentResolver.openInputStream(uri).use { input ->
            require(input != null) { "Keystore açılamadı." }
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        val bytes = target.readBytes()
        val item = ManagedKeystore(
            id = id,
            name = fileName.substringBeforeLast('.').ifBlank { "keystore_$id" },
            originalFileName = fileName,
            savedPath = target.absolutePath,
            algorithm = "RSA / imported",
            sha1 = digest(bytes, "SHA-1"),
            sha256 = digest(bytes, "SHA-256"),
            addedAt = System.currentTimeMillis(),
            alias = "",
            generated = false
        )

        val list = load(context).toMutableList()
        list.removeAll { it.id == id }
        list.add(0, item)
        persist(context, list)
        return item
    }

    /*
     * KEYSTORE_LOCAL_GENERATION_V1_4
     *
     * Produces a real PKCS12 signing identity with the same pinned JDK used
     * by Device Build Runtime V3. The password is supplied to keytool through
     * an owner-only temporary file and is never persisted in metadata.
     */
    suspend fun createGenerated(
        context: Context,
        displayName: String,
        alias: String,
        password: String,
        recreateId: String? = null
    ): ManagedKeystore =
        withContext(Dispatchers.IO) {
            val appContext =
                context.applicationContext

            val safeName =
                displayName
                    .trim()
                    .replace(
                        Regex("[^A-Za-z0-9._-]+"),
                        "_"
                    )
                    .trim('_', '.', '-')
                    .take(64)
                    .ifBlank {
                        "appforge-release"
                    }

            val safeAlias =
                alias
                    .trim()
                    .replace(
                        Regex("[^A-Za-z0-9._-]+"),
                        "-"
                    )
                    .trim('-', '.', '_')
                    .take(64)
                    .ifBlank {
                        "appforge-release"
                    }

            require(password.length in 8..128) {
                "Keystore parolası 8-128 karakter olmalıdır."
            }

            require(
                '\n' !in password &&
                    '\r' !in password &&
                    '\u0000' !in password
            ) {
                "Keystore parolası geçersiz kontrol karakteri içeriyor."
            }

            val previous =
                recreateId?.let { id ->
                    load(appContext)
                        .firstOrNull {
                            it.id == id
                        }
                        ?: error(
                            "Yeniden oluşturulacak keystore bulunamadı."
                        )
                }

            if (previous != null) {
                require(previous.generated) {
                    "İçe aktarılan bir keystore otomatik olarak yeniden oluşturulamaz."
                }
            }

            val operationId =
                UUID.randomUUID()
                    .toString()

            val workspace =
                File(
                    appContext.cacheDir,
                    "keystore-create/$operationId"
                ).apply {
                    deleteRecursively()
                    mkdirs()
                }

            try {
                val runtimeDir =
                    File(
                        workspace,
                        "runtime"
                    ).apply {
                        mkdirs()
                    }

                appContext.assets
                    .open(
                        "device-build/install-toolchain.sh"
                    )
                    .use { input ->
                        File(
                            runtimeDir,
                            "install-toolchain.sh"
                        )
                            .outputStream()
                            .use { output ->
                                input.copyTo(output)
                            }
                    }

                val rootfs =
                    DeviceBuildRuntimeV3
                        .ensureReady(
                            appContext
                        )

                val output =
                    File(
                        workspace,
                        "generated.p12"
                    )

                /*
                 * Keep the secret out of the shell command / process argv.
                 * keytool reads it from an owner-only temporary file that is
                 * deleted together with the isolated operation workspace.
                 */
                File(
                    workspace,
                    ".appforge-keystore-pass"
                ).apply {
                    writeText(
                        password,
                        Charsets.UTF_8
                    )
                    setReadable(false, false)
                    setWritable(false, false)
                    check(
                        setReadable(true, true) &&
                            setWritable(true, true)
                    ) {
                        "Geçici parola dosyası izinleri hazırlanamadı."
                    }
                }

                val dname =
                    "CN=$safeAlias, OU=AppForge Studio, O=AppForge Studio"

                val command =
                    "chmod +x /workspace/runtime/install-toolchain.sh && " +
                        "APPFORGE_DEVICE_OFFLINE=0 " +
                        "/bin/sh /workspace/runtime/install-toolchain.sh webview-static && " +
                        "/opt/appforge-device/jdk-17/bin/keytool " +
                        "-genkeypair -noprompt " +
                        "-alias ${ShellEscaper.quote(safeAlias)} " +
                        "-keyalg RSA -keysize 3072 -sigalg SHA256withRSA " +
                        "-validity 10000 " +
                        "-dname ${ShellEscaper.quote(dname)} " +
                        "-storetype PKCS12 " +
                        "-keystore /workspace/generated.p12 " +
                        "-storepass:file /workspace/.appforge-keystore-pass " +
                        "-keypass:file /workspace/.appforge-keystore-pass"

                val result =
                    LinuxShellEngine(
                        appContext
                    ).execute(
                        sessionId =
                            "keystore-$operationId",
                        rootfs = rootfs,
                        workspace = workspace,
                        command = command,
                        confirmed = true,
                        timeoutMs = 1_800_000L
                    )

                check(
                    !result.timedOut &&
                        result.exitCode == 0
                ) {
                    result.output
                        .takeLast(2_000)
                        .ifBlank {
                            "Keystore oluşturma işlemi tamamlanamadı."
                        }
                }

                require(
                    output.isFile &&
                        output.length() > 0L
                ) {
                    "Keytool çıktı dosyası üretmedi."
                }

                val id =
                    previous?.id
                        ?: UUID.randomUUID()
                            .toString()

                val target =
                    previous
                        ?.savedPath
                        ?.let(::File)
                        ?: File(
                            vaultDir(appContext),
                            "$id.p12"
                        )

                output.copyTo(
                    target,
                    overwrite = true
                )

                val bytes =
                    target.readBytes()

                val item =
                    ManagedKeystore(
                        id = id,
                        name = safeName,
                        originalFileName = "$safeName.p12",
                        savedPath = target.absolutePath,
                        algorithm = "RSA 3072 / PKCS12",
                        sha1 = digest(bytes, "SHA-1"),
                        sha256 = digest(bytes, "SHA-256"),
                        addedAt = System.currentTimeMillis(),
                        alias = safeAlias,
                        generated = true
                    )

                val list =
                    load(appContext)
                        .toMutableList()

                list.removeAll {
                    it.id == id
                }

                list.add(
                    0,
                    item
                )

                persist(
                    appContext,
                    list
                )

                item
            } finally {
                workspace.deleteRecursively()
            }
        }

    fun delete(context: Context, id: String) {
        val list = load(context).toMutableList()
        val found = list.firstOrNull { it.id == id }
        if (found != null) {
            runCatching { File(found.savedPath).delete() }
        }
        list.removeAll { it.id == id }
        persist(context, list)
    }

    fun count(context: Context): Int =
        load(context).size

    private fun persist(context: Context, list: List<ManagedKeystore>) {
        val arr = JSONArray()
        list.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("originalFileName", item.originalFileName)
                    put("savedPath", item.savedPath)
                    put("algorithm", item.algorithm)
                    put("sha1", item.sha1)
                    put("sha256", item.sha256)
                    put("addedAt", item.addedAt)
                    put("alias", item.alias)
                    put("generated", item.generated)
                }
            )
        }
        metaFile(context).writeText(arr.toString(2))
    }

    private fun digest(bytes: ByteArray, algorithm: String): String {
        val md = MessageDigest.getInstance(algorithm)
        return md.digest(bytes).joinToString(":") { "%02X".format(it) }
    }
}
