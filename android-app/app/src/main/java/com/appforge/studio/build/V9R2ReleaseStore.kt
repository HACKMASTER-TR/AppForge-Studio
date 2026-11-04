package com.appforge.studio.build

import android.content.Context
import com.appforge.studio.BuildConfig
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64

/** Persist original signed bytes, never a self-asserted "verified" flag.
 * This authorizes the staging read-only source only; production stays disabled.
 */
internal object V9R2ReleaseStore {
    data class Bundle(val manifest: ByteArray, val signature: ByteArray, val sequence: Long)
    private const val MAX = 17L * 1024 * 1024
    private fun file(context: Context): File {
        val root = context.applicationContext.noBackupFilesDir.canonicalFile
        val target = File(root, "v9-r2-release.json")
        require(!Files.isSymbolicLink(target.toPath()) && target.canonicalFile.parentFile == root)
        return target
    }
    private fun floor(context: Context) = maxOf(2026101801L,
        context.applicationContext.getSharedPreferences("v9-r2-trust", 0).getLong("sequence-floor", 2026101801L))

    @Synchronized fun save(context: Context, manifest: ByteArray, signature: ByteArray): Bundle {
        check(BuildConfig.DEBUG) { "Staging R2 source is disabled in release" }
        val verified = V9ReleaseCandidatePolicy.verify(manifest, signature, floor(context))
        val target = file(context)
        val temp = File(target.path + ".part")
        require(!Files.isSymbolicLink(temp.toPath()))
        val envelope = JSONObject().put("manifest", Base64.getEncoder().encodeToString(manifest))
            .put("signature", Base64.getEncoder().encodeToString(signature)).toString().toByteArray()
        require(envelope.size <= MAX)
        FileOutputStream(temp).use { it.write(envelope); it.fd.sync() }
        // A crash between floor and bundle publication can only deny an old bundle.
        check(context.applicationContext.getSharedPreferences("v9-r2-trust", 0).edit()
            .putLong("sequence-floor", verified.sequence).commit()) { "Cannot persist anti-rollback floor" }
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        return Bundle(manifest.copyOf(), signature.copyOf(), verified.sequence)
    }
    @Synchronized fun load(context: Context): Bundle {
        check(BuildConfig.DEBUG) { "Staging R2 source is disabled in release" }
        val target = file(context)
        require(target.isFile && target.length() in 1..MAX) {
            "R2_RELEASE_REQUIRED: İmzalı R2 manifestini ve 64 baytlık Ed25519 imzasını Modül Merkezi'nden seçin. Snapshot yedeği kullanılmaz."
        }
        val json = JSONObject(target.readText())
        val manifest = Base64.getDecoder().decode(json.getString("manifest"))
        val signature = Base64.getDecoder().decode(json.getString("signature"))
        val verified = V9ReleaseCandidatePolicy.verify(manifest, signature, floor(context))
        return Bundle(manifest, signature, verified.sequence)
    }
}
