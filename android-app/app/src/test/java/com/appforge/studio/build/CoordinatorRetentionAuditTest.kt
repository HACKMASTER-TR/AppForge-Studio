package com.appforge.studio.build

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class CoordinatorRetentionAuditTest {
    private fun fixture(run:(PersistentDownloads)->Unit) {
        val dir=Files.createTempDirectory("audit-real-coordinator").toFile()
        val field=DownloadCoordinator::class.java.getDeclaredField("journal").apply{isAccessible=true}
        val previous=field.get(DownloadCoordinator);val records=DownloadCoordinator.records.value;val tree=DownloadCoordinator.operations.value
        try {field.set(DownloadCoordinator,null);val store=PersistentDownloads(File(dir,"journal"),mapOf("private" to dir));DownloadCoordinator.configure(store);run(store)}
        finally {field.set(DownloadCoordinator,previous);DownloadCoordinator.records.value=records;DownloadCoordinator.operations.value=tree;dir.deleteRecursively()}
    }
    private fun phase(op:String,p:ModulePhase)=DownloadCoordinator.modulePhase(ModuleProgress(OfflineModuleId.NODE_WEB,op,p))
    private fun complete(op:String) {for(p in listOf(ModulePhase.RESOLVING,ModulePhase.PREPARING,ModulePhase.INSTALLING,ModulePhase.OFFLINE_TEST,ModulePhase.VERIFYING,ModulePhase.READY))phase(op,p)}
    @Test fun sixHundredRealFlowsWithRestartAndMixedIncompleteHistoryRemainBounded()=fixture {store->
        for((op,end) in listOf("paused" to ModulePhase.PAUSED,"failed" to ModulePhase.ERROR,"cancelled" to ModulePhase.CANCELED)) {
            phase(op,ModulePhase.RESOLVING);phase(op,ModulePhase.INSTALLING);phase(op,end)
        }
        for(i in 1..600) {
            complete("success-$i")
            assertTrue(DownloadCoordinator.operations.value.size<=512)
            if(i%100==0) {
                val recovered=store.operationGraph().recover(emptyList())
                DownloadCoordinator.operations.value=recovered
                DownloadCoordinator.reconcileModules(mapOf(OfflineModuleId.NODE_WEB to ModuleProgress(OfflineModuleId.NODE_WEB,"success-$i",ModulePhase.READY)))
                val root=recovered.single{it.operationId==PersistentOperationGraph.moduleId("success-$i",OfflineModuleId.NODE_WEB)}
                assertTrue(root.historyClosed);assertEquals(DownloadState.COMPLETED,root.operationState)
            }
        }
        val rows=store.operationGraph().read()
        for(op in listOf("paused","failed","cancelled")) {
            val id=PersistentOperationGraph.moduleId(op,OfflineModuleId.NODE_WEB)
            assertTrue(rows.any{it.operationId==id});assertFalse(rows.single{it.operationId==id}.historyClosed)
            assertTrue(rows.any{it.parentOperationId==id && it.operationState==DownloadState.RECOVERY_REQUIRED})
        }
        assertTrue(rows.filter{it.historyClosed}.all{r->rows.filter{it.parentOperationId==r.operationId}.all{it.operationState==DownloadState.COMPLETED}})
    }
    @Test fun readyCannotClosePausedOrRecoveryChildAndLegacyTerminalHistoryIsNotBlessed()=fixture {store->
        phase("incomplete",ModulePhase.INSTALLING)
        val rows=store.operationGraph().read().map{if(it.parentOperationId!=null)it.copy(operationState=DownloadState.PAUSED)else it}
        store.operationGraph().save(rows);DownloadCoordinator.operations.value=rows
        phase("incomplete",ModulePhase.READY)
        assertFalse(store.operationGraph().read().single{it.parentOperationId==null}.historyClosed)
        val recovered=store.operationGraph().recover(emptyList())
        assertTrue(recovered.all{it.operationState==DownloadState.RECOVERY_REQUIRED})
    }
    @Test fun concurrentRealFlowsDoNotLoseParentsOrChildren()=fixture {store->
        val errors=java.util.concurrent.ConcurrentLinkedQueue<Throwable>()
        val threads=(1..4).map{worker->Thread {try{for(i in 1..40)complete("worker-$worker-$i")}catch(e:Throwable){errors.add(e)}}.apply{start()}}
        threads.forEach{it.join(30000);assertFalse(it.isAlive)}
        assertTrue(errors.toString(),errors.isEmpty())
        val rows=store.operationGraph().read();assertTrue(rows.size<=512)
        assertTrue(rows.filter{it.parentOperationId==null}.all{it.historyClosed})
        assertTrue(rows.all{it.operationState==DownloadState.COMPLETED})
    }
}
