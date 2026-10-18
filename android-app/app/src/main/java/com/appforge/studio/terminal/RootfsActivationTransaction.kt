package com.appforge.studio.terminal

import java.io.File
import java.io.FileOutputStream
import java.nio.file.*
import java.util.UUID
import org.json.JSONObject

/** Journaled root+metadata swap. Old roots are retained; failure never recursively deletes user state. */
internal class RootfsActivationTransaction(private val root:File,private val restore:File,private val metadata:File) {
    private val parent=root.parentFile.canonicalFile
    private val journal=File(parent,".rootfs-activation.json")
    private fun checked(file:File) {require(file.absoluteFile==file.canonicalFile && file.parentFile.canonicalFile==parent)}
    private fun backup(name:String):File {
        require(name.matches(Regex("rootfs-before-[0-9a-f-]{36}")))
        require(restore.canonicalFile==restore.absoluteFile && restore.parentFile.canonicalFile==parent)
        return File(restore,name).also{require(!Files.isSymbolicLink(it.toPath()))}
    }
    private fun write(j:JSONObject) {
        checked(journal);val tmp=File(journal.path+".tmp");checked(tmp)
        FileOutputStream(tmp).use{it.write(j.toString().toByteArray());it.fd.sync()}
        Files.move(tmp.toPath(),journal.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
    }
    fun recover() {
        if(!journal.exists())return
        checked(root);checked(metadata);checked(journal)
        require(journal.isFile && journal.length()<=8192)
        val j=JSONObject(journal.readText());require(j.getInt("schema")==1)
        val old=backup(j.getString("old"));val saved=File(restore,old.name+".metadata")
        require(!Files.isSymbolicLink(saved.toPath()) && (!saved.exists() || saved.length()<=32768))
        if(j.getString("state")!="COMMITTED") {
            if(old.exists()) {
                if(root.exists())Files.move(root.toPath(),File(restore,"failed-rootfs-${UUID.randomUUID()}").toPath(),StandardCopyOption.ATOMIC_MOVE)
                Files.move(old.toPath(),root.toPath(),StandardCopyOption.ATOMIC_MOVE)
            } else if(!j.getBoolean("hadRoot") && root.exists())
                Files.move(root.toPath(),File(restore,"failed-rootfs-${UUID.randomUUID()}").toPath(),StandardCopyOption.ATOMIC_MOVE)
            if(saved.isFile)Files.copy(saved.toPath(),metadata.toPath(),StandardCopyOption.REPLACE_EXISTING)
            else if(!j.getBoolean("hadMetadata"))Files.deleteIfExists(metadata.toPath())
        }
        Files.deleteIfExists(journal.toPath())
    }
    fun activate(staged:File,writeMetadata:()->Unit,fault:(String)->Unit={}) {
        recover();checked(root);checked(metadata)
        require(staged.isDirectory && staged.absoluteFile==staged.canonicalFile)
        require(staged.toPath().startsWith(File(parent,"staging").toPath()))
        val old=backup("rootfs-before-${UUID.randomUUID()}");restore.mkdirs()
        val saved=File(restore,old.name+".metadata")
        require(!metadata.exists() || metadata.isFile && metadata.length()<=32768)
        if(metadata.exists())Files.copy(metadata.toPath(),saved.toPath())
        val j=JSONObject().put("schema",1).put("old",old.name).put("hadRoot",root.exists()).put("hadMetadata",metadata.exists()).put("state","PREPARED")
        write(j)
        try {
            fault("before-switch")
            if(root.exists())Files.move(root.toPath(),old.toPath(),StandardCopyOption.ATOMIC_MOVE)
            fault("old-moved")
            Files.move(staged.toPath(),root.toPath(),StandardCopyOption.ATOMIC_MOVE)
            fault("new-moved")
            writeMetadata();fault("metadata-written")
            j.put("state","COMMITTED");write(j)
            Files.deleteIfExists(journal.toPath())
        }catch(e:Throwable){recover();throw e}
    }
}
