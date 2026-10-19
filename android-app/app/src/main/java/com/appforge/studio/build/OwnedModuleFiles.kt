package com.appforge.studio.build

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import org.json.JSONObject

/** Narrow Portable ownership remains exact; unregister via a journaled archive, never partial destructive deletion. */
internal object OwnedModuleFiles {
    private const val DIRECTORY="offline-build-pack/windows-host-v1"
    private val allowed=setOf("AppForge-Windows-Host-v1.exe","windows-host.ready","AppForge-Windows-Host-v1.exe.part")
    private fun root(base:File)=File(base,DIRECTORY).also{require(it.canonicalFile==it.absoluteFile && it.toPath().startsWith(base.canonicalFile.toPath()))}
    private fun control(base:File)=File(base,"offline-build-pack/.portable-lifecycle.json")
    private fun archive(base:File,name:String):File {
        require(name.matches(Regex("[.]portable-removal-[0-9a-f-]{36}")))
        return File(root(base).parentFile,name).also{require(!Files.isSymbolicLink(it.toPath()))}
    }
    private fun validate(directory:File,sha:String) {
        require(!Files.isSymbolicLink(directory.toPath()) && directory.isDirectory)
        val files=directory.listFiles()?.toList()?:error("Modül dizini yok.")
        require(files.all{it.name in allowed && it.isFile && !Files.isSymbolicLink(it.toPath())}){"Sahipliği bilinmeyen dosya var; kaldırma engellendi."}
        val host=File(directory,"AppForge-Windows-Host-v1.exe")
        require(host.isFile && VerifiedModuleDownload.sha(host)==sha){"Host kimliği doğrulanamadı."}
    }
    fun portableRemovalReason(base:File,sha:String):String?=runCatching{validate(root(base),sha);null}.getOrElse{it.message?:"Host doğrulanamadı."}
    private fun write(base:File,j:JSONObject) {
        val file=control(base);require(!Files.isSymbolicLink(file.toPath()))
        val tmp=File(file.path+".tmp");require(!Files.isSymbolicLink(tmp.toPath()))
        FileOutputStream(tmp).use{it.write(j.toString().toByteArray());it.fd.sync()}
        Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
    }
    fun recoverPortable(base:File) {
        val file=control(base);if(!file.exists())return
        require(!Files.isSymbolicLink(file.toPath()) && file.isFile && file.length()<8192)
        val j=JSONObject(file.readText());require(j.getInt("schema")==1)
        val active=root(base);val saved=archive(base,j.getString("archive"))
        if(j.getString("state")!="COMMITTED" && saved.exists()) {
            val host=File(saved,"AppForge-Windows-Host-v1.exe")
            require(saved.isDirectory && !Files.isSymbolicLink(saved.toPath()) && host.isFile && !Files.isSymbolicLink(host.toPath()) && VerifiedModuleDownload.sha(host)==j.getString("sha256"))
            require(!active.exists()){"Aktif host ile kurtarma arşivi birlikte var; ikisi de korundu."}
            Files.move(saved.toPath(),active.toPath(),StandardCopyOption.ATOMIC_MOVE)
        }
        Files.delete(file.toPath())
    }
    fun removePortable(base:File,expectedHostSha:String,beforeDelete:()->Unit={}) {
        recoverPortable(base);val active=root(base);validate(active,expectedHostSha)
        val saved=archive(base,".portable-removal-${UUID.randomUUID()}")
        val j=JSONObject().put("schema",1).put("sha256",expectedHostSha).put("archive",saved.name).put("state","PREPARED")
        write(base,j)
        try {
            Files.move(active.toPath(),saved.toPath(),StandardCopyOption.ATOMIC_MOVE)
            beforeDelete();validate(saved,expectedHostSha)
            // The exact private host is archived. User data and previous bytes are retained.
            j.put("state","COMMITTED");write(base,j);Files.delete(control(base).toPath())
        }catch(e:Throwable){recoverPortable(base);throw e}
    }
}
