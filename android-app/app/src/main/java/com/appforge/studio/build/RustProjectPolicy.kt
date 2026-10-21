package com.appforge.studio.build

import com.appforge.studio.model.ProjectDraft
import com.appforge.studio.model.SourceMode
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.json.JSONObject

/** A prebuilt WASM payload is not permission to execute Cargo/build.rs or inject JS.
 * Android JNI uses an explicit receipt and isolated staging; other native consumers remain blocked. */
internal object RustProjectPolicy {
    fun sourceRoot(draft:ProjectDraft):File? = if(draft.sourceMode==SourceMode.LOCAL)
        draft.importedFolder?.let(::File)?.takeIf { File(it,"Cargo.toml").isFile } else null
    fun canPrepareNodeDependencies(draft:ProjectDraft):Boolean = sourceRoot(draft)!=null && draft.sourceBuildEngine=="node-web" &&
        runCatching{requirePackageable(draft);true}.getOrDefault(false)
    fun canPrepareEngineDependencies(draft:ProjectDraft):Boolean = sourceRoot(draft)!=null && draft.sourceBuildEngine in setOf("node-web","android-gradle") &&
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
        val engine=draft.sourceBuildEngine.trim().lowercase().ifBlank{"webview-static"}
        if(engine=="android-gradle") { requireAndroidJni(draft);return }
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

    /** A real defined JNI symbol is required; a source-supplied receipt is not
     * independently trusted. Only ELF64 ARM64 shared libraries are accepted. */
    fun androidJniExports(file:File):Set<String> {
        require(file.isFile && file.length() in 64..64L*1024*1024 && !Files.isSymbolicLink(file.toPath()))
        val bytes=file.readBytes();val b=java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        require(bytes.take(6)==listOf<Byte>(127,69,76,70,2,1) && b.getShort(16).toInt()==3 && b.getShort(18).toInt()==183){"Rust JNI ARM64 ELF shared library değil"}
        val ph=b.getLong(32);val phsize=b.getShort(54).toInt() and 65535;val phcount=b.getShort(56).toInt() and 65535
        require(phsize==56 && phcount in 1..1024 && ph>=64 && ph<=bytes.size.toLong()-phsize.toLong()*phcount)
        var loads=0
        for(i in 0 until phcount) {
            val p=(ph+i*56).toInt();if(b.getInt(p)!=1)continue;loads++
            val start=b.getLong(p+8);val va=b.getLong(p+16);val length=b.getLong(p+32);val memory=b.getLong(p+40);val alignment=b.getLong(p+48)
            require(alignment>=16384 && alignment and (alignment-1)==0L && start>=0 && length>=0 && memory>=length && start<=bytes.size.toLong()-length && start%16384==va%16384){"Android16 Rust ELF 16KiB hizalaması geçersiz"}
        }
        require(loads>0)
        val offset=b.getLong(40);val entry=b.getShort(58).toInt() and 65535;val count=b.getShort(60).toInt() and 65535
        require(entry==64 && count in 1..4096 && offset>=64 && offset<=bytes.size.toLong()-entry.toLong()*count)
        fun section(i:Int):Int {require(i in 0 until count);return (offset+i*64).toInt()}
        val names=linkedSetOf<String>()
        for(i in 0 until count) {
            val sec=section(i);if(b.getInt(sec+4)!=11)continue
            val start=b.getLong(sec+24);val length=b.getLong(sec+32);val width=b.getLong(sec+56)
            require(width==24L && start>=0 && length>=0 && length%24==0L && start<=bytes.size.toLong()-length)
            val strings=section(b.getInt(sec+40));require(b.getInt(strings+4)==3)
            val text=b.getLong(strings+24);val textSize=b.getLong(strings+32)
            require(text>=0 && textSize>=0 && text<=bytes.size.toLong()-textSize)
            var pos=start
            while(pos<start+length) {
                val p=pos.toInt();val info=bytes[p+4].toInt() and 255;val other=bytes[p+5].toInt() and 3
                if(b.getShort(p+6).toInt()!=0 && info shr 4 in setOf(1,2) && info and 15==2 && other in setOf(0,3)) {
                    val name=b.getInt(p);require(name>=0 && name.toLong()<textSize)
                    val first=(text+name).toInt();var end=first
                    while(end<text+textSize && bytes[end]!=0.toByte())end++
                    require(end<text+textSize && end-first<=1024)
                    val symbol=String(bytes,first,end-first,Charsets.US_ASCII)
                    if(symbol.startsWith("Java_") || symbol=="JNI_OnLoad")names.add(symbol)
                }
                pos+=24
            }
        }
        require(names.isNotEmpty()){"Rust JNI export yok; C ABI tek başına JNI değildir"};return names
    }
    private fun requireAndroidJni(draft:ProjectDraft):File {
        require(DeviceBuildCapabilities.requestedOutputs(draft.buildOutput).all{it in setOf(DeviceArtifactKind.APK,DeviceArtifactKind.AAB)}){"Rust JNI yalnız Android APK/AAB çıktısına bağlanabilir"}
        val root=requireNotNull(sourceRoot(draft));val config=File(root,"appforge-rust-android.json");RecoveryPathAnchor.check(config)
        require(config.isFile && config.length() in 1..4096){"Açık appforge-rust-android.json JNI bağlama bildirimi gerekir"}
        val c=JSONObject(config.readText());val library=c.getString("library")
        require(c.getInt("schema")==1 && c.getString("module")=="app" && c.getInt("minApi")==26 && library.matches(Regex("[A-Za-z][A-Za-z0-9_]{0,63}")))
        require(draft.minSdk>=26){"Rust JNI API26 linker ile üretildi; minSdk en az26 olmalı"}
        require(File(root,"app/build.gradle").isFile || File(root,"app/build.gradle.kts").isFile)
        val receipt=File(root,"appforge-rust/artifacts.json");RecoveryPathAnchor.check(receipt)
        require(receipt.isFile && receipt.length() in 1..16384){"Android JNI Rust çıktısı önce açık onayla hazırlanmalı"}
        val j=JSONObject(receipt.readText());val name="lib$library.so"
        require(j.getInt("schema")==2 && j.getString("target")=="aarch64-linux-android" && j.getString("module")=="app" && j.getInt("minApi")==26 && j.getString("file")==name)
        require(j.getString("sourceSha256")==sourceDigest(root)){"Rust JNI kaynağı değişti"}
        val file=File(receipt.parentFile,name);RecoveryPathAnchor.check(file)
        require(file.isFile && file.length()<=64L*1024*1024 && digest(file)==j.getString("sha256")){"Rust JNI SHA-256 doğrulanamadı"}
        val actual=androidJniExports(file);val symbols=j.getJSONArray("jniExports")
        require((0 until symbols.length()).map{symbols.getString(it)}.toSet()==actual){"Rust JNI export kaydı gerçek ELF ile uyuşmuyor"}
        return file
    }
    fun stageAndroidJni(draft:ProjectDraft,buildSource:File) {
        if(sourceRoot(draft)==null)return
        val source=requireAndroidJni(draft)
        val directory=File(buildSource,"app/src/main/jniLibs/arm64-v8a");RecoveryPathAnchor.check(directory)
        require(directory.canonicalFile.toPath().startsWith(buildSource.canonicalFile.toPath()))
        require(directory.isDirectory || directory.mkdirs())
        val dest=File(directory,source.name);RecoveryPathAnchor.check(dest)
        if(dest.exists())require(dest.isFile && digest(dest)==digest(source)){"Projenin JNI dosyası farklı; üzerine yazılmadı"}
        else {require(directory.usableSpace>source.length()+1024*1024);source.inputStream().use{input->dest.outputStream().use{input.copyTo(it)}}}
        require(digest(dest)==digest(source))
    }
    fun verifyAndroidJniPackage(draft:ProjectDraft,kind:String,file:File) {
        if(sourceRoot(draft)==null || draft.sourceBuildEngine.trim().lowercase()!="android-gradle")return
        val expected=requireAndroidJni(draft);val path=(if(kind=="aab")"base/" else "")+"lib/arm64-v8a/"+expected.name
        java.util.zip.ZipFile(file).use { zip ->
            val entries=zip.entries().asSequence().filter{it.name==path}.toList();require(entries.size==1){"APK/AAB içinde Rust JNI yok veya çift kayıt var"}
            val hash=MessageDigest.getInstance("SHA-256");var size=0L
            zip.getInputStream(entries.single()).use{input->val buf=ByteArray(65536);while(true){val n=input.read(buf);if(n<0)break;size+=n;require(size<=expected.length());hash.update(buf,0,n)}}
            require(size==expected.length() && hash.digest().joinToString(""){"%02x".format(it)}==digest(expected)){"Paketleyici Rust JNI dosyasını değiştirdi; Gradle keepDebugSymbols ile doğrulanmış baytları koruyun"}
        }
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
