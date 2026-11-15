package com.appforge.studio.build

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** FICLONE is optional. JNI absence, unsupported fs/ioctl or failed isolation => independent copy. */
internal object RuntimeFileClone {
    private val nativeAvailable=runCatching{System.loadLibrary("appforge_pty");true}.getOrDefault(false)
    private external fun cloneFile(source:String,target:String):Boolean
    private external fun allocated(path:String):Long
    private external fun syncPath(path:String,directory:Boolean):Boolean
    private fun tryClone(source:String,target:String):Boolean = nativeAvailable &&
        try {cloneFile(source,target)} catch(_:LinkageError){false}
    fun sync(file:File,directory:Boolean=false) {
        val result=if(nativeAvailable)try{syncPath(file.path,directory)}catch(_:LinkageError){null} else null
        if(result!=null)check(result){"Kalıcı filesystem kaydı yazılamadı; işlem durdu."}
        else java.nio.channels.FileChannel.open(file.toPath(),java.nio.file.StandardOpenOption.READ).use{it.force(true)}
    }
    fun allocatedBytes(path:String):Long=if(nativeAvailable)runCatching{allocated(path)}.getOrDefault(-1) else -1
    fun supported(parent:File):Boolean {
        if(!nativeAvailable)return false
        val probe=Files.createTempDirectory(parent.toPath(),".clone-probe-").toFile()
        val a=File(probe,"source");val b=File(probe,"clone")
        try {
            a.writeText("0123456789")
            if(!tryClone(a.path,b.path) || Files.isSameFile(a.toPath(),b.toPath()))return false
            a.writeText("source-edited")
            if(b.readText()!="0123456789")return false
            b.writeText("clone-edited")
            return a.readText()=="source-edited"
        } catch(_:Exception){return false}
        finally {Files.deleteIfExists(b.toPath());Files.deleteIfExists(a.toPath());Files.delete(probe.toPath())}
    }
    fun copy(source:java.nio.file.Path,target:java.nio.file.Path,reflink:Boolean) {
        require(!Files.isSymbolicLink(source) && !Files.exists(target,java.nio.file.LinkOption.NOFOLLOW_LINKS))
        if(reflink && tryClone(source.toString(),target.toString())) {
            check(!Files.isSameFile(source,target)){"Clone hardlink alias; activation denied"}
            Files.setPosixFilePermissions(target,Files.getPosixFilePermissions(source))
            Files.setLastModifiedTime(target,Files.getLastModifiedTime(source))
        } else Files.copy(source,target,StandardCopyOption.COPY_ATTRIBUTES)
        sync(target.toFile())
    }
}
