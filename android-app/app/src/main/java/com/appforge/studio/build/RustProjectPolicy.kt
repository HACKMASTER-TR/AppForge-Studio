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
    /** Explicit Universal target roots, never heuristic recursive Cargo execution. */
    fun preparationProjects(draft:ProjectDraft):List<Pair<String,ProjectDraft>> {
        if(draft.sourceBuildEngine!="universal-cross-platform")return listOf("" to draft)
        require(draft.sourceMode==SourceMode.LOCAL)
        val root=File(requireNotNull(draft.importedFolder));RecoveryPathAnchor.check(root)
        require(!File(root,"Cargo.toml").exists()){"Universal Rust kaynakları açık hedef köklerinde olmalı; kök Cargo workspace sessizce yönlendirilemez"}
        val file=File(root,"appforge.universal.json");RecoveryPathAnchor.check(file);require(file.isFile && file.length() in 1..65536)
        val j=JSONObject(file.readText());require(j.getString("format")=="appforge-universal" && j.getInt("formatVersion")==1)
        val targets=j.getJSONObject("targets");val outputs=DeviceBuildCapabilities.requestedOutputs(draft.buildOutput)
        val selected=mutableListOf<String>()
        if(outputs.any{it in setOf(DeviceArtifactKind.APK,DeviceArtifactKind.AAB)})selected.add("android")
        if(DeviceArtifactKind.WINDOWS_EXE in outputs)selected.add("windows")
        val projects=selected.map { name ->
            val target=targets.getJSONObject(name);val raw=target.getString("root").replace('\\','/').trim()
            val parts=raw.split('/').filter{it.isNotBlank()}
            require(raw.isNotBlank() && !raw.startsWith('/') && !Regex("^[A-Za-z]:").containsMatchIn(raw) && !raw.contains('\u0000') && parts.isNotEmpty() && parts.none{it=="." || it==".."}){"Universal Rust hedef yolu güvenli değil"}
            val relative=parts.joinToString("/")
            val child=File(root,relative);RecoveryPathAnchor.check(child)
            require(child.isDirectory && child.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()))
            val engine=if(name=="android")"android-gradle" else "webview-static"
            require(target.optString("engine",engine)==engine){"Universal hedef motoru örtük olarak değiştirilemez"}
            val output=if(name=="windows")"exe" else if(DeviceArtifactKind.APK in outputs && DeviceArtifactKind.AAB in outputs)"both" else if(DeviceArtifactKind.APK in outputs)"apk" else "aab"
            relative to draft.copy(importedFolder=child.path,sourceBuildEngine=engine,buildOutput=output,startPage=if(name=="windows")"index.html" else null,allowDependencyDownloads=false)
        }
        val rust=projects.filter{sourceRoot(it.second)!=null}
        for((index,a) in rust.withIndex())for(b in rust.drop(index+1)) {
            val first=File(a.second.importedFolder!!).canonicalFile.toPath();val second=File(b.second.importedFolder!!).canonicalFile.toPath()
            require(!first.startsWith(second) && !second.startsWith(first)){"Universal Cargo hedef kökleri çakışıyor"}
        }
        return rust
    }
    fun hasRust(draft:ProjectDraft):Boolean=if(draft.sourceBuildEngine=="universal-cross-platform")runCatching{preparationProjects(draft).isNotEmpty()}.getOrDefault(false)else sourceRoot(draft)!=null
    fun universalTarget(draft:ProjectDraft,name:String):ProjectDraft?=preparationProjects(draft).firstOrNull {
        if(name=="android")it.second.sourceBuildEngine=="android-gradle" else it.second.sourceBuildEngine=="webview-static"
    }?.second
    fun webPayloadDraft(draft:ProjectDraft):ProjectDraft=if(draft.sourceBuildEngine=="universal-cross-platform")universalTarget(draft,"windows")?:draft else draft
    fun shellProjectPath(relative:String):String {
        require(relative.isEmpty() || !relative.startsWith('/') && !relative.contains('\\') && !relative.contains(':') && !relative.contains('\u0000') && relative.split('/').all{it.isNotBlank() && it!="." && it!=".."})
        val path=if(relative.isEmpty())"/workspace/source" else "/workspace/source/$relative"
        return "'"+path.replace("'","'\"'\"'")+"'"
    }
    private fun requireExportDirectory(file:File) {
        val directory=file.parentFile;RecoveryPathAnchor.check(directory)
        val expected=setOf("artifacts.json",file.name);var count=0
        Files.newDirectoryStream(directory.toPath()).use { entries ->
            for(path in entries)require(++count<=2 && path.fileName.toString() in expected && Files.isRegularFile(path,java.nio.file.LinkOption.NOFOLLOW_LINKS)){"Rust çıktı dizininde yabancı/güvensiz dosya var"}
        }
        require(count==2)
    }
    fun preparationTarget(draft:ProjectDraft):String=when(draft.sourceBuildEngine.trim().lowercase()) {
        "webview-static","node-web"->"wasm32-unknown-unknown"
        "android-gradle","android-ndk","python-android","expo","react-native"->"aarch64-linux-android"
        "windows-native"->"x86_64-pc-windows-gnu"
        else->error("Bu motor için doğrulanmış Rust hazırlık adaptörü yok; başka motora geçilmedi.")
    }
    fun sourceRoot(draft:ProjectDraft):File? = if(draft.sourceMode==SourceMode.LOCAL)
        draft.importedFolder?.let(::File)?.takeIf { File(it,"Cargo.toml").isFile } else null
    fun canPrepareNodeDependencies(draft:ProjectDraft):Boolean = sourceRoot(draft)!=null && draft.sourceBuildEngine=="node-web" &&
        runCatching{requirePackageable(draft);true}.getOrDefault(false)
    fun canPrepareEngineDependencies(draft:ProjectDraft):Boolean = hasRust(draft) && draft.sourceBuildEngine in setOf("node-web","android-gradle","android-ndk","python-android","expo","react-native","universal-cross-platform") &&
        runCatching{requirePackageable(draft);true}.getOrDefault(false)
    fun digest(file:File):String=MessageDigest.getInstance("SHA-256").run {
        file.inputStream().use { input -> val bytes=ByteArray(65536);while(true){val n=input.read(bytes);if(n<0)break;update(bytes,0,n)} }
        digest().joinToString(""){"%02x".format(it)}
    }
    fun sourceDigest(root:File):String {
        val files=mutableListOf<File>();var bytes=0L
        val ignored=setOf("target","node_modules",".git",".gradle",".expo",".next","appforge-rust")
        require(File(root,"src").isDirectory || File(root,"appforge-rust-project.json").isFile || !File(root,"Cargo.toml").exists() && File(root,"appforge.universal.json").isFile){"Rust src dizini eksik"}
        root.walkTopDown().onFail{_,error->throw error}.onEnter { directory ->
            val include=directory==root || directory.name!=".git" && !((directory.parentFile==root || File(directory.parentFile,"Cargo.toml").isFile) && directory.name in ignored)
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
        if(draft.sourceBuildEngine=="universal-cross-platform") {
            preparationProjects(draft).forEach{requirePackageable(it.second)}
            return
        }
        val root=sourceRoot(draft)?:return
        val engine=draft.sourceBuildEngine.trim().lowercase().ifBlank{"webview-static"}
        if(engine in setOf("android-gradle","android-ndk")) { requireAndroidJni(draft);return }
        if(engine=="windows-native") { requireWindowsNative(draft);return }
        if(engine=="python-android") { requirePythonModule(draft);return }
        if(engine in setOf("expo","react-native")) { requireReactNativeJsi(draft);return }
        require(draft.sourceBuildEngine.trim().lowercase().ifBlank{"webview-static"} in setOf("webview-static","node-web")) {
            "Bu motor için doğrulanmış Rust tüketici bağlaması yok; Cargo kaynağı sessizce atlanamaz."
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
        requireExportDirectory(wasm)
        // Receipt is source/artifact consistency only. Runtime JS bindings and device acceptance are separate.
    }
    data class ReactNativeBinding(val turbo:Boolean,val bridge:String,val expoSdk:Int)
    fun reactNativeBinding(root:File):ReactNativeBinding {
        val file=File(root,"appforge-rust-jsi.json");RecoveryPathAnchor.check(file);require(file.isFile && file.length() in 1..4096)
        val c=JSONObject(file.readText());val schema=c.getInt("schema")
        require(schema in 1..2 && c.getString("library")=="appforge_jsi" && c.getString("reactNative")=="0.81" && c.getInt("minApi")==26)
        val architecture=if(schema==1)"legacy"else c.getString("architecture")
        require(architecture in setOf("legacy","turbomodule"))
        val turbo=architecture=="turbomodule";val bridge=if(turbo)"appmodules"else "appforge_rust_jsi"
        require(c.getString("bridgeLibrary")==bridge)
        val sdk=c.getInt("expoSdk");require(sdk in setOf(0,54) && (schema!=1 || sdk==54))
        if(schema==2) {
            val packageFile=File(root,"package.json");RecoveryPathAnchor.check(packageFile);require(packageFile.isFile && packageFile.length() in 1..1024L*1024)
            val deps=JSONObject(packageFile.readText()).getJSONObject("dependencies")
            require(deps.getString("react-native").matches(Regex("""0\.81\.[0-9]+""")))
            if(sdk==54) {
                require(deps.getString("expo").matches(Regex("""54\.[0-9]+\.[0-9]+""")))
                val app=File(root,"app.json");RecoveryPathAnchor.check(app);require(app.isFile && app.length() in 1..1024L*1024)
                require(JSONObject(app.readText()).getJSONObject("expo").getBoolean("newArchEnabled")==turbo)
            }else {
                val props=File(root,"android/gradle.properties");RecoveryPathAnchor.check(props);require(props.isFile && props.length() in 1..65536)
                val flags=Regex("""(?m)^newArchEnabled\s*=\s*(true|false)\s*$""").findAll(props.readText()).map{it.groupValues[1]}.toList()
                require(flags==listOf(turbo.toString()))
            }
        }
        return ReactNativeBinding(turbo,bridge,sdk)
    }
    fun requireReactNativeJsi(draft:ProjectDraft):File {
        require(draft.minSdk>=26 && DeviceBuildCapabilities.requestedOutputs(draft.buildOutput).all{it in setOf(DeviceArtifactKind.APK,DeviceArtifactKind.AAB)})
        val root=requireNotNull(sourceRoot(draft));val config=File(root,"appforge-rust-jsi.json");RecoveryPathAnchor.check(config)
        require(config.isFile && config.length() in 1..4096)
        val binding=reactNativeBinding(root)
        require(draft.sourceBuildEngine!="expo" || binding.expoSdk==54)
        val names=if(binding.turbo)listOf("RustTurboModule.cpp","RustTurboModule.h","OnLoad.cpp","CMakeLists.txt")else listOf("RustJsi.cpp","RustJsiModule.java","RustJsiPackage.java","CMakeLists.txt")
        for(name in names) {
            val file=File(root,"native/$name");RecoveryPathAnchor.check(file);require(file.isFile && file.length() in 1..128L*1024){"Kaynak tarafından açıkça yazılmış JSI köprüsü eksik"}
        }
        val receipt=File(root,"appforge-rust/artifacts.json");RecoveryPathAnchor.check(receipt);require(receipt.isFile && receipt.length() in 1..16384)
        val j=JSONObject(receipt.readText());require(j.getInt("schema")==2 && j.getString("target")=="aarch64-linux-android" && j.getString("integration")== (if(binding.turbo)"react-native-turbomodule"else "react-native-jsi") && j.getInt("minApi")==26 && j.getString("file")=="libappforge_jsi.so" && j.getString("sourceSha256")==sourceDigest(root))
        val file=File(receipt.parentFile,"libappforge_jsi.so");RecoveryPathAnchor.check(file)
        require(digest(file)==j.getString("sha256") && "appforge_jsi_answer" in androidDynamicExports(file) && androidDynamicLibraries(file).first=="libappforge_jsi.so"){"Rust JSI kaynak/ELF/export/SHA-256 uyuşmuyor"}
        requireExportDirectory(file)
        return file
    }
    fun stageReactNativeJsi(draft:ProjectDraft,project:File) {
        if(sourceRoot(draft)==null)return
        val source=requireReactNativeJsi(draft)
        val dir=File(project,"app/src/main/jniLibs/arm64-v8a");RecoveryPathAnchor.check(dir);require(dir.isDirectory || dir.mkdirs())
        val target=File(dir,source.name);RecoveryPathAnchor.check(target)
        if(target.exists())require(target.isFile && digest(target)==digest(source)){"Yabancı JSI dosyası korunur"}
        else {require(dir.usableSpace>source.length()+1024*1024);source.inputStream().use{input->target.outputStream().use{input.copyTo(it)}}}
        require(digest(target)==digest(source))
    }
    fun verifyJsiPackage(draft:ProjectDraft,kind:String,file:File) {
        if(sourceRoot(draft)==null || draft.sourceBuildEngine !in setOf("expo","react-native"))return
        val source=requireReactNativeJsi(draft);val binding=reactNativeBinding(requireNotNull(sourceRoot(draft)));val prefix=(if(kind=="aab")"base/" else "")+"lib/arm64-v8a/"
        java.util.zip.ZipFile(file).use { zip ->
            for(name in listOf(source.name,"lib${binding.bridge}.so")) {
                val entries=zip.entries().asSequence().filter{it.name==prefix+name}.toList()
                require(entries.size==1 && entries.single().size in 64..64L*1024*1024){"APK/AAB Rust/JSI bridge eksik veya çift"}
                val parent=file.absoluteFile.parentFile;RecoveryPathAnchor.check(parent)
                require(parent.usableSpace>entries.single().size+16L*1024*1024)
                val temp=File.createTempFile(".jsi-elf-",".so",parent)
                try {
                    zip.getInputStream(entries.single()).use{input->temp.outputStream().use{output->val b=ByteArray(65536);var size=0L;while(true){val n=input.read(b);if(n<0)break;size+=n;require(size<=64L*1024*1024);output.write(b,0,n)};require(size==entries.single().size)}}
                    if(name==source.name)require(digest(temp)==digest(source))
                    else {
                        require((if(binding.turbo)"JNI_OnLoad"else "Java_com_appforge_rustjsi_RustJsiModule_installNative") in androidJniExports(temp)){"Gerçek JNI→JSI kurulum export'u yok"}
                        val dependencies=androidDynamicLibraries(temp).second
                        require(dependencies.containsAll(if(binding.turbo)setOf("libappforge_jsi.so","libreactnative.so")else setOf("libappforge_jsi.so","libjsi.so"))){"JSI köprüsü gerçek Rust/React Native kütüphanelerine bağlanmadı"}
                    }
                }finally{Files.deleteIfExists(temp.toPath())}
            }
        }
    }

    fun requirePythonModule(draft:ProjectDraft):File {
        require(draft.minSdk>=26 && DeviceBuildCapabilities.requestedOutputs(draft.buildOutput).all{it in setOf(DeviceArtifactKind.APK,DeviceArtifactKind.AAB)})
        val root=requireNotNull(sourceRoot(draft));val config=File(root,"appforge-rust-python.json");RecoveryPathAnchor.check(config)
        require(config.isFile && config.length() in 1..4096)
        val c=JSONObject(config.readText());val module=c.getString("module")
        require(c.getInt("schema")==1 && c.getString("pythonVersion")=="3.12" && c.getInt("minApi")==26 && module.matches(Regex("[A-Za-z_][A-Za-z0-9_]{0,63}")))
        val receipt=File(root,"appforge-rust/artifacts.json");RecoveryPathAnchor.check(receipt);require(receipt.isFile && receipt.length() in 1..16384)
        val j=JSONObject(receipt.readText())
        require(j.getInt("schema")==2 && j.getString("target")=="aarch64-linux-android" && j.getString("integration")=="pyo3" && j.getString("pythonVersion")=="3.12" && j.getInt("minApi")==26 && j.getString("module")==module && j.getString("file")=="$module.so" && j.getString("sourceSha256")==sourceDigest(root))
        val file=File(receipt.parentFile,"$module.so");RecoveryPathAnchor.check(file)
        require(file.isFile && digest(file)==j.getString("sha256") && "PyInit_$module" in androidDynamicExports(file)){"Gerçek PyO3 PyInit export / kaynak / SHA-256 uyuşmuyor"}
        requireExportDirectory(file)
        return file
    }
    fun stagePythonModule(draft:ProjectDraft,project:File) {
        if(sourceRoot(draft)==null)return
        val source=requirePythonModule(draft)
        val directory=File(project,"app/src/main/python");RecoveryPathAnchor.check(directory)
        require(directory.isDirectory || directory.mkdirs())
        val file=File(directory,source.name);RecoveryPathAnchor.check(file)
        if(file.exists())require(file.isFile && digest(file)==digest(source)){"Kullanıcı Python uzantısı korunur; üzerine yazılmadı"}
        else {require(directory.usableSpace>source.length()+1024*1024);source.inputStream().use{input->file.outputStream().use{input.copyTo(it)}}}
        require(digest(file)==digest(source))
    }
    fun verifyPythonPackage(draft:ProjectDraft,kind:String,file:File) {
        if(sourceRoot(draft)==null || draft.sourceBuildEngine!="python-android")return
        val expected=requirePythonModule(draft)
        val parent=file.absoluteFile.parentFile;RecoveryPathAnchor.check(parent)
        val temporary=File.createTempFile(".pyo3-app-",".zip",parent)
        try {
            java.util.zip.ZipFile(file).use { zip ->
                val path=(if(kind=="aab")"base/" else "")+"assets/chaquopy/app.imy"
                val matches=zip.entries().asSequence().filter{it.name==path}.toList()
                require(matches.size==1 && matches.single().size in 1..128L*1024*1024){"Chaquopy app.imy yok, çift veya sınır dışı"}
                require(parent.usableSpace>matches.single().size+16L*1024*1024)
                zip.getInputStream(matches.single()).use { input ->temporary.outputStream().use { output ->
                    val buffer=ByteArray(65536);var count=0L
                    while(true){val n=input.read(buffer);if(n<0)break;count+=n;require(count<=128L*1024*1024);output.write(buffer,0,n)}
                    require(count==matches.single().size)
                }}
            }
            java.util.zip.ZipFile(temporary).use { zip ->
                val paths=hashSetOf<String>();var count=0;var found=0
                val entries=zip.entries()
                while(entries.hasMoreElements()) {
                    val entry=entries.nextElement();require(++count<=20000)
                    require(entry.name.isNotBlank() && !entry.name.startsWith('/') && !entry.name.contains('\\') && !entry.name.contains('\u0000') && entry.name.split('/').none{it==".." || it=="."} && paths.add(entry.name)){"Chaquopy merkez dizininde güvensiz/çift yol"}
                    if(entry.name!=expected.name)continue
                    require(!entry.isDirectory && ++found==1 && entry.size==expected.length())
                    val hash=MessageDigest.getInstance("SHA-256");val crc=java.util.zip.CRC32();var total=0L
                    zip.getInputStream(entry).use{input->val buffer=ByteArray(65536);while(true){val n=input.read(buffer);if(n<0)break;total+=n;require(total<=expected.length());hash.update(buffer,0,n);crc.update(buffer,0,n)}}
                    require(total==expected.length() && entry.crc==crc.value && hash.digest().joinToString(""){"%02x".format(it)}==digest(expected)){"Chaquopy içindeki PyO3 baytları değişti"}
                }
                require(found==1){"Chaquopy app.imy içinde doğrulanmış PyO3 modülü yok"}
            }
        }finally{java.nio.file.Files.deleteIfExists(temporary.toPath())}
    }

    /** Only a real x64 PE executable may satisfy the native EXE output contract.
     * A DLL is not relabelled EXE, and a source-provided receipt never replaces PE/hash checks. */
    fun requireWindowsNative(draft:ProjectDraft):File {
        require(!isWindowsLibrary(draft)){"Bu proje DLL kitaplığıdır; EXE gibi derlenemez. Rust hazırlık kartından doğrulanmış DLL dosyasını dışa aktarın."}
        require(DeviceBuildCapabilities.requestedOutputs(draft.buildOutput)==setOf(DeviceArtifactKind.WINDOWS_NATIVE_EXE)){"Rust Windows native yalnız Native EXE çıktısına bağlanabilir"}
        val root=requireNotNull(sourceRoot(draft));val config=File(root,"appforge-rust-windows.json");RecoveryPathAnchor.check(config)
        require(config.isFile && config.length() in 1..4096)
        val c=JSONObject(config.readText());val name=c.getString("executable")
        require(c.getInt("schema")==1 && c.getString("target")=="x86_64-pc-windows-gnu" && name.matches(Regex("[A-Za-z0-9_-]{1,64}\\.exe")))
        val receipt=File(root,"appforge-rust/artifacts.json");RecoveryPathAnchor.check(receipt)
        require(receipt.isFile && receipt.length() in 1..16384){"Rust Windows hedefini önce ayrı onayla hazırlayın"}
        val j=JSONObject(receipt.readText())
        require(j.getInt("schema")==2 && j.getString("target")=="x86_64-pc-windows-gnu" && j.getString("file")==name && j.getString("sourceSha256")==sourceDigest(root))
        val file=File(receipt.parentFile,name);RecoveryPathAnchor.check(file)
        require(file.isFile && file.length() in 256..128L*1024*1024 && digest(file)==j.getString("sha256")){"Rust Windows kaynak/çıktı bütünlüğü geçersiz"}
        requireWindowsPe(file)
        requireExportDirectory(file)
        return file
    }
    fun requireWindowsPe(file:File,dll:Boolean=false) {
        require(file.isFile && !Files.isSymbolicLink(file.toPath()) && file.length() in 256..128L*1024*1024)
        java.io.RandomAccessFile(file,"r").use { input ->
            fun u16(at:Long):Int {input.seek(at);return input.readUnsignedByte() or (input.readUnsignedByte() shl 8)}
            fun u32(at:Long):Long=u16(at).toLong() or (u16(at+2).toLong() shl 16)
            require(u16(0)==0x5a4d)
            val off=u32(60);require(off>=64 && off<=file.length()-24)
            require(u32(off)==0x4550L && u16(off+4)==0x8664)
            val sections=u16(off+6);val optional=u16(off+20);val flags=u16(off+22)
            require(sections in 1..96 && optional>=112 && flags and 2!=0 && (flags and 0x2000!=0)==dll && off+24+optional+sections*40L<=file.length()){"Rust Windows PE türü EXE/DLL bildirimiyle uyuşmuyor"}
            require(u16(off+24)==0x20b)
            for(i in 0 until sections) {
                val section=off+24+optional+i*40L;val bytes=u32(section+16);val start=u32(section+20)
                require(start<=file.length() && bytes<=file.length()-start){"Rust PE bölüm aralığı geçersiz"}
            }
        }
    }
    fun isWindowsLibrary(draft:ProjectDraft):Boolean=runCatching {
        if(draft.sourceBuildEngine!="windows-native")return@runCatching false
        val root=sourceRoot(draft)?:return@runCatching false
        val file=File(root,"appforge-rust-windows.json");RecoveryPathAnchor.check(file)
        if(!file.isFile || file.length() !in 1..4096)return@runCatching false
        val j=JSONObject(file.readText());j.getInt("schema")==2 && j.getString("target")=="x86_64-pc-windows-gnu"
    }.getOrDefault(false)
    fun requireWindowsLibrary(draft:ProjectDraft):File {
        require(isWindowsLibrary(draft))
        val root=requireNotNull(sourceRoot(draft));val c=JSONObject(File(root,"appforge-rust-windows.json").readText())
        val name=c.getString("library");val symbol=c.getString("export")
        require(name.matches(Regex("[A-Za-z0-9_-]{1,64}\\.dll")) && symbol.matches(Regex("[A-Za-z_][A-Za-z0-9_]{0,127}")))
        val record=File(root,"appforge-rust/artifacts.json");RecoveryPathAnchor.check(record);require(record.isFile && record.length() in 1..16384)
        val j=JSONObject(record.readText())
        require(j.getInt("schema")==2 && j.getString("target")=="x86_64-pc-windows-gnu" && j.getString("artifactKind")=="dll" && j.getString("file")==name && j.getString("sourceSha256")==sourceDigest(root))
        val file=File(record.parentFile,name);RecoveryPathAnchor.check(file)
        require(file.isFile && digest(file)==j.getString("sha256") && symbol in windowsDllExports(file)){"DLL kaynak/PE/export bütünlüğü doğrulanamadı"}
        requireExportDirectory(file);return file
    }
    fun windowsDllExports(file:File):Set<String> {
        requireWindowsPe(file,true)
        val bytes=nativeBytes(file);val b=java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        fun u16(at:Long):Int {require(at>=0 && at<=bytes.size-2);return b.getShort(at.toInt()).toInt() and 65535}
        fun u32(at:Long):Long {require(at>=0 && at<=bytes.size-4);return b.getInt(at.toInt()).toLong() and 0xffffffffL}
        val off=u32(60);val count=u16(off+6);val width=u16(off+20);val optional=off+24
        require(off>=64 && count in 1..96 && width>=120 && optional+width+count*40L<=bytes.size && u32(optional+108)>=1)
        val export=u32(optional+112);val length=u32(optional+116)
        fun rva(value:Long,size:Long=1,code:Boolean=false):Int {
            for(i in 0 until count) {
                val section=optional+width+i*40L;val va=u32(section+12);val raw=u32(section+16);val start=u32(section+20)
                require(start<=bytes.size && raw<=bytes.size-start)
                if(value>=va && value+size<=va+raw && (!code || u32(section+36) and 0x20000000L!=0L))return (start+value-va).toInt()
            }
            throw IllegalArgumentException("DLL RVA bildirilen dosya/kod bölümlerinin dışında")
        }
        val directory=rva(export,40).toLong();val functions=u32(directory+20);val names=u32(directory+24)
        require(functions in 1..65536 && names in 1..4096)
        val table=rva(u32(directory+28),functions*4);val pointers=rva(u32(directory+32),names*4);val ordinals=rva(u32(directory+36),names*2)
        val result=linkedSetOf<String>()
        for(i in 0 until names.toInt()) {
            val ordinal=u16(ordinals+i*2L);require(ordinal<functions)
            val function=u32(table+ordinal*4L);require(function !in export until export+length){"Forwarded export Rust kodu değildir"};rva(function,code=true)
            val nameRva=u32(pointers+i*4L);val name=StringBuilder()
            for(pos in 0..128) {
                val value=bytes[rva(nameRva+pos)].toInt() and 255
                if(value==0)break
                require(pos<128 && value<128);name.append(value.toChar())
            }
            require(name.toString().matches(Regex("[A-Za-z_][A-Za-z0-9_]{0,127}")));result.add(name.toString())
        }
        return result
    }
    fun stageWindowsNative(draft:ProjectDraft,workspace:File):File {
        val source=requireWindowsNative(draft);RecoveryPathAnchor.check(workspace)
        val directory=File(workspace,"windows-native-out");RecoveryPathAnchor.check(directory)
        require(directory.isDirectory || directory.mkdir())
        val output=File(directory,source.name);RecoveryPathAnchor.check(output)
        if(output.exists())require(output.isFile && digest(output)==digest(source)){"Yabancı Windows çıktısı korunur; üzerine yazılmadı"}
        else {require(directory.usableSpace>source.length()+1024*1024);source.inputStream().use{input->output.outputStream().use{input.copyTo(it)}}}
        require(digest(output)==digest(source));requireWindowsPe(output);return output
    }

    /** A real defined JNI symbol is required; a source-supplied receipt is not
     * independently trusted. Only ELF64 ARM64 shared libraries are accepted. */
    fun androidJniExports(file:File):Set<String> = androidDynamicExports(file).filter{it.startsWith("Java_") || it=="JNI_OnLoad"}.toSet().also {require(it.isNotEmpty()){"Rust JNI export yok; C ABI tek başına JNI değildir"}}
    private fun nativeBytes(file:File):ByteArray {
        val length=file.length()
        require(file.isFile && length in 64..64L*1024*1024 && !Files.isSymbolicLink(file.toPath()))
        RecoveryPathAnchor.check(file)
        val bytes=ByteArray(length.toInt())
        file.inputStream().use{input->var offset=0;while(offset<bytes.size){val n=input.read(bytes,offset,bytes.size-offset);require(n>0);offset+=n};require(input.read()==-1){"ELF doğrulama sırasında değişti"}}
        return bytes
    }
    fun androidDynamicExports(file:File):Set<String> = androidExportsBytes(nativeBytes(file))
    private fun androidExportsBytes(bytes:ByteArray):Set<String> {
        val b=java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
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
        val names=linkedSetOf<String>();var symbolsSeen=0L
        for(i in 0 until count) {
            val sec=section(i);if(b.getInt(sec+4)!=11)continue
            val start=b.getLong(sec+24);val length=b.getLong(sec+32);val width=b.getLong(sec+56)
            require(width==24L && start>=0 && length>=0 && length%24==0L && start<=bytes.size.toLong()-length)
            symbolsSeen+=length/24;require(symbolsSeen<=100000)
            val strings=section(b.getInt(sec+40));require(b.getInt(strings+4)==3)
            val text=b.getLong(strings+24);val textSize=b.getLong(strings+32)
            require(text>=0 && textSize>=0 && text<=bytes.size.toLong()-textSize)
            var pos=start
            while(pos<start+length) {
                val p=pos.toInt();val info=bytes[p+4].toInt() and 255;val other=bytes[p+5].toInt() and 3
                if(b.getShort(p+6).toInt()!=0 && info shr 4 in setOf(1,2) && info and 15==2 && other in setOf(0,3)) {
                    val name=b.getInt(p);require(name>=0 && name.toLong()<textSize)
                    val first=(text+name).toInt();var end=first
                    while(end<text+textSize && end-first<=1024 && bytes[end]!=0.toByte())end++
                    require(end<text+textSize && end-first<=1024)
                    val symbol=String(bytes,first,end-first,Charsets.US_ASCII)
                    names.add(symbol)
                }
                pos+=24
            }
        }
        return names
    }
    fun androidDynamicLibraries(file:File):Pair<String?,Set<String>> {
        val bytes=nativeBytes(file);androidExportsBytes(bytes)
        val b=java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val offset=b.getLong(40);val count=b.getShort(60).toInt() and 65535
        require(offset>=64 && count in 1..4096 && offset<=bytes.size.toLong()-count*64L)
        val libraries=linkedSetOf<String>();var soname:String?=null;var examined=0L
        for(i in 0 until count) {
            val section=(offset+i*64).toInt();if(b.getInt(section+4)!=6)continue
            val start=b.getLong(section+24);val size=b.getLong(section+32);val link=b.getInt(section+40)
            require(start>=0 && size>=0 && size%16==0L && b.getLong(section+56)==16L && start<=bytes.size.toLong()-size && link in 0 until count)
            examined+=size/16;require(examined<=65536)
            val strings=(offset+link*64).toInt();val text=b.getLong(strings+24);val length=b.getLong(strings+32)
            require(b.getInt(strings+4)==3 && text>=0 && length>=0 && text<=bytes.size.toLong()-length)
            var pos=start
            while(pos<start+size) {
                val tag=b.getLong(pos.toInt());val value=b.getLong(pos.toInt()+8);pos+=16
                if(tag !in setOf(1L,14L,15L,29L))continue
                require(value>=0 && value<length)
                val first=(text+value).toInt();var end=first
                while(end<text+length && end-first<=1024 && bytes[end]!=0.toByte())end++
                require(end<text+length && end-first<=1024)
                val name=String(bytes,first,end-first,Charsets.US_ASCII)
                if(tag==15L || tag==29L)require(name.isEmpty()){"Doğrulanmamış Android RPATH/RUNPATH"}
                else {
                    require(name.matches(Regex("[A-Za-z0-9_+.-]+\\.so"))){"Android ELF mutlak/güvensiz kütüphane yolu içeriyor"}
                    if(tag==1L){libraries.add(name);require(libraries.size<=256)}else{require(soname==null);soname=name}
                }
            }
        }
        return soname to libraries
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
        requireExportDirectory(file)
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
        if(sourceRoot(draft)==null || draft.sourceBuildEngine.trim().lowercase() !in setOf("android-gradle","android-ndk"))return
        val expected=requireAndroidJni(draft);val path=(if(kind=="aab")"base/" else "")+"lib/arm64-v8a/"+expected.name
        java.util.zip.ZipFile(file).use { zip ->
            val nativePrefix=if(kind=="aab")"base/lib/" else "lib/"
            require(zip.entries().asSequence().filter{!it.isDirectory && it.name.startsWith(nativePrefix) && it.name.endsWith(".so")}.all{it.name.removePrefix(nativePrefix).substringBefore('/')=="arm64-v8a"}){"Rust JNI yalnız ARM64 için doğrulandı; Gradle abiFilters=arm64-v8a ile sınırlandırın"}
            val entries=zip.entries().asSequence().filter{it.name==path}.toList();require(entries.size==1){"APK/AAB içinde Rust JNI yok veya çift kayıt var"}
            val hash=MessageDigest.getInstance("SHA-256");var size=0L
            zip.getInputStream(entries.single()).use{input->val buf=ByteArray(65536);while(true){val n=input.read(buf);if(n<0)break;size+=n;require(size<=expected.length());hash.update(buf,0,n)}}
            require(size==expected.length() && hash.digest().joinToString(""){"%02x".format(it)}==digest(expected)){"Paketleyici Rust JNI dosyasını değiştirdi; Gradle keepDebugSymbols ile doğrulanmış baytları koruyun"}
        }
    }

    private fun webArtifact(draft:ProjectDraft):Pair<String,File>? {
        val root=sourceRoot(draft)?:return null
        requirePackageable(draft)
        require(draft.sourceBuildEngine in setOf("webview-static","node-web")){"Bu motorun Rust çıktısı WASM web payload'ı değildir"}
        val receipt=File(root,"appforge-rust/artifacts.json");val j=JSONObject(receipt.readText())
        val relative="appforge-rust/"+j.getString("file")
        return relative to File(root,relative)
    }
    fun requireWebSite(draft:ProjectDraft,site:File) {
        val (path,expected)=webArtifact(draft)?:return
        val approved=RecoveryPathAnchor.androidPlatformPath(site);RecoveryPathAnchor.check(approved)
        val actual=File(approved,path);RecoveryPathAnchor.check(actual)
        require(actual.isFile && actual.length()==expected.length() && digest(actual)==digest(expected)){"Web çıktısı doğrulanmış Rust WASM dosyasını içermiyor/değiştirdi"}
    }
    /** Verify the central directory used by the Windows extractor, not only
     * local ZIP headers. No project paths are extracted or executed. */
    fun verifyWebArchive(draft:ProjectDraft,archive:File) {
        val (path,expected)=webArtifact(draft)?:return
        var found=0;var files=0;var expanded=0L;val paths=hashSetOf<String>()
        java.util.zip.ZipFile(archive).use { zip ->
            val entries=zip.entries()
            while(entries.hasMoreElements()) {
                val entry=entries.nextElement()
                require(++files<=10000)
                require(!entry.name.startsWith('/') && !entry.name.contains('\\') && entry.name.split('/').none{it==".." || it=="."} && !entry.name.contains('\u0000')){"Web arşiv yolu güvenli değil"}
                val parts=entry.name.split('/').filter{it.isNotEmpty()}
                require(entry.name.isNotBlank() && entry.name.trim()==entry.name && !entry.name.contains(':') && parts.isNotEmpty() && entry.name.trimEnd('/')==parts.joinToString("/"))
                require(parts.all{part->!part.endsWith('.') && !part.endsWith(' ') && !Regex("(?i)^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?$").matches(part)}){"Windows dosya adı güvenli değil"}
                require(paths.add(parts.joinToString("/").lowercase(java.util.Locale.ROOT))){"Windows arşivinde büyük/küçük harf veya çift yol çakışması"}
                val selected=entry.name==path
                if(selected)require(!entry.isDirectory && ++found==1){"Rust WASM çift/dizin kaydı"}
                val hash=if(selected)MessageDigest.getInstance("SHA-256")else null;val crc=java.util.zip.CRC32();var bytes=0L;val buffer=ByteArray(65536)
                zip.getInputStream(entry).use{input->while(true){val n=input.read(buffer);if(n<0)break;expanded+=n;bytes+=n;require(expanded<=500L*1024*1024);crc.update(buffer,0,n);if(selected){require(bytes<=expected.length());hash!!.update(buffer,0,n)}}}
                require(entry.size==bytes && entry.crc==crc.value){"Windows ZIP merkez dizini boyut/CRC doğrulaması başarısız"}
                if(selected)require(bytes==expected.length() && hash!!.digest().joinToString(""){"%02x".format(it)}==digest(expected)){"Windows arşivindeki Rust WASM baytları değişti"}
            }
        }
        require(found==1){"Windows payload içinde Rust WASM bulunamadı"}
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
