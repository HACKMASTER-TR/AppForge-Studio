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
    fun canPrepareNodeDependencies(draft:ProjectDraft):Boolean = sourceRoot(draft)!=null && draft.sourceBuildEngine=="node-web" &&
        runCatching{requirePackageable(draft);true}.getOrDefault(false)
    fun digest(file:File):String=MessageDigest.getInstance("SHA-256").run {
        file.inputStream().use { input -> val bytes=ByteArray(65536);while(true){val n=input.read(bytes);if(n<0)break;update(bytes,0,n)} }
        digest().joinToString(""){"%02x".format(it)}
    }
    fun sourceDigest(root:File):String {
        val files=mutableListOf<File>();var bytes=0L
        val ignored=setOf("target","node_modules",".git",".gradle",".expo",".next","appforge-rust")
        require(File(root,"src").isDirectory){"Rust src dizini eksik"}
        root.walkTopDown().onFail{_,error->throw error}.onEnter { directory ->
            val include=directory==root || directory.name!=".git" && !(directory.parentFile==root && directory.name in ignored)
            if(include)require(!Files.isSymbolicLink(directory.toPath())){"Rust kaynak symlink içeremez"}
            include
        }.forEach { file ->
            if(file!=root && !file.isDirectory) {
                require(file.isFile && !Files.isSymbolicLink(file.toPath())){"Rust kaynak dosyası güvenli değil"}
                bytes+=file.length();files.add(file)
                require(files.size<=20000 && bytes<=512L*1024*1024){"Rust kaynak sınırı aşıldı"}
            }
        }
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
        val j=JSONObject(receipt.readText());require(j.getInt("schema")==2 && j.getString("target")=="wasm32-unknown-unknown"){"Rust hazırlık şeması eski/geçersiz; özgün kaynağı koruyarak yeni özel kopya hazırlayın"}
        require(j.getString("sourceSha256")==sourceDigest(root)){"Rust kaynağı değişti; WASM yeniden hazırlanmalı"}
        val name=j.getString("file");require(name.matches(Regex("[A-Za-z0-9_-]+\\.wasm")))
        val wasm=File(receipt.parentFile,name)
        require(wasm.isFile && !Files.isSymbolicLink(wasm.toPath()) && wasm.canonicalFile.parentFile==receipt.parentFile.canonicalFile && digest(wasm)==j.getString("sha256")){"Rust WASM bütünlük kontrolü başarısız"}
        require(wasm.inputStream().use{input->val magic=ByteArray(8);var n=0;while(n<8){val count=input.read(magic,n,8-n);require(count>0);n+=count};magic}.contentEquals(byteArrayOf(0,97,115,109,1,0,0,0))){"Rust WASM biçimi geçersiz"}
        // Receipt is source/artifact consistency only. Runtime JS bindings and device acceptance are separate.
    }

    /** Explicit project JS owns the binding; the bundler cannot silently drop or
     * substitute a previously verified WASM binary. No JavaScript is injected. */
    fun stageNodeWebPayload(draft:ProjectDraft,buildSource:File,site:File) {
        val root=sourceRoot(draft)?:return
        requirePackageable(draft)
        require(site.canonicalFile.toPath().startsWith(buildSource.canonicalFile.toPath())){"Rust web çıktısı proje çalışma alanı dışında"}
        RecoveryPathAnchor.check(site)
        val receipt=File(root,"appforge-rust/artifacts.json");val j=JSONObject(receipt.readText())
        val source=File(receipt.parentFile,j.getString("file"))
        val directory=File(site,"appforge-rust");RecoveryPathAnchor.check(directory)
        require(!directory.exists() || directory.isDirectory)
        require(directory.isDirectory || directory.mkdir())
        val destination=File(directory,source.name);RecoveryPathAnchor.check(destination)
        if(destination.exists()) {
            require(destination.isFile && digest(destination)==j.getString("sha256")){"Node build Rust WASM çıktısını değiştirdi; kullanıcı dosyası üzerine yazılmadı"}
            return
        }
        require(site.usableSpace>source.length()+1024*1024){"Rust WASM web kopyası için alan yok"}
        source.inputStream().use{input->destination.outputStream().use{input.copyTo(it)}}
        require(digest(destination)==j.getString("sha256")){"Rust WASM web aktarımı bozuk"}
    }
}
