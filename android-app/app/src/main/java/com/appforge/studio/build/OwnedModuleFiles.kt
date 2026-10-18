package com.appforge.studio.build

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Narrow ownership boundary: only the pinned Portable host's private directory. No recursive delete. */
internal object OwnedModuleFiles {
    private const val DIRECTORY="offline-build-pack/windows-host-v1"
    private val allowed=setOf("AppForge-Windows-Host-v1.exe","windows-host.ready","AppForge-Windows-Host-v1.exe.part")
    fun removePortable(base:File,expectedHostSha:String,beforeDelete:()->Unit={}) {
        val root=File(base,DIRECTORY)
        require(root.canonicalFile==root.absoluteFile && root.toPath().startsWith(base.canonicalFile.toPath()))
        val files=root.listFiles()?.toList()?:error("Modül dizini yok.")
        require(files.all{it.name in allowed && it.isFile && !Files.isSymbolicLink(it.toPath())}){"Sahipliği bilinmeyen dosya var; hiçbir dosya silinmedi."}
        val host=File(root,"AppForge-Windows-Host-v1.exe")
        require(host.isFile && VerifiedModuleDownload.sha(host)==expectedHostSha){"Host kimliği doğrulanamadı; kaldırma engellendi."}
        val quarantine=File(root.parentFile,".portable-removal-${UUID.randomUUID()}")
        Files.move(root.toPath(),quarantine.toPath(),StandardCopyOption.ATOMIC_MOVE)
        try {beforeDelete()} catch(e:Throwable) {
            Files.move(quarantine.toPath(),root.toPath(),StandardCopyOption.ATOMIC_MOVE);throw e
        }
        // Only the exact allowlisted inventory is removed; unknown files and shared modules never enter here.
        files.forEach {require(File(quarantine,it.name).delete()){"Silme tamamlanamadı; özel karantina korundu."}}
        check(quarantine.delete()){"Özel karantina dizini korundu."}
    }
}
