package com.appforge.studio.build

import java.io.File
import org.json.JSONObject
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class RuntimeStorageBudgetTest {
    @Test fun windowsPortableDoesNotRequireLinuxGenerationAdmission() {
        assertTrue(RuntimeStorageBudget.runtimePending(listOf(OfflineModuleId.WINDOWS_PORTABLE)).isEmpty())
        assertTrue(RuntimeStorageBudget.runtimePending(listOf(OfflineModuleId.FAST_HTML,OfflineModuleId.WINDOWS_PORTABLE)).isEmpty())
        assertEquals(listOf(OfflineModuleId.RUST),RuntimeStorageBudget.runtimePending(
            listOf(OfflineModuleId.WINDOWS_PORTABLE,OfflineModuleId.RUST)))
    }
    @Test fun belowLimitSnapshotAgreesBetweenDashboardAndInstallation()=fixture { parent,root ->
        val snapshot=File(parent,".v3-previous-${UUID.randomUUID()}").apply{mkdir()}
        val marker=File(snapshot,"existing.txt").apply{writeText("keep")}
        val report=RuntimeStorageBudget.inspect(root,available=Long.MAX_VALUE)
        assertEquals(1,report.obsoleteSnapshotCount)
        assertEquals(4L,report.obsoleteSnapshotBytes)
        assertNull(report.blockedReason)
        RuntimeStorageBudget.requireAdmission(root,available=Long.MAX_VALUE)
        assertEquals("keep",marker.readText())
    }

    @Test fun twoLargeHistoricalSnapshotsDoNotBlockWhenDiskHasHeadroom()=fixture { parent,root ->
        val sizes=listOf(1_600_000_000L,1_703_065_227L)
        val files=sizes.map { size ->
            val snapshot=File(parent,".v3-previous-${UUID.randomUUID()}").apply{mkdir()}
            File(snapshot,"historical-preserved").also { file ->
                java.io.RandomAccessFile(file,"rw").use{it.setLength(size)}
            }
        }
        val report=RuntimeStorageBudget.inspect(root,available=Long.MAX_VALUE)
        assertEquals(2,report.obsoleteSnapshotCount)
        assertEquals(3_303_065_227L,report.obsoleteSnapshotBytes)
        assertNull(report.blockedReason)

        RuntimeStorageBudget.requireAdmission(root,
            expansion=12L*1024*1024*1024,available=Long.MAX_VALUE)

        RuntimeGenerationStore(root).prepare(OfflineModuleId.NODE_WEB,
            availableBytes={Long.MAX_VALUE}).use { assertTrue(it.root.isDirectory) }

        assertTrue(files.all{it.isFile})
        assertEquals(sizes,files.map{it.length()})
        assertTrue(File(root,"vendor").isFile)

        val blocked=RuntimeStorageBudget.inspect(root,
            expansion=12L*1024*1024*1024,available=0L)
        assertTrue(blocked.blockedReason.orEmpty().contains("yetersiz"))

        assertThrows(IllegalArgumentException::class.java) {
            RuntimeStorageBudget.requireAdmission(root,
                expansion=12L*1024*1024*1024,available=0L)
        }
        assertTrue(files.all{it.isFile})
    }
    @Test fun sixteenSmallUnownedSnapshotsRemainProtectedAndDiagnosed()=fixture { parent,root ->
        val old=(1..16).map { id ->
            File(parent,".v3-previous-${UUID.randomUUID()}").apply { mkdir();File(this,"marker-$id").writeText("keep") }
        }
        val report=RuntimeStorageBudget.inspect(root,available=Long.MAX_VALUE)
        assertEquals(16,report.obsoleteSnapshotCount)
        assertTrue(report.blockedReason.orEmpty().contains("adet=16"))
        assertThrows(IllegalArgumentException::class.java) {
            RuntimeStorageBudget.requireAdmission(root,available=Long.MAX_VALUE)
        }
        assertTrue(old.all{it.isDirectory})
    }
    @Test fun approvedBackupVerifiesRawTarThenAllowsRetryWithoutChangingActive()=fixture { parent,root ->
        val store=RuntimeGenerationStore(root)
        val original=store.snapshot()
        val quarantine=File(parent,".v3-discard-${UUID.randomUUID()}")
        root.copyRecursively(quarantine,overwrite=true)
        File(quarantine,"failure-only.txt").writeText("installer-created output")
        RuntimeStorageBudget.requireAdmission(root)
        val archive=java.io.ByteArrayOutputStream()
        val released=store.archiveAndPruneQuarantine(quarantine.name,archive,
            reread={java.io.ByteArrayInputStream(archive.toByteArray())})
        assertTrue(released>0L)
        assertTrue(archive.toByteArray().isNotEmpty())
        assertTrue(store.quarantinedDirectories().isEmpty())
        assertEquals(original,store.snapshot())
        store.prepare(OfflineModuleId.NODE_WEB).use {assertTrue(it.root.isDirectory)}
    }
    @Test fun corruptOrMovingBackupNeverDeletesQuarantine()=fixture { parent,root ->
        val store=RuntimeGenerationStore(root)
        val candidate=File(parent,".v3-discard-${UUID.randomUUID()}")
        root.copyRecursively(candidate,overwrite=true)
        File(candidate,"failure.txt").writeText("keep")
        val archive=java.io.ByteArrayOutputStream()
        assertThrows(Exception::class.java){store.archiveAndPruneQuarantine(candidate.name,archive,
            reread={java.io.ByteArrayInputStream(byteArrayOf(0,1,2,3))})}
        assertTrue(candidate.isDirectory)
        assertThrows(IllegalArgumentException::class.java){store.archiveAndPruneQuarantine("rootfs",
            java.io.ByteArrayOutputStream(),reread={java.io.ByteArrayInputStream(byteArrayOf())})}
        assertTrue(File(root,"vendor").isFile)
        assertTrue(candidate.isDirectory)
    }
    @Test fun quarantineBackupRefusesPendingJournalAndOutsideFiles()=fixture { parent,root ->
        val store=RuntimeGenerationStore(root)
        val candidate=File(parent,".v3-discard-${UUID.randomUUID()}")
        root.copyRecursively(candidate,overwrite=true)
        File(candidate,"temporary").writeText("working")
        val external=File(parent,"user-project").apply{writeText("untouched")}
        val journal=File(parent,".module-generation.json").apply{writeText("unrecovered")}
        assertThrows(IllegalArgumentException::class.java){store.archiveAndPruneQuarantine(candidate.name,
            java.io.ByteArrayOutputStream(),reread={java.io.ByteArrayInputStream(byteArrayOf())})}
        assertTrue(candidate.isDirectory)
        assertEquals("untouched",external.readText())
        assertTrue(journal.delete())
    }
    @Test fun liveDpkgRenameIsRetriedButOtherFilesystemErrorsRemainFatal() {
        assertNull(RuntimeStorageBudget.sampleVolatile<Long> {
            throw java.nio.file.NoSuchFileException("utf8.pm.dpkg-tmp")
        })
        assertEquals(17L,RuntimeStorageBudget.sampleVolatile { 17L })
        assertThrows(java.nio.file.AccessDeniedException::class.java) {
            RuntimeStorageBudget.sampleVolatile<Long> {
                throw java.nio.file.AccessDeniedException("protected")
            }
        }
    }
    private fun fixture(block:(File,File)->Unit) {
        val parent=Files.createTempDirectory("bounded-storage").toFile()
        try {val root=File(parent,"rootfs").apply{mkdir()};File(root,"vendor").writeText("verified vendor")
            block(parent,root)
        } finally {parent.deleteRecursively()}
    }
    @Test fun twentyFailedOrCanceledAttemptsKeepWorkingGenerationCacheProjectsAndRollback()=fixture { parent,root ->
        val project=File(parent,"project.txt").apply{writeText("user source")}
        val cache=File(parent,".module-archive-cache-v5").apply{mkdir()}
        File(cache,"verified.archive").writeText("verified bytes")
        BaseRuntimeOwnership.captureFresh(root)
        val store=RuntimeGenerationStore(root)
        store.prepare(OfflineModuleId.NODE_WEB).use { tx ->
            val tool=File(tx.root,"opt/appforge-device/node-22.23.3/bin/node").apply{parentFile.mkdirs();writeText("node fixture")}
            ModuleOwnershipStore(tx.root).record(OfflineModuleId.NODE_WEB,tx.previousRootName,store.snapshot(),RuntimeGenerationStore(tx.root).snapshot(),root)
            tx.commit(verify={assertTrue(tool.isFile)})
        }
        val expected=store.snapshot()
        assertTrue(ModuleOwnershipStore(root).verifyNamespace(OfflineModuleId.NODE_WEB))
        val beforeBytes=RuntimeStorageBudget.measure(parent).logicalBytes
        fun inodes():Long=Files.walk(parent.toPath()).use{paths->paths.filter{Files.isRegularFile(it,java.nio.file.LinkOption.NOFOLLOW_LINKS)}.map{Files.readAttributes(it,java.nio.file.attribute.BasicFileAttributes::class.java).fileKey()}.distinct().count()}
        val beforeInodes=inodes()
        val du=File("/usr/bin/du")
        fun measuredDu():String=if(du.canExecute())ProcessBuilder(du.path,"-sk",parent.path).start().inputStream.bufferedReader().readText().substringBefore('\t').trim() else "NOT_RUN"
        val beforeDu=measuredDu()
        var stableBytes:Long?=null
        repeat(20) { attempt ->
            try {
                store.prepare(OfflineModuleId.PYTHON_ANDROID).use { tx ->
                    File(tx.root,"failed-install").writeBytes(ByteArray(8192){42})
                    throw CancellationException("injected failure/cancellation")
                }
                fail("Attempt must fail or be denied")
            } catch(_:CancellationException) {} catch(e:IllegalArgumentException) {
                assertTrue(e.message.orEmpty().contains("STORAGE_BUDGET"))
            }
            store.recover()
            assertEquals(expected,store.snapshot())
            assertTrue(ModuleOwnershipStore(root).verifyNamespace(OfflineModuleId.NODE_WEB))
            assertTrue(store.rollbackAvailable(OfflineModuleId.NODE_WEB))
            assertEquals("user source",project.readText())
            assertEquals("verified bytes",File(cache,"verified.archive").readText())
            assertEquals(0,store.quarantinedDirectories().size)
            assertFalse(parent.listFiles()!!.any{it.name.startsWith(".v3-stage-")})
            val measured=RuntimeStorageBudget.measure(parent).logicalBytes
            if(attempt>0)assertEquals(stableBytes,measured)
            stableBytes=measured
        }
        println("STORAGE_20_ATTEMPTS before_logical_bytes=$beforeBytes after_logical_bytes=$stableBytes before_inodes=$beforeInodes after_inodes=${inodes()} before_du_kib=$beforeDu after_du_kib=${measuredDu()} quarantine_copies=0 staging_copies=0 freed_bytes=0")
    }
    @Test fun repeatedFailedInstallWithChangedStageRestartsCleanly()=fixture { parent,root ->
        val original=RuntimeGenerationStore(root).snapshot()
        val project=File(parent,"user-project.txt").apply{writeText("keep project")}
        val store=RuntimeGenerationStore(root)
        repeat(8) { index ->
            store.prepare(OfflineModuleId.RUST_ANDROID_NDK,availableBytes={Long.MAX_VALUE}).use { stage ->
                File(stage.root,"installer-temp-$index").writeText("incomplete")
            }
            store.recover()
            assertTrue(store.quarantinedDirectories().isEmpty())
            assertFalse(parent.listFiles().orEmpty().any{it.name.startsWith(".v3-stage-")})
            assertEquals(original,store.snapshot())
            assertEquals("keep project",project.readText())
            RuntimeStorageBudget.requireAdmission(root,available=Long.MAX_VALUE)
        }
    }
    @Test fun retainedFailedGenerationCanCoexistWithFreshInstall()=fixture { parent,root ->
        val copy=File(parent,".v3-discard-${UUID.randomUUID()}")
        root.copyRecursively(copy,overwrite=true)
        File(copy,"unknown-user-file").writeText("retain intact")
        RuntimeStorageBudget.requireAdmission(root,available=Long.MAX_VALUE)
        val store=RuntimeGenerationStore(root)
        store.prepare(OfflineModuleId.NODE_WEB,availableBytes={Long.MAX_VALUE}).use { stage ->
            assertTrue(stage.root.isDirectory)
        }
        assertEquals("retain intact",File(copy,"unknown-user-file").readText())
        assertEquals(1,store.quarantinedDirectories().size)
    }
    @Test fun liveStagingStillBlocksAndUntrustedQuarantineLinkIsRejected()=fixture { parent,root ->
        val stage=File(parent,".v3-stage-${UUID.randomUUID()}").apply{mkdirs()}
        assertThrows(IllegalArgumentException::class.java) {
            RuntimeStorageBudget.requireAdmission(root,available=Long.MAX_VALUE)
        }
        assertTrue(stage.delete())
        val external=File(parent,"private-user-file").apply{writeText("keep")}
        val link=File(parent,".v3-discard-${UUID.randomUUID()}")
        Files.createSymbolicLink(link.toPath(),external.toPath())
        assertThrows(IllegalArgumentException::class.java) {
            RuntimeStorageBudget.requireAdmission(root,available=Long.MAX_VALUE)
        }
        assertEquals("keep",external.readText())
    }
    @Test fun twentyCanceledUnmodifiedCopiesAreRecoveredWithoutQuarantine()=fixture { parent,root ->
        val store=RuntimeGenerationStore(root);val before=store.snapshot()
        repeat(20) {
            assertThrows(CancellationException::class.java){store.prepare(OfflineModuleId.NODE_WEB).use{throw CancellationException()}}
            store.recover();assertEquals(before,store.snapshot())
            assertEquals(0,store.quarantinedDirectories().size)
            assertEquals(0,parent.listFiles()!!.count{it.name.startsWith(".v3-stage-")})
        }
    }
    @Test fun stageIsolationAndHardlinkAliasNeverReachActive()=fixture { parent,root ->
        val external=File(parent,"project").apply{writeText("external")}
        Files.createLink(File(root,"hardlinked-input").toPath(),external.toPath())
        val original=File(root,"vendor").readText()
        val store=RuntimeGenerationStore(root)
        // Active state was edited while the stage was in progress. Fail closed.
        assertThrows(IllegalArgumentException::class.java) {
            store.prepare(OfflineModuleId.NODE_WEB).use { tx ->
                val staged=File(tx.root,"vendor")
                File(root,"vendor").writeText("active change")
                assertEquals(original,staged.readText())
                staged.writeText("stage change")
                assertEquals("active change",File(root,"vendor").readText())
                File(tx.root,"hardlinked-input").writeText("staged only")
                assertEquals("external",external.readText())
                assertFalse(Files.isSameFile(File(tx.root,"hardlinked-input").toPath(),external.toPath()))
            }
        }
        assertEquals("external",external.readText())
        assertEquals("active change",File(root,"vendor").readText())
        val journal=File(parent,".module-generation.json")
        assertTrue(journal.isFile)
        val staged=File(parent,JSONObject(journal.readText()).getString("stage"))
        assertEquals("staged only",File(staged,"hardlinked-input").readText())
        assertThrows(IllegalArgumentException::class.java){store.recover()}
        assertTrue(journal.isFile)
    }
    @Test fun processLockDeniesConcurrentPrepareAndRecovery()=fixture { parent,root ->
        RuntimeGenerationStore(root).prepare(OfflineModuleId.NODE_WEB).use {
            assertThrows(IllegalArgumentException::class.java){RuntimeGenerationStore(root).prepare(OfflineModuleId.RUST)}
            assertThrows(IllegalArgumentException::class.java){RuntimeGenerationStore(root).recover()}
        }
        RuntimeMutationLock.acquire(parent).close()
    }
    @Test fun enospcAndHistoricalQuarantineDenyAdmissionWithoutChanges()=fixture { parent,root ->
        val before=RuntimeGenerationStore(root).snapshot()
        assertThrows(IllegalArgumentException::class.java){RuntimeStorageBudget.requireAdmission(root,available=0)}
        val foreign=File(parent,".v3-discard-${UUID.randomUUID()}").apply{mkdir()}
        File(foreign,"unknown").writeText("keep")
        repeat(20){RuntimeStorageBudget.requireAdmission(root,available=Long.MAX_VALUE)}
        assertEquals(before,RuntimeGenerationStore(root).snapshot());assertEquals("keep",File(foreign,"unknown").readText())
    }
    @Test fun cleanupRequiresOwnershipAndRejectsAChangedReviewedPlan()=fixture { parent,root ->
        BaseRuntimeOwnership.captureFresh(root)
        val copy=File(parent,".v3-discard-${UUID.randomUUID()}").apply{mkdir()}
        root.copyRecursively(copy,overwrite=true)
        val store=RuntimeGenerationStore(root);val plan=store.duplicateCleanupPlan()
        assertEquals(1,plan.candidates.size)
        File(copy,"foreign-project").writeText("preserve")
        assertThrows(IllegalArgumentException::class.java){store.executeDuplicateCleanup(plan)}
        assertEquals("preserve",File(copy,"foreign-project").readText())
        assertEquals(0,store.duplicateCleanupPlan().candidates.size)
    }
    @Test fun unknownDuplicateWithoutVendorOwnershipCannotBeCleaned()=fixture { parent,root ->
        val copy=File(parent,".v3-discard-${UUID.randomUUID()}").apply{mkdir()};root.copyRecursively(copy,overwrite=true)
        val store=RuntimeGenerationStore(root)
        assertTrue(store.duplicateCleanupPlan().candidates.isEmpty())
        assertThrows(IllegalArgumentException::class.java){store.pruneDuplicateQuarantines()}
        assertTrue(copy.isDirectory)
    }
    @Test fun canceledInventoryNeverDeletesSymlinkTargets()=fixture { parent,root ->
        val project=File(parent,"project").apply{writeText("keep")}
        Files.createSymbolicLink(File(root,"link").toPath(),project.toPath())
        assertThrows(CancellationException::class.java){RuntimeStorageBudget.inspect(root,canceled={throw CancellationException()})}
        assertEquals("keep",project.readText())
    }
}
