package com.appforge.studio.build
import java.io.File
import java.nio.file.Files
internal object AptSnapshotMigration {
    fun blockedReason(root:File):String? {
        val receipts=File(root,"opt/appforge-device/modules-v3")
        if(receipts.listFiles().orEmpty().none{it.extension=="json"})return null
        val parent=File(root,"opt/appforge-device/apt-snapshot-v1")
        if(parent.absoluteFile!=parent.canonicalFile || receipts.absoluteFile!=receipts.canonicalFile)return "APT migration sahiplik yolu güvenli değil."
        val stores=parent.listFiles().orEmpty().filter{it.name in setOf("amd64-${AptModuleLocks.SNAPSHOT}","arm64-${AptModuleLocks.SNAPSHOT}") && it.isDirectory && !Files.isSymbolicLink(it.toPath())}
        if(stores.any{store-> val proof=File(store,"managed-origin");proof.isFile && proof.length()<=1024 && proof.canonicalFile==proof.absoluteFile && proof.readText().trim()=="fresh-managed-snapshot-v1"})return null
        return "Eski ortak APT ortamının snapshot migration güvenliği kanıtlanmadı. Mevcut generation korunur; otomatik güncelleme kapalı."
    }
}
