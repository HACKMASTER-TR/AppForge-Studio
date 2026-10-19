package com.appforge.studio.build
import java.io.File
import java.io.FileOutputStream
import java.nio.file.*
import java.util.UUID
import org.json.JSONObject

/** Unregister a private runtime atomically; never recursively delete possibly user-added files. */
internal class RuntimeArchiveRemoval(private val active:File,private val availableBytes:(File)->Long={it.usableSpace}) {
    private val parent=active.parentFile.canonicalFile
    private val journal=File(parent,".runtime-removal-v5.json")
    private val receipt=File(parent,".runtime-archive-v5.json")
    private fun safe(path:File){var p:File?=path.absoluteFile;while(p!=null){require(!Files.isSymbolicLink(p.toPath())){"Runtime kaldırma yolu symlink olamaz"};p=p.parentFile}}
    private fun archive(name:String):File {
        require(name.matches(Regex("runtime-v3-archived-[0-9a-f-]{36}")))
        return File(parent,name).also(::safe)
    }
    private fun write(file:File,value:JSONObject) {
        parent.mkdirs();safe(file)
        val bytes=value.put("schema",1).toString().toByteArray(Charsets.UTF_8);require(bytes.size<8192)
        require(availableBytes(parent)>bytes.size+65536L){"Runtime kaldırma günlüğü için depolama alanı yetersiz."}
        val tmp=File(file.path+".tmp");safe(tmp)
        try {
            FileOutputStream(tmp).use{it.write(bytes);it.fd.sync()}
            Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
        } catch(e:Throwable) {
            if(!Files.isSymbolicLink(tmp.toPath()))Files.deleteIfExists(tmp.toPath())
            throw e
        }
    }
    fun recover() {
        val staleTmp=File(journal.path+".tmp");require(!Files.isSymbolicLink(staleTmp.toPath()));Files.deleteIfExists(staleTmp.toPath())
        if(!journal.exists())return
        safe(journal);require(journal.isFile && journal.length() in 1..8191)
        val j=JSONObject(journal.readText());require(j.optInt("schema",1)==1){"Tanınmayan runtime kaldırma günlüğü şeması"}
        val backup=archive(j.getString("archive"))
        require(active.absoluteFile==active.canonicalFile && active.parentFile.canonicalFile==parent && active.name=="runtime-v3")
        if(j.getString("state")!="COMMITTED") {
            if(backup.exists()) {
                require(!active.exists()){"Aktif runtime ile kurtarma arşivi birlikte var; ikisi de korundu."}
                Files.move(backup.toPath(),active.toPath(),StandardCopyOption.ATOMIC_MOVE)
            }
        } else if(backup.isDirectory && !receipt.isFile) {
            write(receipt,JSONObject().put("archive",backup.name))
        }
        Files.delete(journal.toPath())
    }
    fun remove(fault:()->Unit={}) {
        recover();require(active.name=="runtime-v3" && active.isDirectory && active.absoluteFile==active.canonicalFile)
        val target=archive("runtime-v3-archived-${UUID.randomUUID()}")
        val j=JSONObject().put("archive",target.name).put("state","PREPARED")
        write(journal,j)
        try {
            Files.move(active.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE);fault()
            write(receipt,JSONObject().put("archive",target.name))
            j.put("state","COMMITTED");write(journal,j);Files.delete(journal.toPath())
        }catch(e:Throwable){recover();throw e}
    }
    fun archivedDirectory():File?=runCatching {
        safe(receipt);require(receipt.isFile && receipt.length()<8192)
        val j=JSONObject(receipt.readText());require(j.optInt("schema",1)==1)
        archive(j.getString("archive")).takeIf{it.isDirectory}
    }.getOrNull()
}
