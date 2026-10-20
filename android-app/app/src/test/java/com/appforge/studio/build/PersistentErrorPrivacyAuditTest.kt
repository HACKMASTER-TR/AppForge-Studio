package com.appforge.studio.build

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class PersistentErrorPrivacyAuditTest {
    @Test fun artifactAndGraphStoresRedactLegacyCodeShapedSensitiveTextWithoutTrustingMalformedRecords() {
        val dir=Files.createTempDirectory("audit-all-journals").toFile()
        try {
            val downloads=PersistentDownloads(File(dir,"journal"),mapOf("private" to dir))
            val row=ArtifactDownloadRecord("audit-operation",OfflineModuleId.NODE_WEB,"a".repeat(64),"1","b".repeat(64),null,null,0,DownloadState.FAILED,DownloadCoordinator.ResumeCapability.SAFE_RESTART_ONLY,DownloadNetworkPolicy.WIFI_ONLY,0,1,2,"UNVERIFIED",null,"TOKEN_SYNTHETIC_MARKER")
            downloads.save(listOf(row))
            assertFalse(File(dir,"journal/artifacts.json").readText().contains("SYNTHETIC_MARKER"))
            assertEquals("DRIVER_FAILED",downloads.read().single().failureReason)
            val graph=downloads.operationGraph()
            val n=DownloadOperationNode("c".repeat(64),null,OfflineModuleId.NODE_WEB,OperationStage.MODULE,DownloadState.FAILED,DownloadNetworkPolicy.WIFI_ONLY,1,2,failureReason="TOKEN_SYNTHETIC_MARKER")
            graph.save(listOf(n));assertEquals("DRIVER_FAILED",graph.read().single().failureReason)
            assertFalse(dir.walkTopDown().filter{it.isFile}.any{it.readText().contains("SYNTHETIC_MARKER")})
            assertThrows(Exception::class.java){downloads.save(listOf(row.copy(failureReason="https://invalid.example")))}
            assertThrows(Exception::class.java){graph.save(listOf(n.copy(failureReason="https://invalid.example")))}
        } finally {dir.deleteRecursively()}
    }
    @Test fun coordinatorCannotTurnAnUntrustedErrorStringIntoAPersistentCode() {
        val dir=Files.createTempDirectory("audit-coordinator-error").toFile()
        val field=DownloadCoordinator::class.java.getDeclaredField("journal").apply{isAccessible=true}
        val previous=field.get(DownloadCoordinator);val records=DownloadCoordinator.records.value;val tree=DownloadCoordinator.operations.value
        try {
            field.set(DownloadCoordinator,null)
            val store=PersistentDownloads(File(dir,"journal"),mapOf("private" to dir));DownloadCoordinator.configure(store)
            DownloadCoordinator.observe(DownloadCoordinator.Transfer("a".repeat(64),"1","b".repeat(64),ModuleProgress(OfflineModuleId.NODE_WEB,"audit-operation",ModulePhase.ERROR,errorCode="Authorization: Bearer SYNTHETIC_MARKER",detail="password=SYNTHETIC_MARKER"),DownloadCoordinator.ResumeCapability.SAFE_RESTART_ONLY))
            assertEquals("DRIVER_FAILED",store.read().single().failureReason)
            assertFalse(dir.walkTopDown().filter{it.isFile}.any{it.readText().contains("SYNTHETIC_MARKER")})
        } finally {field.set(DownloadCoordinator,previous);DownloadCoordinator.records.value=records;DownloadCoordinator.operations.value=tree;dir.deleteRecursively()}
    }
}
