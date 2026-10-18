package com.appforge.studio.build

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class RuntimeGenerationStoreTest {
    @Test fun stagingDoesNotTouchActiveAndCommitCanRollBackWholeOwnedEnvironment() {
        val base=Files.createTempDirectory("generation").toFile()
        try {
            val active=File(base,"rootfs").apply{mkdir()};File(active,"shared.txt").writeText("old")
            val external=File(base,"project.txt").apply{writeText("user source")}
            val store=RuntimeGenerationStore(active)
            store.prepare(OfflineModuleId.NODE_WEB).use {tx->
                File(tx.root,"shared.txt").writeText("new");File(tx.root,"node.txt").writeText("node")
                assertEquals("old",File(active,"shared.txt").readText())
                tx.commit(verify={assertTrue(File(it,"node.txt").isFile)})
            }
            assertEquals("new",File(active,"shared.txt").readText());assertTrue(store.rollbackAvailable(OfflineModuleId.NODE_WEB))
            store.rollback(OfflineModuleId.NODE_WEB)
            assertEquals("old",File(active,"shared.txt").readText());assertFalse(File(active,"node.txt").exists());assertEquals("user source",external.readText())
        }finally{base.deleteRecursively()}
    }
    @Test fun failuresAtBothRenameBoundariesRestorePriorGeneration() {
        for(point in listOf("before-switch","old-moved","new-moved")) {
            val base=Files.createTempDirectory("generation-fault").toFile()
            try {
                val active=File(base,"rootfs").apply{mkdir()};File(active,"valid").writeText("old")
                val store=RuntimeGenerationStore(active)
                assertThrows(IllegalStateException::class.java){store.prepare(OfflineModuleId.ANDROID_CORE).use{tx->File(tx.root,"valid").writeText("new");tx.commit(verify={},fault={if(it==point)error("injected")})}}
                assertEquals("old",File(active,"valid").readText())
            }finally{base.deleteRecursively()}
        }
    }
    @Test fun cancellationDiskAndSymlinksCannotDamageUserFiles() {
        val base=Files.createTempDirectory("generation-safe").toFile()
        try {
            val active=File(base,"rootfs").apply{mkdir()};File(active,"valid").writeText("old")
            val external=File(base,"private-project").apply{writeText("preserve")}
            Files.createSymbolicLink(File(active,"external-link").toPath(),external.toPath())
            val store=RuntimeGenerationStore(active)
            assertThrows(IllegalArgumentException::class.java){store.prepare(OfflineModuleId.PYTHON_ANDROID,availableBytes={0})}
            assertThrows(java.util.concurrent.CancellationException::class.java){store.prepare(OfflineModuleId.PYTHON_ANDROID,canceled={throw java.util.concurrent.CancellationException()})}
            store.prepare(OfflineModuleId.PYTHON_ANDROID).use{assertTrue(Files.isSymbolicLink(File(it.root,"external-link").toPath()))}
            assertEquals("preserve",external.readText());assertEquals("old",File(active,"valid").readText())
        }finally{base.deleteRecursively()}
    }
    @Test fun tamperedOldSnapshotCannotBecomeActive() {
        val base=Files.createTempDirectory("generation-tamper").toFile()
        try {
            val active=File(base,"rootfs").apply{mkdir()};File(active,"valid").writeText("old");val store=RuntimeGenerationStore(active)
            store.prepare(OfflineModuleId.WINDOWS_NATIVE).use{it.commit(verify={})}
            val old=base.listFiles()!!.single{it.name.startsWith(".v3-previous-")};File(old,"valid").writeText("tampered")
            assertThrows(IllegalArgumentException::class.java){store.rollback(OfflineModuleId.WINDOWS_NATIVE)}
            assertEquals("old",File(active,"valid").readText())
        }finally{base.deleteRecursively()}
    }
}
