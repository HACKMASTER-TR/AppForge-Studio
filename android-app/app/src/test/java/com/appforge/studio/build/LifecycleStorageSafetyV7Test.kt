package com.appforge.studio.build

import com.appforge.studio.terminal.RootfsActivationTransaction
import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LifecycleStorageSafetyV7Test {
    private fun node(
        id:String,
        parent:String?=null,
        module:OfflineModuleId=OfflineModuleId.NODE_WEB,
        stage:OperationStage=if(parent==null)OperationStage.MODULE else OperationStage.INSTALLATION,
        state:DownloadState=DownloadState.COMPLETED,
        createdAt:Long=1L,
        updatedAt:Long=2L
    )=DownloadOperationNode(id,parent,module,stage,state,DownloadNetworkPolicy.WIFI_ONLY,createdAt,updatedAt)

    @Test fun terminalSubtreePruningEvictsOldestCompleteSubtreesAndPreservesActiveOrRecoveryTrees() {
        val rootOld=PersistentOperationGraph.id("root","old")
        val childOld=PersistentOperationGraph.id("child","old")
        val rootRecovery=PersistentOperationGraph.id("root","recovery")
        val childRecovery=PersistentOperationGraph.id("child","recovery")
        val rootNew=PersistentOperationGraph.id("root","new")
        val childNew=PersistentOperationGraph.id("child","new")
        val rows=listOf(
            node(rootOld,state=DownloadState.COMPLETED,createdAt=1,updatedAt=10).copy(historyClosed=true),
            node(childOld,parent=rootOld,state=DownloadState.COMPLETED,createdAt=1,updatedAt=12),
            node(rootRecovery,state=DownloadState.COMPLETED,createdAt=2,updatedAt=5),
            node(childRecovery,parent=rootRecovery,state=DownloadState.RECOVERY_REQUIRED,createdAt=2,updatedAt=6),
            node(rootNew,state=DownloadState.CANCELLED,createdAt=3,updatedAt=20),
            node(childNew,parent=rootNew,state=DownloadState.FAILED,createdAt=3,updatedAt=25)
        )
        val pruned=PersistentOperationGraph.pruneRetained(rows,maxNodes=4)
        assertEquals(4,pruned.size)
        assertFalse(pruned.any{it.operationId==rootOld || it.operationId==childOld})
        assertTrue(pruned.any{it.operationId==rootRecovery})
        assertTrue(pruned.any{it.operationId==childRecovery})
        assertTrue(pruned.any{it.operationId==rootNew})
        assertTrue(pruned.any{it.operationId==childNew})
    }

    @Test fun coordinatorRecordsRemovalAndRollbackStagesAndReconcilesOnRestart() {
        val dir=Files.createTempDirectory("v7-lifecycle-graph").toFile()
        val journalField=DownloadCoordinator::class.java.getDeclaredField("journal").apply{isAccessible=true}
        val previous=journalField.get(DownloadCoordinator)
        val oldRows=DownloadCoordinator.records.value
        val oldTree=DownloadCoordinator.operations.value
        try {
            journalField.set(DownloadCoordinator,null)
            val store=PersistentDownloads(File(dir,"journal"),mapOf("private" to dir))
            DownloadCoordinator.configure(store)
            DownloadCoordinator.modulePhase(ModuleProgress(OfflineModuleId.NODE_WEB,"remove-op",ModulePhase.REMOVING,detail="removing"))
            DownloadCoordinator.modulePhase(ModuleProgress(OfflineModuleId.NODE_WEB,"remove-op",ModulePhase.REMOVED,detail="removed"))
            DownloadCoordinator.modulePhase(ModuleProgress(OfflineModuleId.ANDROID_CORE,"rollback-op",ModulePhase.ROLLING_BACK,detail="rolling back"))
            val tree=store.operationGraph().read()
            val removeParent=PersistentOperationGraph.moduleId("remove-op",OfflineModuleId.NODE_WEB)
            val removeStage=PersistentOperationGraph.id(removeParent,OperationStage.REMOVAL.name)
            assertEquals(DownloadState.COMPLETED,tree.single{it.operationId==removeParent}.operationState)
            assertEquals(DownloadState.COMPLETED,tree.single{it.operationId==removeStage}.operationState)
            val rollbackParent=PersistentOperationGraph.moduleId("rollback-op",OfflineModuleId.ANDROID_CORE)
            val rollbackStage=PersistentOperationGraph.id(rollbackParent,OperationStage.ROLLBACK.name)
            assertEquals(DownloadState.PREPARING,tree.single{it.operationId==rollbackStage}.operationState)
            val recovered=store.operationGraph().recover(emptyList())
            assertTrue(recovered.filter{it.operationId==removeParent || it.parentOperationId==removeParent}.all{it.operationState==DownloadState.COMPLETED})
            assertTrue(recovered.filter{it.operationId==rollbackParent || it.parentOperationId==rollbackParent}.all{it.operationState==DownloadState.RECOVERY_REQUIRED})
        } finally {
            journalField.set(DownloadCoordinator,previous)
            DownloadCoordinator.records.value=oldRows
            DownloadCoordinator.operations.value=oldTree
            dir.deleteRecursively()
        }
    }

    @Test fun moduleOperationJournalEnforcesDiskSpacePreflightAndRejectsUrls() {
        val dir=Files.createTempDirectory("v7-module-journal").toFile()
        try {
            val file=File(dir,"operation.json")
            val journal=ModuleOperationJournal(file)
            journal.save(mapOf(OfflineModuleId.NODE_WEB to ModuleProgress(OfflineModuleId.NODE_WEB,"op-1",ModulePhase.PAUSED,10,100,detail="safe detail")))
            val before=file.readBytes()
            val lowSpace=ModuleOperationJournal(file,availableBytes={0L})
            assertThrows(IllegalArgumentException::class.java) {
                lowSpace.save(mapOf(OfflineModuleId.NODE_WEB to ModuleProgress(OfflineModuleId.NODE_WEB,"op-2",ModulePhase.DOWNLOADING,20,100,detail="blocked")))
            }
            assertArrayEquals(before,file.readBytes())
            assertFalse(File(file.path+".tmp").exists())
            journal.save(mapOf(OfflineModuleId.NODE_WEB to ModuleProgress(OfflineModuleId.NODE_WEB,"op-3",ModulePhase.DOWNLOADING,10,100,detail="https://secret.example/token")))
            assertFalse(file.readText().contains("secret.example"))
            assertFalse(file.readText().contains("https://"))
            assertEquals("PROGRESS",JSONObject(file.readText()).getJSONObject("NODE_WEB").getString("detail"))
            assertTrue(journal.restoreStrict().getValue(OfflineModuleId.NODE_WEB).operationId.matches(Regex("id_[a-f0-9]{64}")))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun partialCopyingStageReclaimsUnmodifiedCloneWhileModifiedStageIsQuarantined() {
        val base=Files.createTempDirectory("v7-stage-reclaim").toFile()
        try {
            val root=File(base,"rootfs").apply{mkdir()}
            File(root,"a.txt").writeText("alpha")
            File(root,"b.txt").writeText("beta")
            var calls=0
            val store=RuntimeGenerationStore(root)
            assertThrows(java.util.concurrent.CancellationException::class.java) {
                store.prepare(OfflineModuleId.NODE_WEB,canceled={if(++calls>6)throw java.util.concurrent.CancellationException("stop during copy")})
            }
            assertEquals(0,store.quarantinedDirectories().size)
            assertEquals(0L,store.quarantinedBytes())
            assertFalse(base.listFiles()!!.any{it.name.startsWith(".v3-stage-")})
            assertThrows(IllegalStateException::class.java) {
                store.prepare(OfflineModuleId.NODE_WEB).use { tx ->
                    File(tx.root,"a.txt").writeText("staged-change")
                    tx.commit(verify={},fault={if(it=="before-switch")error("abort after stage edit")})
                }
            }
            assertEquals(1,store.quarantinedDirectories().size)
            assertTrue(store.quarantinedBytes()>0L)
            assertEquals("alpha",File(root,"a.txt").readText())
        } finally {
            base.deleteRecursively()
        }
    }

    @Test fun midCopyDiskDropAbortsCloneAndPreservesActiveRootWithoutLeakingDiscard() {
        val base=Files.createTempDirectory("v7-copy-space").toFile()
        try {
            val root=File(base,"rootfs").apply{mkdir()}
            File(root,"one.txt").writeText("first")
            File(root,"two.txt").writeText("second")
            var spaceChecks=0
            val store=RuntimeGenerationStore(root)
            assertThrows(IllegalArgumentException::class.java) {
                store.prepare(OfflineModuleId.NODE_WEB,availableBytes={if(++spaceChecks<=2)512L*1024*1024 else 0L})
            }
            assertEquals("first",File(root,"one.txt").readText())
            assertEquals("second",File(root,"two.txt").readText())
            assertEquals(0,store.quarantinedDirectories().size)
            assertFalse(File(base,".module-generation.json").exists())
        } finally {
            base.deleteRecursively()
        }
    }

    @Test fun runtimeArchiveRemovalAndPortableAndRootfsRejectLowSpaceAndDualDirectoryConflict() {
        val base=Files.createTempDirectory("v7-archive-safety").toFile()
        try {
            val runtime=File(base,"runtime-v3").apply{mkdir()}
            File(runtime,"marker").writeText("keep")
            assertThrows(IllegalArgumentException::class.java) {
                RuntimeArchiveRemoval(runtime,availableBytes={0L}).remove()
            }
            assertTrue(runtime.isDirectory)
            val backup=File(base,"runtime-v3-archived-00000000-0000-0000-0000-000000000000").apply{mkdir()}
            File(backup,"marker").writeText("backup")
            val journal=File(base,".runtime-removal-v5.json")
            journal.writeText(JSONObject().put("schema",1).put("archive",backup.name).put("state","PREPARED").toString())
            assertThrows(IllegalArgumentException::class.java) {
                RuntimeArchiveRemoval(runtime).recover()
            }
            assertTrue(journal.isFile)
            assertTrue(runtime.isDirectory)
            assertTrue(backup.isDirectory)

            val portableBase=File(base,"portable").apply{mkdirs()}
            val hostDir=File(portableBase,"offline-build-pack/windows-host-v1").apply{mkdirs()}
            val host=File(hostDir,"AppForge-Windows-Host-v1.exe").apply{writeText("host-bytes")}
            val sha=VerifiedModuleDownload.sha(host)
            assertThrows(IllegalArgumentException::class.java) {
                OwnedModuleFiles.removePortable(portableBase,sha,availableBytes={0L})
            }
            assertTrue(host.isFile)

            val swapBase=File(base,"swap").apply{mkdirs()}
            val rootfs=File(swapBase,"rootfs").apply{mkdir()}
            val meta=File(swapBase,"meta").apply{writeText("meta")}
            val stage=File(swapBase,"staging/install-id/rootfs").apply{mkdirs()}
            val tx=RootfsActivationTransaction(rootfs,File(swapBase,"restore-points"),meta,availableBytes={0L})
            assertThrows(IllegalArgumentException::class.java) {
                tx.activate(stage,{meta.writeText("new")})
            }
            assertEquals("meta",meta.readText())
        } finally {
            base.deleteRecursively()
        }
    }
}
