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
        val manifest: ByteArray
        val signature: ByteArray

        if (target.exists()) {
            require(target.isFile &&
                !Files.isSymbolicLink(target.toPath()) &&
                target.length() in 1..MAX) {
                "Geçersiz kayıtlı R2 sürüm dosyası."
            }
            val json = JSONObject(target.readText())
            manifest = Base64.getDecoder().decode(json.getString("manifest"))
            signature = Base64.getDecoder().decode(json.getString("signature"))
        } else {
            // Debug-only signed release bundled in the APK.
            // Do not persist a fake verification flag.
            val assets = context.applicationContext.assets
            manifest = assets.open("v9-r2/release-v1.json").use {
                it.readBytes()
            }
            signature = assets.open("v9-r2/release-v1.sig").use {
                it.readBytes()
            }
            require(manifest.size in 1..(12 * 1024 * 1024))
            require(signature.size == 64)
        }

        // Always verify the pinned Ed25519 signature and release policy.
        val verified = V9ReleaseCandidatePolicy.verify(
            manifest, signature, floor(context)
        )
        return Bundle(manifest, signature, verified.sequence)
    }
}
