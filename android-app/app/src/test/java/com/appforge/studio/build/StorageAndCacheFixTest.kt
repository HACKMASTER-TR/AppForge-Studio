package com.appforge.studio.build

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

/** Regression coverage for opt-in exact-duplicate cleanup and offline SHA-256 cache reuse. */
class StorageAndCacheFixTest {
    @Test fun duplicateQuarantineCleanupNeverRemovesDivergentOrActiveData() {
        val base=Files.createTempDirectory("generation-discard").toFile()
        try {
            val active=File(base,"rootfs").apply{mkdir()}
            File(active,"valid").writeText("original")
            val duplicate=File(base,".v3-discard-${java.util.UUID.randomUUID()}").apply{mkdir()}
            File(duplicate,"valid").writeText("original")
            val divergent=File(base,".v3-discard-${java.util.UUID.randomUUID()}").apply{mkdir()}
            File(divergent,"valid").writeText("different-user-content")
            val external=File(base,"private-project").apply{writeText("keep me")}
            val store=RuntimeGenerationStore(active)
            assertEquals(8L,store.duplicateQuarantineBytes())
            assertEquals(8L,store.pruneDuplicateQuarantines())
            assertFalse(duplicate.exists())
            assertTrue(divergent.isDirectory)
            assertEquals("different-user-content",File(divergent,"valid").readText())
            assertEquals("original",File(active,"valid").readText())
            assertEquals("keep me",external.readText())
        } finally {base.deleteRecursively()}
    }

    @Test fun runningGenerationTransactionBlocksQuarantineCleanup() {
        val base=Files.createTempDirectory("generation-live").toFile()
        try {
            val active=File(base,"rootfs").apply{mkdir()}
            File(active,"valid").writeText("source")
            val store=RuntimeGenerationStore(active)
            store.prepare(OfflineModuleId.NODE_WEB).use {
                assertThrows(IllegalArgumentException::class.java) { store.pruneDuplicateQuarantines() }
            }
            assertEquals("source",File(active,"valid").readText())
        } finally {base.deleteRecursively()}
    }

    @Test fun validLocalSha256ArchiveUsesZeroNetworkBytes() {
        val base=Files.createTempDirectory("verified-cache").toFile()
        try {
            val data="already-downloaded".toByteArray()
            val target=File(base,"archive.tar.xz").apply{writeBytes(data)}
            val sha=VerifiedModuleDownload.sha(target)
            val events=mutableListOf<ModuleProgress>()
            VerifiedModuleDownload.download("https://node.example/archive.tar.xz",sha,target,
                OfflineModuleId.NODE_WEB,"cache-only",open={error("Network call forbidden on cache hit")},
                progress={events.add(it)})
            assertTrue(events.any{it.detail.contains("ÖNBELLEKTEN KULLANILDI")})
            assertFalse(events.any{it.phase==ModulePhase.CONNECTING || it.phase==ModulePhase.DOWNLOADING})
            assertThrows(IllegalArgumentException::class.java) {
                VerifiedModuleDownload.download("http://node.example/archive.tar.xz",sha,target,
                    OfflineModuleId.NODE_WEB,"unsafe-cache",open={error("Network forbidden")})
            }
            assertArrayEquals(data,target.readBytes())
        } finally {base.deleteRecursively()}
    }
}
