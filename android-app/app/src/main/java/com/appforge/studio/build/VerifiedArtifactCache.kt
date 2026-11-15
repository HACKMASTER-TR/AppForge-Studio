package com.appforge.studio.build

import java.io.File
import java.nio.file.Files

/** Trust stays with the caller's pinned manifest. No network, deletion or READY transitions. */
internal object VerifiedArtifactCache {
    @Volatile private var roots:List<File> = emptyList()
    fun configure(privateRoot:File,runtimeParent:File?) {
        val safe=RecoveryPathAnchor.androidPlatformPath(privateRoot);RecoveryPathAnchor.check(safe)
        roots=listOf(File(safe,"v9-signed-r2-canary"))+listOfNotNull(runtimeParent?.let{File(it,".module-archive-cache-v5")})
    }
    fun reuse(target:File,sha256:String,expectedBytes:Long?,maxBytes:Long):Boolean {
        if(matches(target,sha256,expectedBytes,maxBytes))return true
        if(target.exists())return false // Never replace damaged/unowned content implicitly.
        val source=roots.asSequence().flatMap{sequenceOf(File(it,sha256),File(it,"$sha256.archive"))}
            .filter{it.absoluteFile!=target.absoluteFile}.firstOrNull{matches(it,sha256,expectedBytes,maxBytes)}?:return false
        RecoveryPathAnchor.check(target)
        val parent=requireNotNull(target.absoluteFile.parentFile)
        parent.mkdirs()
        require(parent.usableSpace>source.length()+64L*1024*1024)
        val temp=File(parent,".cache-import-${java.util.UUID.randomUUID()}")
        try {
            Files.copy(source.toPath(),temp.toPath())
            require(matches(temp,sha256,expectedBytes,maxBytes))
            java.nio.channels.FileChannel.open(temp.toPath(),java.nio.file.StandardOpenOption.WRITE).use{it.force(true)}
            Files.move(temp.toPath(),target.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        } finally {Files.deleteIfExists(temp.toPath())}
        return true
    }
    fun matches(file:File,sha256:String,expectedBytes:Long?=null,maxBytes:Long=Long.MAX_VALUE):Boolean {
        require(sha256.matches(Regex("[a-f0-9]{64}")))
        require(expectedBytes==null || expectedBytes>0)
        RecoveryPathAnchor.check(file)
        require(!Files.isSymbolicLink(file.toPath()))
        return file.isFile && file.length() in 1..maxBytes &&
            (expectedBytes==null || file.length()==expectedBytes) && VerifiedModuleDownload.sha(file)==sha256
    }
}
