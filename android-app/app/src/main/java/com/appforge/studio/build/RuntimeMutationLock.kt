package com.appforge.studio.build

import java.io.Closeable
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.nio.file.Files

/** FileChannel locks survive coroutine thread switches and are released by process death. */
internal object RuntimeMutationLock {
    class Lease internal constructor(private val parent:File,private val lock:java.nio.channels.FileLock,private val channel:FileChannel):Closeable {
        private var closed=false
        fun borrow(expected:File):Closeable {check(!closed && expected.absoluteFile==parent.absoluteFile);return Closeable{}}
        @Synchronized override fun close(){if(!closed){closed=true;try{lock.release()}finally{channel.close()}}}
    }
    fun acquire(parent:File,name:String=".runtime-mutation.lock"):Lease {
        RecoveryPathAnchor.check(parent)
        if(!parent.exists())require(parent.mkdirs())
        RecoveryPathAnchor.check(parent)
        require(name==".runtime-mutation.lock" || name.matches(Regex("\\.artifact-[a-f0-9]{64}\\.lock")))
        val path=File(parent,name).toPath()
        require(!Files.isSymbolicLink(path))
        val channel=FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.WRITE,java.nio.file.LinkOption.NOFOLLOW_LINKS)
        try {
            val lock=channel.tryLock()?:error("Başka süreç runtime işlemi yürütüyor; çalışan ortam korundu.")
            return Lease(parent,lock,channel)
        }catch(e:Throwable){channel.close();if(e is java.nio.channels.OverlappingFileLockException)throw IllegalArgumentException("Başka runtime işlemi var; işlem başlamadı.",e);throw e}
    }
}
