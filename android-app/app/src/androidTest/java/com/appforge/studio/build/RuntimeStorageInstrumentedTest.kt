package com.appforge.studio.build

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CancellationException

/** Disposable fixtures only. Does not access the installed device-build runtime or user projects. */
@RunWith(AndroidJUnit4::class)
class RuntimeStorageInstrumentedTest {
    @Test fun twentyFailuresPreserveActiveAndRetainQuarantineOnAndroidFilesystem() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        // Production normalizes only Android's system-owned app-data path alias.
        // Do the same for this disposable fixture; reject all arbitrary symlinks.
        val cache=RecoveryPathAnchor.androidPlatformPath(context.cacheDir)
        RecoveryPathAnchor.check(cache)
        val parent=Files.createTempDirectory(cache.toPath(),"storage-acceptance-").toFile()
        try {
            val active=File(parent,"rootfs").apply{mkdir()};File(active,"vendor").writeText("keep")
            val store=RuntimeGenerationStore(active);val before=store.snapshot()
            var first:Long?=null
            repeat(20) { n ->
                try {
                    store.prepare(OfflineModuleId.NODE_WEB).use{tx->File(tx.root,"failed").writeText("partial install");throw CancellationException()}
                    fail("Expected cancellation or admission denial")
                } catch(_:CancellationException) {} catch(e:IllegalArgumentException) {
                    assertTrue("Unexpected storage admission error on attempt ${n+1}: ${e.javaClass.simpleName}: ${e.message}",
                        e.message.orEmpty().contains("STORAGE_BUDGET"))
                }
                store.recover();assertEquals(before,store.snapshot())
                val quarantine=store.quarantinedDirectories()
                assertEquals(n+1,quarantine.size)
                assertTrue(quarantine.all { candidate ->
                    candidate.isDirectory && File(candidate,"failed").readText()=="partial install"
                })
                assertFalse(parent.listFiles().orEmpty().any{it.name.startsWith(".v3-stage-")})
                val bytes=RuntimeStorageBudget.measure(parent).logicalBytes
                if(n==0)first=bytes else assertTrue("Unexpected reduction of protected bytes",bytes>=first!!)
                assertThrows(IllegalArgumentException::class.java) {
                    RuntimeStorageBudget.requireAdmission(active,available=0L)
                }
                assertEquals(before,store.snapshot())
            }
        } finally {parent.deleteRecursively()}
    }
    @Test fun cloneCapabilityAndFallbackPreserveBidirectionalIsolation() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val parent=Files.createTempDirectory(context.cacheDir.toPath(),"clone-acceptance-").toFile()
        try {
            val source=File(parent,"source").apply{writeText("original")};val target=File(parent,"copy")
            val reflink=RuntimeFileClone.supported(parent)
            RuntimeFileClone.copy(source.toPath(),target.toPath(),reflink)
            source.writeText("active changed");assertEquals("original",target.readText())
            target.writeText("stage changed");assertEquals("active changed",source.readText())
            assertFalse(Files.isSameFile(source.toPath(),target.toPath()))
            assertTrue(RuntimeFileClone.allocatedBytes(source.path)>=0)
        } finally {parent.deleteRecursively()}
    }
    @Test fun verifiedObjectReuseRequiresNoNetwork() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val parent=Files.createTempDirectory(context.cacheDir.toPath(),"cache-acceptance-").toFile()
        try {
            val target=File(parent,"object").apply{writeText("pinned bytes")}
            var gets=0;val events=mutableListOf<ModuleProgress>()
            VerifiedModuleDownload.download("https://nodejs.org/dist/fixture",VerifiedModuleDownload.sha(target),target,
                OfflineModuleId.NODE_WEB,"instrumented-cache",open={gets++;error("Network forbidden")},progress={events.add(it)},expectedBytes=target.length())
            assertEquals(0,gets);assertTrue(events.any{it.cacheHit && it.networkBytes==0L && it.networkGetCount==0L})
            assertFalse(events.any{it.phase==ModulePhase.CONNECTING || it.phase==ModulePhase.READY})
        } finally {parent.deleteRecursively()}
    }
}
