package com.appforge.studio.build

import java.io.File
import java.io.FileOutputStream
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONObject

/** Private build-root generations only. Never follows links while copying/deleting. Original projects stay outside. */
internal class RuntimeGenerationStore(private val active:File) {
    private val parent=requireNotNull(active.parentFile).canonicalFile
    private val journal=File(parent,".module-generation.json")
    private fun checkAnchor(){require(active.absoluteFile==active.canonicalFile && active.parentFile.canonicalFile==parent && active.isDirectory)}
    private fun child(name:String):File {require(name.matches(Regex("\\.v3-(stage|previous|discard)-[0-9a-f-]{36}")));return File(parent,name)}
    private fun write(value:JSONObject) {
        require(!Files.isSymbolicLink(journal.toPath()))
        val tmp=File(journal.path+".tmp");require(!Files.isSymbolicLink(tmp.toPath()))
        FileOutputStream(tmp).use{it.write(value.toString().toByteArray());it.fd.sync()}
        Files.move(tmp.toPath(),journal.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
    }
    fun recover() {
        if(!journal.exists())return
        require(journal.isFile && !Files.isSymbolicLink(journal.toPath()) && journal.length()<8192)
        val j=JSONObject(journal.readText());require(j.getString("active")==active.name)
        val stage=child(j.getString("stage"));val old=child(j.getString("previous"))
        if(j.getString("state")!="COMMITTED" && old.exists()) {
            val failed=child(".v3-discard-${UUID.randomUUID()}")
            if(active.exists())Files.move(active.toPath(),failed.toPath(),StandardCopyOption.ATOMIC_MOVE)
            Files.move(old.toPath(),active.toPath(),StandardCopyOption.ATOMIC_MOVE)
            removeOwned(failed)
        }
        if(j.getString("state")!="COMMITTED")removeOwned(stage)
        journal.delete()
    }
    fun prepare(module:OfflineModuleId,availableBytes:()->Long={parent.usableSpace},canceled:()->Unit={}):Prepared=prepareOwner(module.name,availableBytes,canceled)
    fun prepareOwner(owner:String,availableBytes:()->Long={parent.usableSpace},canceled:()->Unit={}):Prepared {
        require(owner.matches(Regex("[A-Z0-9_:-]{1,100}")))
        recover();checkAnchor()
        val before=inventory(active,canceled)
        val bytes=before.values.sumOf{it.size}
        require(availableBytes()>bytes+256L*1024*1024){"İzole sürüm için yeterli depolama yok; çalışan modüller değişmedi."}
        val id=UUID.randomUUID().toString();val stage=child(".v3-stage-$id");val old=child(".v3-previous-$id")
        val j=JSONObject().put("active",active.name).put("stage",stage.name).put("previous",old.name).put("module",owner).put("state","COPYING")
        write(j)
        try {copyTree(active,stage,canceled)}catch(e:Throwable){removeOwned(stage);journal.delete();throw e}
        check(inventory(stage,canceled)==before){"Sürüm kopyası bütünlüğü doğrulanamadı."}
        return Prepared(stage,old,j,before,canceled)
    }
    inner class Prepared internal constructor(val root:File,private val old:File,private val j:JSONObject,private val before:Map<String,Entry>,private val canceled:()->Unit):AutoCloseable {
        private var committed=false
        fun commit(verify:(File)->Unit,fault:(String)->Unit={}) {
            canceled();verify(root)
            val after=inventory(root,canceled)
            val changed=(before.keys+after.keys).filter{before[it]!=after[it]}.sorted()
            val ownership=File(root,"opt/appforge-device/generations-v4")
            require(ownership.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()))
            ownership.mkdirs()
            val latest=File(ownership,"latest.json");require(!Files.isSymbolicLink(latest.toPath()))
            latest.writeText(JSONObject().put("module",j.getString("module")).put("previous",old.name)
                .put("previousDigest",treeDigest(before)).put("changed",org.json.JSONArray(changed)).put("schema",1).toString())
            j.put("state","STAGED");write(j);fault("before-switch")
            Files.move(active.toPath(),old.toPath(),StandardCopyOption.ATOMIC_MOVE)
            try {
                fault("old-moved")
                Files.move(root.toPath(),active.toPath(),StandardCopyOption.ATOMIC_MOVE)
                fault("new-moved")
                j.put("state","COMMITTED");write(j);committed=true;journal.delete()
            }catch(e:Throwable){recover();throw e}
        }
        override fun close(){if(!committed){recover();removeOwned(root)}}
    }
    fun rollbackAvailable(module:OfflineModuleId):Boolean=runCatching {
        checkAnchor();val f=File(active,"opt/appforge-device/generations-v4/latest.json")
        require(!Files.isSymbolicLink(f.toPath()) && f.canonicalFile.toPath().startsWith(active.canonicalFile.toPath()))
        if(!f.isFile || f.length()>4L*1024*1024)return@runCatching false
        val j=JSONObject(f.readText());j.getString("module")==module.name && child(j.getString("previous")).isDirectory
    }.getOrDefault(false)
    fun rollback(module:OfflineModuleId,canceled:()->Unit={}) {
        recover();checkAnchor()
        val f=File(active,"opt/appforge-device/generations-v4/latest.json");require(f.length()<=4L*1024*1024)
        val record=JSONObject(f.readText());require(record.getString("module")==module.name){"Yalnız son izole modül işlemi geri alınabilir."}
        val old=child(record.getString("previous"))
        require(treeDigest(inventory(old,canceled))==record.getString("previousDigest")){"Önceki sürüm bütünlüğü bozuk; geri alma yapılmadı."}
        val stage=child(".v3-stage-${UUID.randomUUID()}")
        val j=JSONObject().put("active",active.name).put("stage",stage.name).put("previous",old.name).put("state","STAGED").put("module",module.name)
        // A separate swap journal restores the current generation if the reverse activation fails.
        val current=child(".v3-previous-${UUID.randomUUID()}")
        j.put("previous",current.name).put("state","COPYING");write(j)
        try{copyTree(old,stage,canceled)}catch(e:Throwable){recover();throw e}
        j.put("state","STAGED");write(j)
        Files.move(active.toPath(),current.toPath(),StandardCopyOption.ATOMIC_MOVE)
        try {Files.move(stage.toPath(),active.toPath(),StandardCopyOption.ATOMIC_MOVE);j.put("state","COMMITTED");write(j);journal.delete()}
        catch(e:Throwable){recover();throw e}
    }
    internal data class Entry(val kind:String,val size:Long,val digest:String,val executable:Boolean,val permissions:String)
    private fun treeDigest(map:Map<String,Entry>):String = MessageDigest.getInstance("SHA-256").digest(map.toSortedMap().entries.joinToString("\n"){"${it.key}\t${it.value}"}.toByteArray()).joinToString(""){"%02x".format(it)}
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
    private fun copyTree(from:File,to:File,canceled:()->Unit) {
        require(!to.exists() && !Files.isSymbolicLink(to.toPath()))
        Files.walkFileTree(from.toPath(),object:SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir:Path,attrs:BasicFileAttributes):FileVisitResult {canceled();Files.createDirectory(to.toPath().resolve(from.toPath().relativize(dir)));return FileVisitResult.CONTINUE}
            override fun visitFile(file:Path,attrs:BasicFileAttributes):FileVisitResult {
                canceled();val dst=to.toPath().resolve(from.toPath().relativize(file))
                if(Files.isSymbolicLink(file))Files.createSymbolicLink(dst,Files.readSymbolicLink(file)) else {require(attrs.isRegularFile);Files.copy(file,dst,StandardCopyOption.COPY_ATTRIBUTES)}
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
