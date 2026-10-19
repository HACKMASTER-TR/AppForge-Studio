package com.appforge.studio.build

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import javax.tools.ToolProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LifecycleCrashRecoveryTest {
    @Test fun realHostProcessDeathAtRenameAndCommitBoundariesRecoversWithoutDeletingUserData() {
        val base=Files.createTempDirectory("generation-process-death-").toFile()
        try {
            val classpath=listOf(RuntimeGenerationStore::class.java,kotlin.Unit::class.java,JSONObject::class.java,kotlinx.coroutines.Job::class.java)
                .map{File(it.protectionDomain.codeSource.location.toURI()).path}.distinct().joinToString(File.pathSeparator)
            val source=File(base,"GenerationCrashProbe.java").apply{writeText("""
                import java.io.File;
                import java.nio.file.Files;
                import com.appforge.studio.build.*;
                import kotlin.Unit;
                public class GenerationCrashProbe {
                  public static void main(String[] args) throws Exception {
                    RuntimeGenerationStore store=new RuntimeGenerationStore(new File(args[0]));
                    RuntimeGenerationStore.Prepared tx=store.prepare(OfflineModuleId.NODE_WEB,()->Long.MAX_VALUE,()->Unit.INSTANCE);
                    Files.writeString(new File(tx.getRoot(),"tool").toPath(),"new");
                    tx.commit(root->Unit.INSTANCE,point->{if(point.equals(args[1]))Runtime.getRuntime().halt(86);return Unit.INSTANCE;});
                    throw new AssertionError("Crash boundary was not reached");
                  }
                }
            """.trimIndent())}
            val compiler=ToolProvider.getSystemJavaCompiler();assertNotNull("host acceptance requires the build JDK",compiler)
            assertEquals(0,compiler.run(null,null,null,"-classpath",classpath,"-d",base.path,source.path))
            for(point in listOf("before-switch","old-moved","new-moved","committed-marker")) {
                val case=File(base,point).apply{mkdir()};val root=File(case,"rootfs").apply{mkdir()}
                File(root,"tool").writeText("old");File(root,"user-key").writeText("user bytes")
                val external=File(case,"project.html").apply{writeText("source")}
                val child=ProcessBuilder(File(System.getProperty("java.home"),"bin/java").path,"-cp",base.path+File.pathSeparator+classpath,"GenerationCrashProbe",root.path,point).redirectErrorStream(true).start()
                try {
                    assertTrue(child.waitFor(20,TimeUnit.SECONDS));assertEquals(child.inputStream.bufferedReader().readText(),86,child.exitValue())
                }finally{if(child.isAlive)child.destroyForcibly()}
                assertTrue(File(case,".module-generation.json").isFile)
                RuntimeGenerationStore(root).recover()
                assertFalse(File(case,".module-generation.json").exists())
                assertEquals(if(point=="committed-marker")"new" else "old",File(root,"tool").readText())
                assertEquals("user bytes",File(root,"user-key").readText());assertEquals("source",external.readText())
            }
        }finally{base.deleteRecursively()}
    }
    @Test fun corruptRollbackTargetBlocksRecoveryAndPreservesAllGenerations() {
        val base=Files.createTempDirectory("generation-bad-recovery-").toFile()
        try {
            val root=File(base,"rootfs").apply{mkdir()};File(root,"tool").writeText("old");File(root,"user-data").writeText("preserve")
            val store=RuntimeGenerationStore(root)
            assertThrows(IllegalArgumentException::class.java){store.prepare(OfflineModuleId.NODE_WEB).use{tx->
                File(tx.root,"tool").writeText("new")
                tx.commit(verify={},fault={point->if(point=="old-moved"){
                    val journal=JSONObject(File(base,".module-generation.json").readText())
                    File(File(base,journal.getString("previous")),"tool").writeText("corrupt")
                    error("process failure with damaged backup")
                }})
            }}
            val j=JSONObject(File(base,".module-generation.json").readText());val previous=File(base,j.getString("previous"))
            assertEquals("preserve",File(previous,"user-data").readText())
            File(previous,"tool").writeText("old");store.recover();assertEquals("old",File(root,"tool").readText())
        }finally{base.deleteRecursively()}
    }
    @Test fun activeEditsDuringStagingArePreservedAndCannotBeSilentlyOverwritten() {
        val base=Files.createTempDirectory("generation-drift-").toFile()
        try {
            val root=File(base,"rootfs").apply{mkdir()};File(root,"tool").writeText("old")
            assertThrows(IllegalArgumentException::class.java){RuntimeGenerationStore(root).prepare(OfflineModuleId.NODE_WEB).use{tx->
                File(tx.root,"tool").writeText("new");File(root,"user-new-project").writeText("source added while staging")
                tx.commit(verify={})
            }}
            assertEquals("source added while staging",File(root,"user-new-project").readText());assertEquals("old",File(root,"tool").readText())
            assertFalse(File(base,".module-generation.json").exists())
        }finally{base.deleteRecursively()}
    }
    @Test fun simultaneousMutationRequestsAndActivePreparationCannotOverlap() {
        val first=BuildModuleUsage.mutationLease()
        try {
            val rejected=java.util.concurrent.atomic.AtomicBoolean(false)
            val thread=Thread{try{BuildModuleUsage.mutationLease().use{}}catch(e:IllegalStateException){rejected.set(true)}}
            thread.start();thread.join(3000);assertFalse(thread.isAlive);assertTrue(rejected.get())
            assertThrows(IllegalStateException::class.java){BuildModuleUsage.preparationLease()}
        }finally{first.close();first.close()}
        BuildModuleUsage.preparationLease().use{assertThrows(IllegalStateException::class.java){BuildModuleUsage.mutationLease()}}
        assertNull(BuildModuleUsage.mutationBlockedReason())
    }
    @Test fun embeddedUnknownEvidenceAndMissingTrustedUpdateRemainDisabled() {
        for(id in OfflineModuleId.entries) {
            val absent=ModuleLifecyclePolicy.evaluate(ModuleLifecyclePolicy.Facts(id,true))
            assertFalse(absent.update.allowed);assertFalse(absent.remove.allowed);assertFalse(absent.rollback.allowed)
        }
        val fast=ModuleLifecyclePolicy.evaluate(ModuleLifecyclePolicy.Facts(OfflineModuleId.FAST_HTML,true,true,null,null,null))
        assertFalse(fast.update.allowed);assertFalse(fast.remove.allowed);assertFalse(fast.rollback.allowed)
    }
}
