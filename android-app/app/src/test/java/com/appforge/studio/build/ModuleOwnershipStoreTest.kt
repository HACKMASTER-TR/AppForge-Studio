package com.appforge.studio.build

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ModuleOwnershipStoreTest {
    private fun install(root:File,id:OfflineModuleId,action:(File)->Unit) {
        if(!File(root,BaseRuntimeOwnership.FILE).exists())BaseRuntimeOwnership.captureFresh(root)
        RuntimeGenerationStore(root).prepare(id).use{tx->
            action(tx.root)
            ModuleOwnershipStore(tx.root).record(id,tx.previousRootName,RuntimeGenerationStore(root).snapshot(),RuntimeGenerationStore(tx.root).snapshot())
            tx.commit(verify={})
        }
    }
    @Test fun outOfOrderRemovalPreservesIndependentModuleAndUserFiles() {
        val base=Files.createTempDirectory("owned-delta").toFile()
        try {
            val root=File(base,"rootfs").apply{mkdir()};File(root,"foreign").writeText("keep")
            install(root,OfflineModuleId.NODE_WEB){File(it,"node").writeText("node")}
            install(root,OfflineModuleId.WINDOWS_NATIVE){File(it,"compiler").writeText("compiler")}
            assertNull(ModuleOwnershipStore(root).removalReason(OfflineModuleId.NODE_WEB))
            RuntimeGenerationStore(root).prepareOwner("REMOVE:NODE_WEB").use{tx->ModuleOwnershipStore(root).restoreInto(OfflineModuleId.NODE_WEB,tx.root);tx.commit(verify={})}
            assertFalse(File(root,"node").exists());assertEquals("compiler",File(root,"compiler").readText());assertEquals("keep",File(root,"foreign").readText())
        }finally{base.deleteRecursively()}
    }
    @Test fun sharedMetadataAndForeignChildrenBlockDestructiveRemoval() {
        val base=Files.createTempDirectory("owned-shared").toFile()
        try {
            val root=File(base,"rootfs").apply{mkdir()};File(root,"dpkg-status").writeText("base")
            install(root,OfflineModuleId.ANDROID_CORE){File(it,"dpkg-status").writeText("core");File(it,"owned-dir").mkdir();File(it,"owned-dir/core").writeText("core")}
            install(root,OfflineModuleId.PYTHON_ANDROID){File(it,"dpkg-status").writeText("python")}
            assertNotNull(ModuleOwnershipStore(root).removalReason(OfflineModuleId.ANDROID_CORE))
            File(root,"owned-dir/user-source").writeText("preserve")
            assertThrows(IllegalArgumentException::class.java){RuntimeGenerationStore(root).prepareOwner("REMOVE:ANDROID_CORE").use{ModuleOwnershipStore(root).restoreInto(OfflineModuleId.ANDROID_CORE,it.root)}}
            assertEquals("preserve",File(root,"owned-dir/user-source").readText());assertEquals("python",File(root,"dpkg-status").readText())
        }finally{base.deleteRecursively()}
    }
    @Test fun updateKeepsOriginalBaselineAndTamperedSnapshotIsRejected() {
        val base=Files.createTempDirectory("owned-update").toFile()
        try {
            val root=File(base,"rootfs").apply{mkdir()};File(root,"node").writeText("base")
            install(root,OfflineModuleId.NODE_WEB){File(it,"node").writeText("v1")}
            install(root,OfflineModuleId.NODE_WEB){File(it,"node").writeText("v2")}
            assertNull(ModuleOwnershipStore(root).removalReason(OfflineModuleId.NODE_WEB))
            val original=base.listFiles()!!.filter{it.name.startsWith(".v3-previous-")}.single{File(it,"node").readText()=="base"}
            File(original,"node").writeText("tampered")
            assertNotNull(ModuleOwnershipStore(root).removalReason(OfflineModuleId.NODE_WEB));assertEquals("v2",File(root,"node").readText())
        }finally{base.deleteRecursively()}
    }
    @Test fun contentAddressedBaselineSurvivesVerifiedSnapshotPruning() {
        val base=Files.createTempDirectory("owned-cas-prune").toFile()
        try {
            val root=File(base,"rootfs").apply{mkdir()};File(root,"node").writeText("original")
            BaseRuntimeOwnership.captureFresh(root)
            repeat(3){n->RuntimeGenerationStore(root).prepare(OfflineModuleId.NODE_WEB).use{tx->
                File(tx.root,"node").writeText("v$n")
                ModuleOwnershipStore(tx.root).record(OfflineModuleId.NODE_WEB,tx.previousRootName,RuntimeGenerationStore(root).snapshot(),RuntimeGenerationStore(tx.root).snapshot(),root)
                tx.commit(verify={})
            }}
            assertTrue(RuntimeGenerationStore(root).pruneUnused(ModuleOwnershipStore(root).protectedSnapshots())>0)
            assertEquals(1,base.listFiles()!!.count{it.name.startsWith(".v3-previous-") && it.isDirectory})
            assertNull(ModuleOwnershipStore(root).removalReason(OfflineModuleId.NODE_WEB))
            RuntimeGenerationStore(root).prepareOwner("REMOVE:NODE_WEB").use{tx->ModuleOwnershipStore(root).restoreInto(OfflineModuleId.NODE_WEB,tx.root);tx.commit(verify={})}
            assertEquals("original",File(root,"node").readText())
        }finally{base.deleteRecursively()}
    }
    @Test fun changedSnapshotAndCorruptContentStoreCannotBeDeletedOrRestored() {
        val base=Files.createTempDirectory("owned-cas-fault").toFile()
        try {
            val root=File(base,"rootfs").apply{mkdir()};File(root,"node").writeText("original")
            BaseRuntimeOwnership.captureFresh(root)
            repeat(3){n->RuntimeGenerationStore(root).prepare(OfflineModuleId.NODE_WEB).use{tx->
                File(tx.root,"node").writeText("v$n")
                ModuleOwnershipStore(tx.root).record(OfflineModuleId.NODE_WEB,tx.previousRootName,RuntimeGenerationStore(root).snapshot(),RuntimeGenerationStore(tx.root).snapshot(),root);tx.commit(verify={})
            }}
            val old=base.listFiles()!!.filter{it.name.startsWith(".v3-previous-") && it.isDirectory}.minBy{it.lastModified()}
            val timestamp=old.lastModified()
            File(old,"foreign-user-file").writeText("preserve");assertTrue(old.setLastModified(timestamp))
            assertThrows(IllegalArgumentException::class.java){RuntimeGenerationStore(root).pruneUnused(emptySet())}
            assertEquals("preserve",File(old,"foreign-user-file").readText());assertEquals("v2",File(root,"node").readText())
            File(base,".module-ownership-cas-v5").listFiles()!!.filter{it.isFile}.first().writeText("corrupt")
            assertNotNull(ModuleOwnershipStore(root).removalReason(OfflineModuleId.NODE_WEB))
        }finally{base.deleteRecursively()}
    }

    @Test fun installerCannotDeleteUnownedDataEvenWhenRunningInAnIsolatedStage() {
        val base=Files.createTempDirectory("owned-foreign-update").toFile()
        try {
            val root=File(base,"rootfs").apply{mkdir()};File(root,"vendor").writeText("base")
            BaseRuntimeOwnership.captureFresh(root)
            File(root,"user-project.txt").writeText("private source")
            assertThrows(IllegalArgumentException::class.java){RuntimeGenerationStore(root).prepare(OfflineModuleId.NODE_WEB).use{tx->
                File(tx.root,"user-project.txt").delete()
                ModuleOwnershipStore(tx.root).record(OfflineModuleId.NODE_WEB,tx.previousRootName,RuntimeGenerationStore(root).snapshot(),RuntimeGenerationStore(tx.root).snapshot(),root)
            }}
            assertEquals("private source",File(root,"user-project.txt").readText())
        }finally{base.deleteRecursively()}
    }

    @Test fun readinessChecksSharedFilesAgainstTheLatestAuthorizedOwner() {
        val base=Files.createTempDirectory("owned-shared-ready").toFile()
        try {
            val root=File(base,"rootfs").apply{mkdir()}
            install(root,OfflineModuleId.ANDROID_CORE){r->
                File(r,"opt/appforge-device/jdk-17/bin/java").apply{parentFile.mkdirs();writeText("jdk")}
                File(r,"usr/lib/shared.so").apply{parentFile.mkdirs();writeText("v1")}
                File(r,"root/.gradle-appforge/caches/volatile.lock").apply{parentFile.mkdirs();writeText("cache")}
            }
            assertTrue(ModuleOwnershipStore(root).verifyNamespace(OfflineModuleId.ANDROID_CORE))
            install(root,OfflineModuleId.NODE_WEB){r->
                File(r,"opt/appforge-device/node-22.23.3/bin/node").apply{parentFile.mkdirs();writeText("node")}
                File(r,"usr/lib/shared.so").writeText("v2")
            }
            assertTrue(ModuleOwnershipStore(root).verifyNamespace(OfflineModuleId.ANDROID_CORE))
            assertTrue(ModuleOwnershipStore(root).verifyNamespace(OfflineModuleId.NODE_WEB))
            File(root,"root/.gradle-appforge/caches/volatile.lock").writeText("legitimate cache update")
            assertTrue(ModuleOwnershipStore(root).verifyNamespace(OfflineModuleId.ANDROID_CORE))
            File(root,"usr/lib/shared.so").writeText("foreign")
            assertFalse(ModuleOwnershipStore(root).verifyNamespace(OfflineModuleId.ANDROID_CORE))
            assertFalse(ModuleOwnershipStore(root).verifyNamespace(OfflineModuleId.NODE_WEB))
        }finally{base.deleteRecursively()}
    }
    @Test fun unownedExtraPrivateRuntimeFilesAreNotBlessedByAnOldReceipt() {
        val base=Files.createTempDirectory("owned-extra-ready").toFile()
        try {
            val root=File(base,"rootfs").apply{mkdir()}
            install(root,OfflineModuleId.NODE_WEB){r->File(r,"opt/appforge-device/node-22.23.3/bin/node").apply{parentFile.mkdirs();writeText("node")}}
            assertTrue(ModuleOwnershipStore(root).verifyNamespace(OfflineModuleId.NODE_WEB))
            val extra=File(root,"opt/appforge-device/node-22.23.3/foreign.js");extra.writeText("preserve")
            assertFalse(ModuleOwnershipStore(root).verifyNamespace(OfflineModuleId.NODE_WEB))
            assertNotNull(ModuleOwnershipStore(root).removalReason(OfflineModuleId.NODE_WEB))
            assertEquals("preserve",extra.readText())
        }finally{base.deleteRecursively()}
    }

    @Test fun removingOwnedToolsPreservesDependencyCachesAndUserSources() {
        val base=Files.createTempDirectory("owned-cache-preserve").toFile()
        try {
            val root=File(base,"rootfs").apply{mkdir()};File(root,"user-project.txt").writeText("source")
            install(root,OfflineModuleId.NODE_WEB){r->
                File(r,"opt/appforge-device/node-22.23.3/bin/node").apply{parentFile.mkdirs();writeText("node")}
                File(r,"opt/appforge-device/npm-cache-v1/user-dependency").apply{parentFile.mkdirs();writeText("cache")}
            }
            File(root,"opt/appforge-device/npm-cache-v1/user-dependency").writeText("new project cache")
            assertNull(ModuleOwnershipStore(root).removalReason(OfflineModuleId.NODE_WEB))
            RuntimeGenerationStore(root).prepareOwner("REMOVE:NODE_WEB").use{tx->ModuleOwnershipStore(root).restoreInto(OfflineModuleId.NODE_WEB,tx.root);tx.commit(verify={})}
            assertEquals("new project cache",File(root,"opt/appforge-device/npm-cache-v1/user-dependency").readText())
            assertEquals("source",File(root,"user-project.txt").readText())
            assertFalse(File(root,"opt/appforge-device/node-22.23.3/bin/node").exists())
        }finally{base.deleteRecursively()}
    }

}
