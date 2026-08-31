package com.appforge.studio.io

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

data class ManagedKeystore(
    val id: String,
    val name: String,
    val originalFileName: String,
    val savedPath: String,
    val algorithm: String,
    val sha1: String,
    val sha256: String,
    val addedAt: Long
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
                            addedAt = o.optLong("addedAt")
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
            addedAt = System.currentTimeMillis()
        )

        val list = load(context).toMutableList()
        list.removeAll { it.id == id }
        list.add(0, item)
        persist(context, list)
        return item
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
