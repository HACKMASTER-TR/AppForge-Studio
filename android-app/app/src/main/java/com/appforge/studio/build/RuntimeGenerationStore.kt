package com.appforge.studio.build

import java.io.File
import java.io.FileOutputStream
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONObject

/** Private build-root generations only. Never follows links while copying/deleting. Original projects stay outside. */
internal class RuntimeGenerationStore @JvmOverloads constructor(private val active:File,private val defaultAvailableBytes:()->Long={requireNotNull(active.parentFile).canonicalFile.usableSpace}) {
    private val parent=requireNotNull(active.absoluteFile.parentFile)
    private val journal=File(parent,".module-generation.json")
    private fun checkAnchor(){require(active.absoluteFile==active.canonicalFile && active.parentFile.canonicalFile==parent && active.isDirectory && !Files.isSymbolicLink(parent.toPath()))}
    private fun child(name:String):File {require(name.matches(Regex("\\.v3-(stage|previous|discard)-[0-9a-f-]{36}")));return File(parent,name)}
    private fun atomicWrite(target:File,bytes:ByteArray,availableBytes:()->Long=defaultAvailableBytes) {
        require(!Files.isSymbolicLink(parent.toPath()) && !Files.isSymbolicLink(target.toPath()))
        require(availableBytes()>bytes.size+65536L){"Generation günlüğü için yeterli depolama yok; önceki kayıt korunur."}
        val tmp=File(target.path+".tmp");require(!Files.isSymbolicLink(tmp.toPath()))
        try {
            FileOutputStream(tmp).use{it.write(bytes);it.fd.sync()}
            Files.move(tmp.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
        } catch(e:Throwable) {
            if(!Files.isSymbolicLink(tmp.toPath()))Files.deleteIfExists(tmp.toPath())
            throw e
        }
    }
    private fun write(value:JSONObject,availableBytes:()->Long=defaultAvailableBytes)=atomicWrite(journal,value.toString().toByteArray(Charsets.UTF_8),availableBytes)
    private val activeSeal=File(parent,".v3-active-generation.json")
    private fun seal(digest:String,availableBytes:()->Long=defaultAvailableBytes) {
        require(digest.matches(Regex("[a-f0-9]{64}")))
        atomicWrite(activeSeal,JSONObject().put("schema",1).put("active",active.name).put("digest",digest).toString().toByteArray(Charsets.UTF_8),availableBytes)
    }
    private fun verifyActiveSeal() {
        require(activeSeal.isFile && !Files.isSymbolicLink(activeSeal.toPath()) && activeSeal.length()<8192){"Eski ortamda generation mühürü yok; geri alma güvenle kanıtlanamıyor."}
        val j=JSONObject(activeSeal.readText());require(j.getInt("schema")==1 && j.getString("active")==active.name)
        require(treeDigest(inventory(active,{}))==j.getString("digest")){"Aktif ortam son geçişten sonra değişmiş; kullanıcı dosyaları korunarak geri alma engellendi."}
    }
    private fun cleanStaleTmpSidecars() {
        for(sidecar in listOf(File(journal.path+".tmp"),File(activeSeal.path+".tmp"),File(snapshotRegistry.path+".tmp"))) {
            require(!Files.isSymbolicLink(sidecar.toPath())){"Generation geçici dosyası symlink olamaz"}
            Files.deleteIfExists(sidecar.toPath())
        }
    }
    fun recover() {
        RecoveryPathAnchor.siblings(active,journal,activeSeal,snapshotRegistry,
            File(journal.path+".tmp"),File(activeSeal.path+".tmp"),File(snapshotRegistry.path+".tmp"))
        if(!journal.exists()){cleanStaleTmpSidecars();return}
        require(journal.isFile && !Files.isSymbolicLink(journal.toPath()) && journal.length()<8192)
        val j=JSONObject(journal.readText());require(j.getString("active")==active.name)
        val stage=child(j.getString("stage"));val old=child(j.getString("previous"))
        require(j.optInt("schema",0)==2){"Eski işlem günlüğünün bütünlük kanıtı yok; tüm ortamlar korundu, otomatik geçiş kapalı."}
        val state=j.getString("state")
        require(state in setOf("COPYING","STAGED","COMMITTED"))
        require(j.getString("previousDigest").matches(Regex("[a-f0-9]{64}")))
        if(state!="COPYING")require(j.getString("stagedDigest").matches(Regex("[a-f0-9]{64}")))
        RecoveryPathAnchor.siblings(active,stage,old)
        // Validate the complete record before any temporary-file cleanup.
        if(state=="COMMITTED") {
            require(treeDigest(inventory(active,{}))==j.getString("stagedDigest")){"Aktifleştirme sonrası ortam değişmiş; kurtarma engellendi, dosyalar korundu."}
            seal(j.getString("stagedDigest"))
        } else if(old.exists()) {
            require(treeDigest(inventory(old,{}))==j.getString("previousDigest")){"Önceki generation bozuk; mevcut ortam değiştirilmedi."}
            if(active.exists()) {
                require(treeDigest(inventory(active,{}))==j.getString("stagedDigest")){"Yeni ortamda yabancı değişiklik var; kurtarma tüm dosyaları koruyarak durdu."}
                // Retain the failed activated environment as quarantine, never erase it.
                Files.move(active.toPath(),child(".v3-discard-${UUID.randomUUID()}").toPath(),StandardCopyOption.ATOMIC_MOVE)
            }
            Files.move(old.toPath(),active.toPath(),StandardCopyOption.ATOMIC_MOVE)
            seal(j.getString("previousDigest"))
        } else {
            if(state=="COPYING")checkAnchor()
            else require(treeDigest(inventory(active,{}))==j.getString("previousDigest")){"Çalışan ortam işlem sırasında değişmiş; kullanıcı verileri ve stage korundu."}
        }
        if(stage.exists()) {
            require(!Files.isSymbolicLink(stage.toPath()))
            val activeEntries=if(active.isDirectory)runCatching{inventory(active,{})}.getOrNull() else null
            val stageEntries=if(state=="COPYING" && !old.exists() && stage.isDirectory)runCatching{inventory(stage,{})}.getOrNull() else null
            // If interrupted during initial clone (COPYING) and every staged entry is an exact unmodified subset of active, reclaim the partial clone safely; otherwise quarantine.
            if(state=="COPYING" && !old.exists() && activeEntries!=null && stageEntries!=null && treeDigest(activeEntries)==j.getString("previousDigest") && stageEntries.all{(k,v)->activeEntries[k]==v}) {
                removeOwned(stage)
            } else {
                Files.move(stage.toPath(),child(".v3-discard-${UUID.randomUUID()}").toPath(),StandardCopyOption.ATOMIC_MOVE)
            }
        }
        cleanStaleTmpSidecars()
        Files.delete(journal.toPath())
    }
    fun quarantinedDirectories():List<File> {
        checkAnchor()
        return parent.listFiles().orEmpty().filter{it.name.matches(Regex("\\.v3-discard-[0-9a-f-]{36}"))}.onEach{require(!Files.isSymbolicLink(it.toPath()))}.sortedBy{it.name}
    }
    fun quarantinedBytes():Long = quarantinedDirectories().sumOf{dir->inventory(dir,{}).values.sumOf{it.size}}
    fun prepare(module:OfflineModuleId,availableBytes:()->Long=defaultAvailableBytes,canceled:()->Unit={}):Prepared=prepareOwner(module.name,availableBytes,canceled)
    fun prepareOwner(owner:String,availableBytes:()->Long=defaultAvailableBytes,canceled:()->Unit={}):Prepared {
        require(owner.matches(Regex("[A-Z0-9_:-]{1,100}")))
        recover();checkAnchor()
        val before=inventory(active,canceled)
        val bytes=before.values.sumOf{it.size}
        require(availableBytes()>bytes+256L*1024*1024){"İzole sürüm için yeterli depolama yok; çalışan modüller değişmedi."}
        val id=UUID.randomUUID().toString();val stage=child(".v3-stage-$id");val old=child(".v3-previous-$id")
        val j=JSONObject().put("active",active.name).put("stage",stage.name).put("previous",old.name).put("module",owner).put("state","COPYING").put("schema",2).put("previousDigest",treeDigest(before))
        write(j,availableBytes)
        try {
            copyTree(active,stage,canceled,availableBytes)
            check(inventory(stage,canceled)==before){"Sürüm kopyası bütünlüğü doğrulanamadı."}
            return Prepared(stage,old,j,before,canceled,availableBytes)
        } catch(e:Throwable) {
            // Prepared.close does not exist yet. Recover the known clone even if its
            // post-copy inventory/cancellation check failed; preserve the original cause.
            try { recover() } catch(cleanup:Throwable) { e.addSuppressed(cleanup) }
            throw e
        }
    }
    inner class Prepared internal constructor(val root:File,private val old:File,private val j:JSONObject,private val before:Map<String,Entry>,private val canceled:()->Unit,private val availableBytes:()->Long=defaultAvailableBytes):AutoCloseable {
        val previousRootName:String get()=old.name
        private var committed=false
        fun commit(verify:(File)->Unit,fault:(String)->Unit={}) {
            canceled();verify(root)
            require(inventory(active,canceled)==before){"Aktif ortam kopyalama sırasında değişti; kullanıcı verileri korunur, geçiş yapılmadı."}
            val after=inventory(root,canceled)
            val changed=(before.keys+after.keys).filter{before[it]!=after[it]}.sorted()
            val ownership=File(root,"opt/appforge-device/generations-v4")
            require(ownership.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()))
            ownership.mkdirs()
            val latest=File(ownership,"latest.json");require(!Files.isSymbolicLink(latest.toPath()))
            latest.writeText(JSONObject().put("module",j.getString("module")).put("previous",old.name)
                .put("previousDigest",treeDigest(before)).put("changed",org.json.JSONArray(changed)).put("schema",1).toString())
            registerSnapshot(old.name,treeDigest(before),availableBytes)
            val stagedDigest=treeDigest(inventory(root,canceled))
            j.put("stagedDigest",stagedDigest)
            j.put("state","STAGED");write(j,availableBytes);fault("before-switch")
            Files.move(active.toPath(),old.toPath(),StandardCopyOption.ATOMIC_MOVE)
            try {
                fault("old-moved")
                Files.move(root.toPath(),active.toPath(),StandardCopyOption.ATOMIC_MOVE)
                fault("new-moved")
                j.put("state","COMMITTED");write(j,availableBytes);fault("committed-marker");seal(stagedDigest,availableBytes);committed=true;journal.delete()
            }catch(e:Throwable){recover();throw e}
        }
        override fun close(){if(!committed)recover()}
    }
    fun rollbackReason(module:OfflineModuleId):String?=runCatching {
        checkAnchor();require(!journal.exists()){"Kesilmiş işlem önce güvenle kurtarılmalı."}
        val f=File(active,"opt/appforge-device/generations-v4/latest.json")
        require(!Files.isSymbolicLink(f.toPath()) && f.canonicalFile.toPath().startsWith(active.canonicalFile.toPath()) && f.isFile && f.length()<=4L*1024*1024)
        val j=JSONObject(f.readText());require(j.getString("module")==module.name){"Yalnız son doğrulanmış modül işlemi geri alınabilir."}
        verifyActiveSeal()
        val old=child(j.getString("previous"))
        require(treeDigest(inventory(old,{}))==j.getString("previousDigest")){"Önceki generation bütünlüğü bozuk."}
        null
    }.getOrElse{it.message?:"Generation doğrulanamadı."}
    fun rollbackAvailable(module:OfflineModuleId):Boolean=rollbackReason(module)==null
    fun rollback(module:OfflineModuleId,availableBytes:()->Long=defaultAvailableBytes,fault:(String)->Unit={},canceled:()->Unit={}) {
        recover();checkAnchor();require(rollbackReason(module)==null){rollbackReason(module)?:"Geri alma engellendi."}
        val record=JSONObject(File(active,"opt/appforge-device/generations-v4/latest.json").readText())
        val old=child(record.getString("previous"));val target=inventory(old,canceled)
        require(availableBytes()>target.values.sumOf{it.size}+256L*1024*1024){"Geri alma stage'i için depolama yetersiz; çalışan ortam korundu."}
        val before=inventory(active,canceled)
        val stage=child(".v3-stage-${UUID.randomUUID()}");val current=child(".v3-previous-${UUID.randomUUID()}")
        registerSnapshot(current.name,treeDigest(before),availableBytes)
        val j=JSONObject().put("schema",2).put("active",active.name).put("stage",stage.name).put("previous",current.name)
            .put("previousDigest",treeDigest(before)).put("stagedDigest",treeDigest(target)).put("module",module.name).put("state","COPYING")
        write(j,availableBytes)
        try {
            copyTree(old,stage,canceled,availableBytes);require(inventory(stage,canceled)==target);canceled()
            require(inventory(active,canceled)==before){"Aktif ortam geri alma sırasında değişti; geçiş engellendi."}
            j.put("state","STAGED");write(j,availableBytes);fault("before-switch")
            Files.move(active.toPath(),current.toPath(),StandardCopyOption.ATOMIC_MOVE);fault("old-moved")
            Files.move(stage.toPath(),active.toPath(),StandardCopyOption.ATOMIC_MOVE);fault("new-moved")
            j.put("state","COMMITTED");write(j,availableBytes);seal(j.getString("stagedDigest"),availableBytes);Files.delete(journal.toPath())
        }catch(e:Throwable){recover();throw e}
    }
    private val snapshotRegistry=File(parent,".v3-snapshots.json")
    private fun registry():JSONObject {
        if(!snapshotRegistry.exists())return JSONObject()
        require(!Files.isSymbolicLink(snapshotRegistry.toPath()) && snapshotRegistry.length()<=1024*1024)
        return JSONObject(snapshotRegistry.readText())
    }
    private fun saveRegistry(j:JSONObject,availableBytes:()->Long=defaultAvailableBytes)=atomicWrite(snapshotRegistry,j.toString().toByteArray(Charsets.UTF_8),availableBytes)
    private fun registerSnapshot(name:String,digest:String,availableBytes:()->Long=defaultAvailableBytes) {val j=registry();require(j.length()<2048);j.put(name,digest);saveRegistry(j,availableBytes)}
    private fun requireOwnedSnapshot(root:File,entries:Map<String,Entry>) {
        val known=BaseRuntimeOwnership.paths(root).toMutableSet()
        require(known.isNotEmpty()){"Eski snapshot'ın vendor sahiplik envanteri yok; kullanıcı dosyaları korunur."}
        known.add(BaseRuntimeOwnership.FILE)
        known.add("opt/appforge-device/generations-v4/latest.json")
        known.addAll(OfflineModuleId.entries.map{"opt/appforge-device/modules-v3/${it.name}.json"})
        val ownership=File(root,"opt/appforge-device/ownership-v5")
        require(ownership.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()))
        for(file in ownership.listFiles().orEmpty()) {
            require(!Files.isSymbolicLink(file.toPath()) && file.isFile && file.length()<=16L*1024*1024)
            val record=JSONObject(file.readText());require(record.getInt("schema")==1)
            val id=OfflineModuleId.valueOf(record.getString("module"));require(file.name==id.name+".json")
            val changes=record.getJSONObject("changes")
            for(key in changes.keys())if(!changes.getJSONObject(key).isNull("after"))known.add(key)
            known.add(file.relativeTo(root).invariantSeparatorsPath)
        }
        for((key,value) in entries) {
            if(key.isEmpty() || key in known)continue
            if(value.kind=="directory" && known.any{it.startsWith("$key/")})continue
            if(key=="opt/appforge-device/generations-v4/latest.json" && value.kind=="file")continue
            if(key.startsWith("opt/appforge-device/modules-v3/") && key.substringAfterLast('/') in OfflineModuleId.entries.map{it.name+".json"} && value.kind=="file")continue
            require(false){"Snapshot içinde kullanıcıya ait/sahipsiz içerik var; otomatik temizlenmez: $key"}
        }
    }
    fun minimumStagingBytes():Long {
        checkAnchor();var bytes=256L*1024*1024;var count=0
        Files.walkFileTree(active.toPath(),object:SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir:Path,attrs:BasicFileAttributes):FileVisitResult {require(++count<=250000);return FileVisitResult.CONTINUE}
            override fun visitFile(file:Path,attrs:BasicFileAttributes):FileVisitResult {require(++count<=250000);if(attrs.isRegularFile)bytes=Math.addExact(bytes,attrs.size());return FileVisitResult.CONTINUE}
        });return bytes
    }
    fun prunableBytes(protected:Set<String>,retain:Int=1):Long {
        checkAnchor();require(!journal.exists() && retain in 1..10)
        val j=registry();val keep=protected.toMutableSet()
        val latest=File(active,"opt/appforge-device/generations-v4/latest.json")
        require(latest.canonicalFile.toPath().startsWith(active.canonicalFile.toPath()))
        if(latest.isFile){require(!Files.isSymbolicLink(latest.toPath()) && latest.length()<=4L*1024*1024);keep.add(JSONObject(latest.readText()).getString("previous"))}
        val candidates=j.keys().asSequence().map{child(it)}.filter{it.isDirectory}.sortedByDescending{it.lastModified()}.toList()
        keep.addAll(candidates.take(retain).map{it.name})
        return candidates.filter{it.name !in keep}.sumOf{candidate->
            val files=inventory(candidate,{});requireOwnedSnapshot(candidate,files);require(treeDigest(files)==j.getString(candidate.name)){"Eski snapshot değiştirilmiş; silinebilir alan doğrulanamadı."};files.values.sumOf{it.size}
        }
    }
    fun pruneUnused(protected:Set<String>,retain:Int=1,canceled:()->Unit={}):Long {
        recover();checkAnchor();require(retain in 1..10)
        val j=registry();val keep=protected.toMutableSet()
        for(name in j.keys().asSequence().toList()) {
            val candidate=child(name)
            if(!candidate.exists() && !Files.isSymbolicLink(candidate.toPath())) {
                val obsolete=File(parent,name+".prune.json");require(!Files.isSymbolicLink(obsolete.toPath()));Files.deleteIfExists(obsolete.toPath());j.remove(name)
            }
        }
        saveRegistry(j)
        val latest=File(active,"opt/appforge-device/generations-v4/latest.json")
        require(latest.canonicalFile.toPath().startsWith(active.canonicalFile.toPath()))
        if(latest.isFile){require(!Files.isSymbolicLink(latest.toPath()) && latest.length()<=4L*1024*1024);keep.add(JSONObject(latest.readText()).getString("previous"))}
        val candidates=j.keys().asSequence().map{child(it)}.filter{it.isDirectory}.sortedByDescending{it.lastModified()}.toList()
        keep.addAll(candidates.take(retain).map{it.name})
        var freed=0L
        for(candidate in candidates.filter{it.name !in keep}) {
            canceled();require(!Files.isSymbolicLink(candidate.toPath()))
            val planFile=File(parent,candidate.name+".prune.json");require(!Files.isSymbolicLink(planFile.toPath()))
            val expected:Map<String,Entry>
            if(planFile.exists()) {
                require(planFile.length()<=64L*1024*1024);val plan=JSONObject(planFile.readText());require(plan.optBoolean("ownershipValidated",false)){"Eski temizleme planının sahiplik kanıtı yok; dosyalar korundu."};val entries=plan.getJSONObject("entries")
                expected=entries.keys().asSequence().associateWith{key->val e=entries.getJSONObject(key);Entry(e.getString("kind"),e.getLong("size"),e.getString("digest"),e.getBoolean("executable"),e.getString("permissions"))}
            }else {
                expected=inventory(candidate,canceled)
                requireOwnedSnapshot(candidate,expected)
                require(treeDigest(expected)==j.getString(candidate.name)){"Eski sürüm değişmiş; temizlenmedi."}
                val entries=JSONObject();for((key,e) in expected)entries.put(key,JSONObject().put("kind",e.kind).put("size",e.size).put("digest",e.digest).put("executable",e.executable).put("permissions",e.permissions))
                val bytes=JSONObject().put("ownershipValidated",true).put("entries",entries).toString().toByteArray();require(bytes.size<=64*1024*1024)
                atomicWrite(planFile,bytes)
            }
            require(treeDigest(expected)==j.getString(candidate.name))
            val current=inventory(candidate,canceled)
            require(current.all{(key,value)->
                val old=expected[key]
                old==value || old!=null && old.kind=="directory" && value.kind=="directory" && value.permissions.split(',').toSet()==(old.permissions.split(',').filter{it.isNotEmpty()}.toSet()+setOf("OWNER_WRITE","OWNER_EXECUTE"))
            }){"Eski snapshot içinde yabancı/değişmiş dosya var; temizleme durdu."}
            canceled();freed+=current.values.sumOf{it.size};removeOwned(candidate)
            Files.delete(planFile.toPath());j.remove(candidate.name);saveRegistry(j)
        }
        return freed
    }
    internal data class Entry(val kind:String,val size:Long,val digest:String,val executable:Boolean,val permissions:String)
    private fun treeDigest(map:Map<String,Entry>):String = MessageDigest.getInstance("SHA-256").digest(map.toSortedMap().entries.joinToString("\n"){"${it.key}\t${it.value}"}.toByteArray()).joinToString(""){"%02x".format(it)}
    internal fun inspectRelative(relative:String):Entry? {
        require(relative.isNotEmpty() && !File(relative).isAbsolute && relative.split('/').none{it==".."||it=="."||it.isEmpty()})
        val file=File(active,relative);require(file.parentFile.canonicalFile.toPath().startsWith(active.canonicalFile.toPath()))
        val path=file.toPath();if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS))return null
        if(Files.isSymbolicLink(path))return Entry("link",0,Files.readSymbolicLink(path).toString(),false,"")
        val attributes=Files.readAttributes(path,BasicFileAttributes::class.java,LinkOption.NOFOLLOW_LINKS)
        require(attributes.isDirectory || attributes.isRegularFile)
        return Entry(if(attributes.isDirectory)"directory" else "file",if(attributes.isDirectory)0 else attributes.size(),if(attributes.isDirectory)"" else VerifiedModuleDownload.sha(file),Files.isExecutable(path),Files.getPosixFilePermissions(path,LinkOption.NOFOLLOW_LINKS).map{it.name}.sorted().joinToString(","))
    }
    internal fun snapshot():Map<String,Entry> = inventory(active,{})
    private fun inventory(root:File,canceled:()->Unit):Map<String,Entry> {
        require(root.isDirectory && !Files.isSymbolicLink(root.toPath()))
        val result=sortedMapOf<String,Entry>()
        Files.walkFileTree(root.toPath(),object:SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir:Path,attrs:BasicFileAttributes):FileVisitResult {canceled();require(result.size<250000);result[root.toPath().relativize(dir).toString()]=Entry("directory",0,"",Files.isExecutable(dir),Files.getPosixFilePermissions(dir,LinkOption.NOFOLLOW_LINKS).map{it.name}.sorted().joinToString(","));return FileVisitResult.CONTINUE}
            override fun visitFile(file:Path,attrs:BasicFileAttributes):FileVisitResult {
                canceled();require(result.size<250000)
                val link=Files.isSymbolicLink(file);require(link||attrs.isRegularFile){"Desteklenmeyen özel dosya; aktif ortam korunur."}
                result[root.toPath().relativize(file).toString()]=Entry(if(link)"link" else "file",if(link)0 else attrs.size(),if(link)Files.readSymbolicLink(file).toString() else VerifiedModuleDownload.sha(file.toFile()),if(link)false else Files.isExecutable(file),if(link)"" else Files.getPosixFilePermissions(file,LinkOption.NOFOLLOW_LINKS).map{it.name}.sorted().joinToString(","))
                return FileVisitResult.CONTINUE
            }
        });return result
    }
    private fun copyTree(from:File,to:File,canceled:()->Unit,availableBytes:()->Long=defaultAvailableBytes) {
        require(!to.exists() && !Files.isSymbolicLink(to.toPath()))
        Files.walkFileTree(from.toPath(),object:SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir:Path,attrs:BasicFileAttributes):FileVisitResult {canceled();Files.createDirectory(to.toPath().resolve(from.toPath().relativize(dir)));return FileVisitResult.CONTINUE}
            override fun visitFile(file:Path,attrs:BasicFileAttributes):FileVisitResult {
                canceled();val dst=to.toPath().resolve(from.toPath().relativize(file))
                if(Files.isSymbolicLink(file))Files.createSymbolicLink(dst,Files.readSymbolicLink(file)) else {
                    require(attrs.isRegularFile)
                    require(availableBytes()>attrs.size()+64L*1024*1024){"Kopyalama sırasında depolama alanı tükendi; aktif ortam korundu."}
                    Files.copy(file,dst,StandardCopyOption.COPY_ATTRIBUTES)
                }
                return FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(dir:Path,error:java.io.IOException?):FileVisitResult {
                if(error!=null)throw error
                val dst=to.toPath().resolve(from.toPath().relativize(dir));Files.setPosixFilePermissions(dst,Files.getPosixFilePermissions(dir));return FileVisitResult.CONTINUE
            }
        })
    }
    private fun removeOwned(root:File) {
        if(!root.exists() && !Files.isSymbolicLink(root.toPath()))return
        require(root.parentFile.canonicalFile==parent && root.name.matches(Regex("\\.v3-(stage|previous|discard)-[0-9a-f-]{36}")))
        Files.walkFileTree(root.toPath(),object:SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir:Path,attrs:BasicFileAttributes):FileVisitResult {
                val permissions=Files.getPosixFilePermissions(dir).toMutableSet();permissions.add(java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);permissions.add(java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE);Files.setPosixFilePermissions(dir,permissions);return FileVisitResult.CONTINUE
            }
            override fun visitFile(file:Path,attrs:BasicFileAttributes):FileVisitResult {Files.delete(file);return FileVisitResult.CONTINUE}
            override fun postVisitDirectory(dir:Path,error:java.io.IOException?):FileVisitResult {if(error!=null)throw error;Files.delete(dir);return FileVisitResult.CONTINUE}
        })
    }
}
