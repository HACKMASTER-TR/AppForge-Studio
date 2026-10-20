package com.appforge.studio.build

import java.io.File
import java.nio.file.Files
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class GenerationPreparationAuditTest {
    @Test fun cancellationAtEveryPreparationCallbackIncludingPostCopyInventoryPreservesActiveAndClosesClone() {
        // Three inventory, three copy, three staged inventory callbacks for this tree.
        for(boundary in 1..9) {
            val base=Files.createTempDirectory("audit-cancel").toFile()
            try {
                val root=File(base,"rootfs").apply{mkdir()}
                File(root,"a").writeText("alpha");File(root,"b").writeText("beta")
                val foreign=File(base,"user-key-fixture").apply{writeText("synthetic-not-a-key")}
                val store=RuntimeGenerationStore(root);val before=store.snapshot();var calls=0
                val cause=CancellationException("synthetic-boundary-$boundary")
                val thrown=assertThrows(CancellationException::class.java) {
                    store.prepare(OfflineModuleId.NODE_WEB,canceled={if(++calls==boundary)throw cause}).close()
                }
                assertSame(cause,thrown);assertEquals(before,store.snapshot())
                assertFalse(File(base,".module-generation.json").exists())
                assertFalse(base.listFiles()!!.any{it.name.startsWith(".v3-stage-")})
                assertEquals(0,store.quarantinedDirectories().size)
                store.recover();store.recover();assertEquals(before,store.snapshot())
                assertEquals("synthetic-not-a-key",foreign.readText())
            } finally {base.deleteRecursively()}
        }
    }
    @Test fun storageFailuresAtAllPreparationWritesPreserveTheLastWorkingTree() {
        for(boundary in 1..4) {
            val base=Files.createTempDirectory("audit-enospc").toFile()
            try {
                val root=File(base,"rootfs").apply{mkdir()};File(root,"a").writeText("alpha");File(root,"b").writeText("beta")
                val store=RuntimeGenerationStore(root);val before=store.snapshot();var calls=0
                assertThrows(IllegalArgumentException::class.java) {
                    store.prepare(OfflineModuleId.NODE_WEB,availableBytes={if(++calls==boundary)0L else Long.MAX_VALUE}).close()
                }
                store.recover();assertEquals(before,store.snapshot())
                assertFalse(File(base,".module-generation.json").exists())
                assertFalse(base.listFiles()!!.any{it.name.startsWith(".v3-stage-")})
            } finally {base.deleteRecursively()}
        }
    }
}
