package com.appforge.studio.build
import java.io.File
import java.io.FileOutputStream
import java.nio.file.*
import java.util.UUID
import org.json.JSONObject

/** Unregister a private runtime atomically; never recursively delete possibly user-added files. */
internal class RuntimeArchiveRemoval(private val active:File) {
    private val parent=active.parentFile.canonicalFile
    private val journal=File(parent,".runtime-removal-v5.json")
    private val receipt=File(parent,".runtime-archive-v5.json")
    private fun archive(name:String):File {
        require(name.matches(Regex("runtime-v3-archived-[0-9a-f-]{36}")))
        return File(parent,name).also{require(!Files.isSymbolicLink(it.toPath()))}
    }
    private fun write(file:File,value:JSONObject) {
        parent.mkdirs();require(!Files.isSymbolicLink(file.toPath()))
        val tmp=File(file.path+".tmp");require(!Files.isSymbolicLink(tmp.toPath()))
        FileOutputStream(tmp).use{it.write(value.toString().toByteArray());it.fd.sync()}
        Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
    }
    fun recover() {
        if(!journal.exists())return
        require(journal.isFile && journal.length()<8192 && !Files.isSymbolicLink(journal.toPath()))
        val j=JSONObject(journal.readText());val backup=archive(j.getString("archive"))
        require(active.absoluteFile==active.canonicalFile && active.parentFile.canonicalFile==parent && active.name=="runtime-v3")
        if(j.getString("state")!="COMMITTED" && backup.exists() && !active.exists())Files.move(backup.toPath(),active.toPath(),StandardCopyOption.ATOMIC_MOVE)
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
        require(receipt.isFile && receipt.length()<8192 && !Files.isSymbolicLink(receipt.toPath()))
        archive(JSONObject(receipt.readText()).getString("archive")).takeIf{it.isDirectory}
    }.getOrNull()
}
