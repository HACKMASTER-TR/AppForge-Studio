package com.appforge.studio.terminal
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test
class RootfsActivationTransactionTest {
    @Test fun eachSwapBoundaryPreservesPreviousRootAndMetadataOnFailure() {
        for(point in listOf("before-switch","old-moved","new-moved","metadata-written")) {
            val base=Files.createTempDirectory("root-swap-fault").toFile()
            try {
                val root=File(base,"rootfs").apply{mkdir()};File(root,"valid").writeText("old")
                val meta=File(base,"meta").apply{writeText("old metadata")}
                val stage=File(base,"staging/install-id/rootfs").apply{mkdirs()};File(stage,"valid").writeText("new")
                val store=RootfsActivationTransaction(root,File(base,"restore-points"),meta)
                assertThrows(IllegalStateException::class.java){store.activate(stage,{meta.writeText("new metadata")},{if(it==point)error("fault")})}
                assertEquals("old",File(root,"valid").readText());assertEquals("old metadata",meta.readText())
            }finally{base.deleteRecursively()}
        }
    }
    @Test fun successfulActivationRetainsOldGenerationAndRejectsForeignStage() {
        val base=Files.createTempDirectory("root-swap").toFile()
        try {
            val root=File(base,"rootfs").apply{mkdir()};File(root,"valid").writeText("old")
            val meta=File(base,"meta").apply{writeText("old")};val restore=File(base,"restore-points")
            val stage=File(base,"staging/install-id/rootfs").apply{mkdirs()};File(stage,"valid").writeText("new")
            val tx=RootfsActivationTransaction(root,restore,meta);tx.activate(stage,{meta.writeText("new")})
            assertEquals("new",File(root,"valid").readText());assertEquals("old",File(restore.listFiles()!!.single{it.isDirectory},"valid").readText());assertEquals("new",meta.readText())
            val foreign=File(base,"user-project").apply{mkdir()}
            assertThrows(IllegalArgumentException::class.java){tx.activate(foreign,{})}
            assertTrue(foreign.isDirectory)
        }finally{base.deleteRecursively()}
    }
}
