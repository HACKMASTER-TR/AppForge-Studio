package com.appforge.studio.build

import com.appforge.studio.model.ProjectDraft
import com.appforge.studio.model.SourceMode
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.json.JSONObject

/** A prebuilt WASM payload is not permission to execute Cargo/build.rs or inject JS.
 * Native/JNI/PyO3/JSI consumers require separate ABI integration and remain blocked. */
internal object RustProjectPolicy {
    fun sourceRoot(draft:ProjectDraft):File? = if(draft.sourceMode==SourceMode.LOCAL)
        draft.importedFolder?.let(::File)?.takeIf { File(it,"Cargo.toml").isFile } else null
    fun digest(file:File):String=MessageDigest.getInstance("SHA-256").run {
        file.inputStream().use { input -> val bytes=ByteArray(65536);while(true){val n=input.read(bytes);if(n<0)break;update(bytes,0,n)} }
        digest().joinToString(""){"%02x".format(it)}
    }
    fun sourceDigest(root:File):String {
        val files=mutableListOf(File(root,"Cargo.toml"),File(root,"Cargo.lock"))
        for(name in listOf("build.rs",".cargo/config.toml","rust-toolchain.toml")){val f=File(root,name);if(f.exists())files.add(f)}
        val src=File(root,"src");require(src.isDirectory){"Rust src dizini eksik"}
        src.walkTopDown().onEnter { require(!Files.isSymbolicLink(it.toPath()));true }.forEach {
            require(!Files.isSymbolicLink(it.toPath())){"Rust kaynak symlink içeremez"}
            if(it.isFile)files.add(it)
        }
        require(files.size<=4096 && files.sumOf{it.length()}<=32L*1024*1024){"Rust kaynak sınırı aşıldı"}
        val hash=MessageDigest.getInstance("SHA-256")
        files.sortedBy{it.relativeTo(root).invariantSeparatorsPath}.forEach {
            require(it.isFile && !Files.isSymbolicLink(it.toPath()) && it.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()))
            hash.update(it.relativeTo(root).invariantSeparatorsPath.toByteArray(Charsets.UTF_8));hash.update(0.toByte());hash.update(digest(it).toByteArray(Charsets.US_ASCII));hash.update(10.toByte())
        }
        return hash.digest().joinToString(""){"%02x".format(it)}
    }
    fun requirePackageable(draft:ProjectDraft) {
        val root=sourceRoot(draft)?:return
        require(draft.sourceBuildEngine.trim().lowercase().ifBlank{"webview-static"} in setOf("webview-static","node-web")) {
            "Rust JNI/NDK/PyO3/JSI/Windows native entegrasyonu bu motor için doğrulanmadı; Cargo kaynağı sessizce atlanamaz."
        }
        val receipt=File(root,"appforge-rust/artifacts.json")
        require(receipt.isFile && !Files.isSymbolicLink(receipt.toPath()) && receipt.length() in 1..16384 && receipt.canonicalFile.toPath().startsWith(root.canonicalFile.toPath())) {
            "Rust kaynağı var. Rust + Cargo modülü ve açık kaynak çalıştırma onayıyla çevrimdışı WASM hazırlayın; otomatik Cargo yürütülmez."
        }
        val j=JSONObject(receipt.readText());require(j.getInt("schema")==1 && j.getString("target")=="wasm32-unknown-unknown")
        require(j.getString("sourceSha256")==sourceDigest(root)){"Rust kaynağı değişti; WASM yeniden hazırlanmalı"}
        val name=j.getString("file");require(name.matches(Regex("[A-Za-z0-9_-]+\\.wasm")))
        val wasm=File(receipt.parentFile,name)
        require(wasm.isFile && !Files.isSymbolicLink(wasm.toPath()) && wasm.canonicalFile.parentFile==receipt.parentFile.canonicalFile && digest(wasm)==j.getString("sha256")){"Rust WASM bütünlük kontrolü başarısız"}
        require(wasm.inputStream().use{input->val magic=ByteArray(8);var n=0;while(n<8){val count=input.read(magic,n,8-n);require(count>0);n+=count};magic}.contentEquals(byteArrayOf(0,97,115,109,1,0,0,0))){"Rust WASM biçimi geçersiz"}
        // Receipt is source/artifact consistency only. Runtime JS bindings and device acceptance are separate.
    }
}
