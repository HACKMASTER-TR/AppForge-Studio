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

    /**
     * Called ONLY on a newly installed and verified vendor rootfs.
     * Never retroactively admit a runtime with module receipts.
     * This marker is origin provenance, NOT signed APT metadata.
     */
    internal fun captureFreshRoot(
        root: File,
        architecture: String,
        archiveSha256: String
    ) {
        require(architecture in setOf("arm64", "amd64"))
        require(archiveSha256.matches(Regex("[a-f0-9]{64}")))
        RecoveryPathAnchor.check(root)

        val vendor = File(
            root,
            "opt/appforge-device/apt-locks-v5/LINUX_RUNTIME/vendor-v1.json"
        )
        require(vendor.isFile &&
            !Files.isSymbolicLink(vendor.toPath()) &&
            vendor.length() in 1..65536)

        val record = org.json.JSONObject(vendor.readText())
        require(record.getInt("schema") == 1)
        require(record.getString("release") == "24.04.4")
        require(record.getString("architecture") == architecture)
        require(record.getString("rootfsSha256") == archiveSha256)

        val status = File(root, "var/lib/dpkg/status")
        require(status.isFile && !Files.isSymbolicLink(status.toPath()))
        require(record.getString("statusSha256") ==
            VerifiedModuleDownload.sha(status))

        val installed = AptModuleLocks.installedVersions(status.readText())
        val packages = record.getJSONObject("packages")
        require(installed.isNotEmpty() && packages.length() == installed.size)

        installed.forEach { (name, version) ->
            require(packages.getString(name) ==
                "${version.first} (${version.second})")
        }

        val owner = File(root, BaseRuntimeOwnership.FILE)
        require(owner.isFile && !Files.isSymbolicLink(owner.toPath()))
        require(BaseRuntimeOwnership.paths(root).isNotEmpty())

        val receipts = File(root, "opt/appforge-device/modules-v3")
        require(receipts.listFiles().orEmpty().none {
            it.extension == "json"
        }) { "Legacy module receipts prohibit fresh-origin capture" }

        val store = File(
            root,
            "opt/appforge-device/apt-snapshot-v1/" +
                "$architecture-${AptModuleLocks.SNAPSHOT}"
        )

        RecoveryPathAnchor.check(store)
        require(!store.exists()) {
            "Existing APT store cannot be retroactively trusted"
        }
        require(store.mkdirs())
        RecoveryPathAnchor.check(store)

        val marker = File(store, "managed-origin")
        val temporary = File(store, "managed-origin.part")

        require(!marker.exists() && !temporary.exists())
        require(!Files.isSymbolicLink(marker.toPath()))
        require(!Files.isSymbolicLink(temporary.toPath()))

        java.io.FileOutputStream(temporary).use {
            it.write("fresh-managed-snapshot-v1\n".toByteArray())
            it.fd.sync()
        }
        Files.move(
            temporary.toPath(),
            marker.toPath(),
            java.nio.file.StandardCopyOption.ATOMIC_MOVE
        )

        check(marker.readText().trim() == "fresh-managed-snapshot-v1")
        check(blockedReason(root) == null)
    }

}
