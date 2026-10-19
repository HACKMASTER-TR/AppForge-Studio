package com.appforge.studio.build

import java.io.File
import java.nio.file.*
import java.nio.file.attribute.PosixFilePermission
import org.json.JSONObject

/** Exact install deltas, not filename guesses. Shared or foreign modifications block removal. */
internal class ModuleOwnershipStore(private val root:File) {
    private val control="opt/appforge-device/ownership-v5"
    private fun namespacePrefixes(id:OfflineModuleId):List<String> {
        val prefixes=when(id) {
            OfflineModuleId.NODE_WEB->listOf("opt/appforge-device/node-22.23.3/")
            OfflineModuleId.ANDROID_CORE->listOf("opt/appforge-device/android-sdk/platforms/android-37.0/","opt/appforge-device/android-sdk/build-tools/36.0.0/","opt/appforge-device/android-sdk/cmdline-tools/","opt/appforge-device/jdk-17/","opt/appforge-device/gradle-9.3.1/","opt/appforge-device/gradle-8.14.3/")
            OfflineModuleId.EXPO_REACT_NATIVE->listOf("opt/appforge-device/android-sdk/ndk/27.1.12297006/","opt/appforge-device/android-sdk/cmake/3.22.1/","opt/appforge-device/android-sdk/platforms/android-36/","usr/lib/llvm-18/")
            OfflineModuleId.PYTHON_ANDROID->listOf("usr/lib/python3.12/","usr/lib/python3/dist-packages/")
            OfflineModuleId.WINDOWS_NATIVE->listOf("usr/x86_64-w64-mingw32/","usr/lib/gcc/x86_64-w64-mingw32/","usr/bin/x86_64-w64-mingw32-")
            else->emptyList()
        }
        return prefixes+"opt/appforge-device/apt-locks-v5/${id.name}/"
    }
    private fun namespace(id:OfflineModuleId,key:String)=namespacePrefixes(id).any{key.startsWith(it)}
    // Build/package-manager caches and logs are mutable; project readiness attests caches separately.
    private fun readinessTracked(key:String)=listOf("root/.gradle-appforge/","root/.gradle/","root/.cache/","root/.npm/","var/cache/","var/log/","var/lib/apt/lists/","tmp/","run/","opt/appforge-device/cache/","opt/appforge-device/npm-cache-v1/","opt/appforge-device/npm-cache-expo54-v1/").none{key==it.trimEnd('/') || key.startsWith(it)}
    fun verifyNamespace(id:OfflineModuleId):Boolean=runCatching {
        val own=read(id).getJSONObject("changes");val store=RuntimeGenerationStore(root)
        val latest=mutableMapOf<String,Pair<Long,RuntimeGenerationStore.Entry?>>()
        val directory=path(root,control)
        for(file in directory.listFiles().orEmpty().filter{it.name.endsWith(".json")}) {
            require(!Files.isSymbolicLink(file.toPath()) && file.length()<=16L*1024*1024)
            val record=JSONObject(file.readText());require(record.getInt("schema")==1)
            require(record.getString("module")+".json"==file.name)
            val order=record.optLong("sequence",0);require(order in 0..1_000_000_000_000L)
            val changes=record.getJSONObject("changes")
            for(key in changes.keys()) {
                require(!metadata(key));path(root,key)
                if(!readinessTracked(key) || !own.has(key) && !namespace(id,key))continue
                val expected=decode(changes.getJSONObject(key).optJSONObject("after"))
                val previous=latest[key]
                if(previous==null || order>previous.first)latest[key]=order to expected
                else if(order==previous.first)require(previous.second==expected){"Çakışan eski sahiplik kaydı"}
            }
        }
        require(own.keys().asSequence().any{namespace(id,it)})
        val keys=(own.keys().asSequence().filter{readinessTracked(it)}.toSet()+latest.keys.filter{namespace(id,it)})
        require(keys.all{key->store.inspectRelative(key)==latest.getValue(key).second})
        // An extra file in a private runtime namespace is not silently blessed as installed.
        for(prefix in namespacePrefixes(id)) {
            if(!prefix.endsWith('/')) {
                val parent=path(root,prefix).parentFile
                for(child in parent.listFiles().orEmpty().filter{it.name.startsWith(prefix.substringAfterLast('/'))})require(latest[child.relativeTo(root).invariantSeparatorsPath]?.second!=null)
                continue
            }
            val base=path(root,prefix.trimEnd('/'))
            if(!Files.exists(base.toPath(),LinkOption.NOFOLLOW_LINKS))continue
            require(!Files.isSymbolicLink(base.toPath()) && base.isDirectory)
            var count=0
            Files.walkFileTree(base.toPath(),object:SimpleFileVisitor<Path>() {
                private fun inspect(p:Path){require(++count<=250000);if(p==base.toPath())return;val relative=p.toFile().relativeTo(root).invariantSeparatorsPath;require(latest[relative]?.second!=null){"Sahipsiz runtime dosyası: $relative"}}
                override fun preVisitDirectory(dir:Path,attrs:java.nio.file.attribute.BasicFileAttributes):FileVisitResult {inspect(dir);return FileVisitResult.CONTINUE}
                override fun visitFile(file:Path,attrs:java.nio.file.attribute.BasicFileAttributes):FileVisitResult {inspect(file);return FileVisitResult.CONTINUE}
            })
        }
        true
    }.getOrDefault(false)
    private fun metadata(path:String)=path in setOf("opt/appforge-device/modules-v3","opt/appforge-device/apt-locks-v5") || control.startsWith("$path/") || path==control || path.startsWith("$control/") || path=="opt/appforge-device/generations-v4" || path.startsWith("opt/appforge-device/generations-v4/")
    private fun path(base:File,relative:String):File {
        require(relative.isNotEmpty() && !File(relative).isAbsolute && relative.split('/').none{it.isEmpty()||it=="."||it==".."})
        val f=File(base,relative);require(f.parentFile.canonicalFile.toPath().startsWith(base.canonicalFile.toPath())){"Dosya üst yolu başka alana yönleniyor."}
        return f
    }
    private fun content(digest:String):File {
        require(digest.matches(Regex("[a-f0-9]{64}")))
        val f=File(root.parentFile,".module-ownership-cas-v5/$digest")
        require(f.parentFile.canonicalFile==f.parentFile.absoluteFile && !Files.isSymbolicLink(f.toPath()))
        return f
    }
    private fun receipt(id:OfflineModuleId)=path(root,"$control/${id.name}.json")
    private fun entry(e:RuntimeGenerationStore.Entry?)=e?.let{JSONObject().put("kind",it.kind).put("size",it.size).put("digest",it.digest).put("executable",it.executable).put("permissions",it.permissions)}
    private fun decode(j:JSONObject?)=j?.let{RuntimeGenerationStore.Entry(it.getString("kind"),it.getLong("size"),it.getString("digest"),it.getBoolean("executable"),it.getString("permissions"))}
    private fun read(id:OfflineModuleId):JSONObject {
        val f=receipt(id);require(f.isFile && !Files.isSymbolicLink(f.toPath()) && f.length()<=16L*1024*1024)
        return JSONObject(f.readText()).also{require(it.getInt("schema")==1 && it.getString("module")==id.name)}
    }
    fun record(id:OfflineModuleId,previousRootName:String,before:Map<String,RuntimeGenerationStore.Entry>,after:Map<String,RuntimeGenerationStore.Entry>,beforeRoot:File?=null) {
        require(previousRootName.matches(Regex("\\.v3-previous-[0-9a-f-]{36}")))
        val old=if(receipt(id).exists())read(id) else null
        require(before[BaseRuntimeOwnership.FILE]==after[BaseRuntimeOwnership.FILE]){"Vendor sahiplik kaydı değişti."}
        val known=BaseRuntimeOwnership.paths(root).toMutableSet()
        val directory=path(root,control)
        var sequence=0L
        for(file in directory.listFiles().orEmpty().filter{it.name.endsWith(".json")} ) {
            val relative=file.relativeTo(root).invariantSeparatorsPath
            require(before[relative]==after[relative]){"Başka modülün sahiplik kaydı değişti."}
            require(!Files.isSymbolicLink(file.toPath()) && file.length()<=16L*1024*1024)
            val existing=JSONObject(file.readText());val order=existing.optLong("sequence",0);require(order in 0 until 1_000_000_000_000L)
            sequence=maxOf(sequence,order)
            val owner=existing.getJSONObject("changes")
            for(key in owner.keys())if(owner.getJSONObject(key).optJSONObject("after")!=null)known.add(key)
        }
        val protectedUserChanges=(before.keys+after.keys).filter{key->
            (key.startsWith("home/") || key.startsWith("root/")) && readinessTracked(key) && before[key]!=after[key]
        }
        require(protectedUserChanges.isEmpty()){"Kurulum kullanıcı kaynak/ayar dizinini değiştirdi; çalışan ortam korunur."}
        val unknownRemoved=before.keys.filter{it.isNotEmpty() && !metadata(it) && readinessTracked(it) && before[it]!=after[it] && it !in known}
        require(unknownRemoved.isEmpty()){"Kurulum kullanıcıya ait/sahipliği bilinmeyen dosyaları değiştirdi; eski ortam korundu: "+unknownRemoved.take(3).joinToString()}

        val changes=JSONObject()
        for(key in (before.keys+after.keys).filter{it.isNotEmpty() && !metadata(it) && readinessTracked(it) && (before[it]!=after[it] || namespace(id,it))}.sorted())
            changes.put(key,JSONObject().put("before",entry(before[key])).put("after",entry(after[key])))
        // Repair/update preserves the original ownership baseline; shared writes still fail removal checks.
        old?.getJSONObject("changes")?.let{prior->for(key in prior.keys()) {
            if(!readinessTracked(key))continue
            val priorEntry=prior.getJSONObject(key)
            if(namespace(id,key) && before[key]==after[key])require(decode(priorEntry.optJSONObject("after"))==after[key]){"Onarılamayan modül dosyası değişmiş: $key"}
            val next=changes.optJSONObject(key)?:JSONObject().put("after",entry(after[key]))
            next.put("before",prior.getJSONObject(key).optJSONObject("before"));changes.put(key,next)
        }}
        val useContentStore=beforeRoot!=null
        if(useContentStore)for(key in changes.keys()) {
            val original=decode(changes.getJSONObject(key).optJSONObject("before"))?:continue
            if(original.kind!="file")continue
            val dst=content(original.digest)
            if(dst.isFile && dst.length()==original.size && VerifiedModuleDownload.sha(dst)==original.digest)continue
            val prior=old?.getJSONObject("changes")?.has(key)==true
            val sourceBase=if(prior)File(root.parentFile,old!!.getString("previous")) else beforeRoot!!
            val source=path(sourceBase,key)
            require(source.isFile && !Files.isSymbolicLink(source.toPath()) && VerifiedModuleDownload.sha(source)==original.digest){"Önceki sahipli içerik doğrulanamadı: $key"}
            dst.parentFile.mkdirs();require(dst.parentFile.usableSpace>source.length()+64L*1024*1024)
            val tmp=File(dst.path+".part-${java.util.UUID.randomUUID()}")
            Files.copy(source.toPath(),tmp.toPath());require(VerifiedModuleDownload.sha(tmp)==original.digest)
            Files.move(tmp.toPath(),dst.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
        }
        val f=receipt(id);f.parentFile.mkdirs();require(!Files.isSymbolicLink(f.toPath()))
        val value=JSONObject().put("schema",1).put("module",id.name).put("sequence",sequence+1).put("previous",old?.getString("previous")?:previousRootName).put("changes",changes).put("beforeStore",if(useContentStore)"CAS1" else "SNAPSHOT")
            .put("lifecycleRevision",2).put("version",OfflineModules.spec(id).version)
            .put("dependencies",org.json.JSONArray(OfflineModules.spec(id).dependencies.map{it.name}.sorted()))
            .put("generation",previousRootName.removePrefix(".v3-previous-"))
            .put("previousVersion",old?.optString("version","")?.takeIf{it.isNotBlank()})
            .put("installState","VERIFIED_FILES")
        val tmp=File(f.path+".tmp");require(!Files.isSymbolicLink(tmp.toPath()))
        java.io.FileOutputStream(tmp).use{it.write(value.toString().toByteArray());it.fd.sync()}
        Files.move(tmp.toPath(),f.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
    }
    private fun validate(id:OfflineModuleId):Pair<JSONObject,File> {
        val record=read(id);val name=record.getString("previous")
        require(name.matches(Regex("\\.v3-previous-[0-9a-f-]{36}")))
        val old=File(root.parentFile,name);val cas=record.optString("beforeStore")=="CAS1"
        require(cas || old.isDirectory && !Files.isSymbolicLink(old.toPath()))
        val oldStore=RuntimeGenerationStore(old);val current=RuntimeGenerationStore(root);val changes=record.getJSONObject("changes")
        val createdDirectories=mutableListOf<String>()
        require(changes.length()>0){"Boş sahiplik kaydı kaldırma kanıtı değildir."}
        for(key in changes.keys()) {
            require(!metadata(key));path(root,key);if(!cas)path(old,key)
            val e=changes.getJSONObject(key);val expected=decode(e.optJSONObject("after"));val original=decode(e.optJSONObject("before"))
            require(current.inspectRelative(key)==expected){"Başka modül/kullanıcı dosyayı değiştirdi: $key"}
            if(cas && original?.kind=="file") {
                val saved=content(original.digest)
                require(saved.isFile && saved.length()==original.size && VerifiedModuleDownload.sha(saved)==original.digest){"Önceki dosya içeriği bozuk: $key"}
            }else if(!cas)require(oldStore.inspectRelative(key)==original){"Önceki dosya bütünlüğü değişti: $key"}
            if(expected?.kind=="directory" && original?.kind!="directory")createdDirectories.add(key)
        }
        val outer=createdDirectories.filter{key->createdDirectories.none{other->other!=key && key.startsWith("$other/")}}
        for(key in outer) {
            var count=0
            Files.walkFileTree(path(root,key).toPath(),object:SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir:Path,attrs:java.nio.file.attribute.BasicFileAttributes):FileVisitResult {require(++count<=250000 && changes.has(dir.toFile().relativeTo(root).invariantSeparatorsPath)){"Sahipliği bilinmeyen klasör"};return FileVisitResult.CONTINUE}
                override fun visitFile(file:Path,attrs:java.nio.file.attribute.BasicFileAttributes):FileVisitResult {require(++count<=250000 && changes.has(file.toFile().relativeTo(root).invariantSeparatorsPath)){"Klasörde başka sahipli dosya var: $key"};return FileVisitResult.CONTINUE}
            })
        }
        return record to old
    }
    data class LifecycleInventory(val module:OfflineModuleId,val version:String,val dependencies:Set<OfflineModuleId>,val generation:String,val previousVersion:String?,val fileCount:Int,val manifestSha256:String)
    fun lifecycleInventory(id:OfflineModuleId):LifecycleInventory {
        val j=read(id);require(j.optInt("lifecycleRevision")==2){"Eski sahiplik kaydı yaşam döngüsü için doğrulanmış sürüm/generation içermiyor."}
        require(j.getString("installState")=="VERIFIED_FILES")
        val version=j.getString("version");require(version.length in 1..300)
        val generation=j.getString("generation");require(generation.matches(Regex("[0-9a-f-]{36}")))
        val deps=j.getJSONArray("dependencies");require(deps.length()<=OfflineModuleId.entries.size)
        val dependencies=(0 until deps.length()).map{OfflineModuleId.valueOf(deps.getString(it))}.toSet()
        require(dependencies==OfflineModules.spec(id).dependencies){"Sahiplik bağımlılık grafiği geçerli tarifle uyumsuz."}
        require(verifyNamespace(id)){"Modül dosya/sahiplik bütünlüğü doğrulanamadı."}
        return LifecycleInventory(id,version,dependencies,generation,j.optString("previousVersion").takeIf{it.isNotBlank()},j.getJSONObject("changes").length(),VerifiedModuleDownload.sha(receipt(id)))
    }
    /** Shared OS/package-database restoration is not an independent package uninstall. */
    fun safeRemovalReason(id:OfflineModuleId):String?=runCatching {
        lifecycleInventory(id)
        val (record,_)=validate(id);val changes=record.getJSONObject("changes")
        for(key in changes.keys()) {
            val change=changes.getJSONObject(key);val before=decode(change.optJSONObject("before"));val after=decode(change.optJSONObject("after"))
            if(before==after)continue
            val privatePath=namespace(id,key) || namespacePrefixes(id).any{it.startsWith("$key/")}
            val alias=id==OfflineModuleId.NODE_WEB && key in setOf("usr/local/bin/node","usr/local/bin/npm","usr/local/bin/npx") && after?.kind=="link" && after.digest.startsWith("/opt/appforge-device/node-22.23.3/bin/")
            require(privatePath || alias){"Ortak APT/rootfs dosyası bağımsız kaldırılamaz: $key"}
            for(other in path(root,control).listFiles().orEmpty().filter{it.extension=="json" && it.name!=id.name+".json"}) {
                require(!Files.isSymbolicLink(other.toPath()) && other.length()<=16L*1024*1024)
                val claims=JSONObject(other.readText()).getJSONObject("changes")
                require(!claims.has(key)){"Başka modül aynı dosyayı kullanıyor: $key"}
            }
        }
        null
    }.getOrElse{it.message?:"Güvenli kaldırma kanıtı eksik."}
    fun protectedSnapshots():Set<String> {
        val dir=path(root,control)
        return dir.listFiles().orEmpty().filter{it.name.endsWith(".json")}.mapNotNull{f->
            require(!Files.isSymbolicLink(f.toPath()) && f.length()<=16L*1024*1024)
            val j=JSONObject(f.readText());if(j.optString("beforeStore")=="CAS1")null else j.getString("previous").also{require(it.matches(Regex("\\.v3-previous-[0-9a-f-]{36}")))}
        }.toSet()
    }
    fun removalReason(id:OfflineModuleId):String?=runCatching{validate(id);null}.getOrElse{it.message?:"Sahiplik doğrulanamadı."}
    fun restoreInto(id:OfflineModuleId,staged:File) {
        val(record,old)=validate(id);val changes=record.getJSONObject("changes")
        val keys=changes.keys().asSequence().toList()
        // Stage must be an exact private clone of the inspected root, not a caller-provided target.
        require(staged.parentFile.canonicalFile==root.parentFile.canonicalFile && staged.name.matches(Regex("\\.v3-stage-[0-9a-f-]{36}")))
        require(RuntimeGenerationStore(staged).snapshot()==RuntimeGenerationStore(root).snapshot())
        for(key in keys.sortedByDescending{it.count{c->c=='/'}}) {
            val e=changes.getJSONObject(key);val after=decode(e.optJSONObject("after"));val before=decode(e.optJSONObject("before"))
            if(after!=null && !(after.kind=="directory" && before?.kind=="directory"))Files.delete(path(staged,key).toPath())
        }
        for(key in keys.sortedBy{it.count{c->c=='/'}}) {
            val before=decode(changes.getJSONObject(key).optJSONObject("before"))?:continue
            val dst=path(staged,key).toPath();val src=if(record.optString("beforeStore")=="CAS1" && before.kind=="file")content(before.digest).toPath() else if(record.optString("beforeStore")=="CAS1")dst else path(old,key).toPath()
            when(before.kind) {
                "directory"->if(!Files.exists(dst,LinkOption.NOFOLLOW_LINKS))Files.createDirectory(dst)
                "file"->Files.copy(src,dst,StandardCopyOption.COPY_ATTRIBUTES)
                "link"->Files.createSymbolicLink(dst,if(record.optString("beforeStore")=="CAS1")Paths.get(before.digest) else Files.readSymbolicLink(src))
                else->error("Bilinmeyen dosya türü")
            }
        }
        for(key in keys.sortedByDescending{it.count{c->c=='/'}}) {
            val before=decode(changes.getJSONObject(key).optJSONObject("before"))?:continue
            if(before.kind!="link")Files.setPosixFilePermissions(path(staged,key).toPath(),before.permissions.split(',').filter{it.isNotBlank()}.map{PosixFilePermission.valueOf(it)}.toSet())
        }
        val stagedStore=ModuleOwnershipStore(staged);Files.delete(stagedStore.receipt(id).toPath())
    }
}
