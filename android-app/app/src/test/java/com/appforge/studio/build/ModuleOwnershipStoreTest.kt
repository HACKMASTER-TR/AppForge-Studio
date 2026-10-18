package com.appforge.studio.build

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ModuleOwnershipStoreTest {
    private fun install(root:File,id:OfflineModuleId,action:(File)->Unit) {
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

}
