package com.appforge.studio.build

import com.appforge.studio.terminal.RootfsActivationTransaction
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class RecoveryAnchorAuditTest {
    private fun recovery(base:File,kind:Int):()->Unit = when(kind) {
        0 -> { {RuntimeGenerationStore(File(base,"rootfs")).recover()} }
        1 -> { {RuntimeArchiveRemoval(File(base,"runtime-v3")).recover()} }
        else -> { {RootfsActivationTransaction(File(base,"rootfs"),File(base,"restore"),File(base,"meta")).recover()} }
    }
    private val names=listOf(".module-generation.json",".runtime-removal-v5.json",".rootfs-activation.json")
    @Test fun originalSymlinkParentRejectedWithoutDeletingAnyForeignFixture() {
        for(kind in 0..2) {
            val base=Files.createTempDirectory("audit-anchor").toFile()
            try {
                val foreign=File(base,"foreign").apply{mkdir()}
                File(foreign,"rootfs").mkdir();File(foreign,"runtime-v3").mkdir()
                val tmp=File(foreign,names[kind]+".tmp").apply{writeText("synthetic-foreign-owned")}
                val before=VerifiedModuleDownload.sha(tmp)
                val alias=File(base,"alias");Files.createSymbolicLink(alias.toPath(),foreign.toPath())
                assertThrows(Exception::class.java){recovery(alias,kind)()}
                assertEquals(before,VerifiedModuleDownload.sha(tmp))
            } finally {base.deleteRecursively()}
        }
    }
    @Test fun missingRootNoJournalIsLegalButMalformedJournalCannotCleanSidecars() {
        for(kind in 0..2) {
            val base=Files.createTempDirectory("audit-missing-root").toFile()
            try {
                recovery(base,kind)() // A rename gap or first install must remain legal.
                val journal=File(base,names[kind]);val tmp=File(base,names[kind]+".tmp").apply{writeText("keep-on-invalid")}
                for(bad in listOf("broken-json","{\"schema\":99}","{\"schema\":1,\"state\":\"INVENTED\"}")) {
                    journal.writeText(bad)
                    assertThrows(Exception::class.java){recovery(base,kind)()}
                    assertEquals("keep-on-invalid",tmp.readText());assertEquals(bad,journal.readText())
                }
            } finally {base.deleteRecursively()}
        }
    }
    @Test fun incompatibleGenerationRolesAndCopyingRenameStatePreserveAllSidecars() {
        val base=Files.createTempDirectory("audit-invalid-transition").toFile()
        try {
            val root=File(base,"rootfs").apply{mkdir()};File(root,"user-marker").writeText("keep")
            val suffix="00000000-0000-0000-0000-000000000000"
            val old=File(base,".v3-previous-$suffix").apply{mkdir()};File(old,"marker").writeText("old-keep")
            val journal=File(base,".module-generation.json");val tmp=File(base,".module-generation.json.tmp").apply{writeText("keep-control")}
            val record=org.json.JSONObject().put("schema",2).put("active","rootfs").put("stage",".v3-stage-$suffix")
                .put("previous",old.name).put("module","NODE_WEB").put("state","COPYING").put("previousDigest","a".repeat(64))
            for(candidate in listOf(record,org.json.JSONObject(record.toString()).put("stage",old.name))) {
                journal.writeText(candidate.toString())
                assertThrows(Exception::class.java){RuntimeGenerationStore(root).recover()}
                assertEquals(candidate.toString(),journal.readText());assertEquals("keep-control",tmp.readText())
                assertEquals("keep",File(root,"user-marker").readText());assertEquals("old-keep",File(old,"marker").readText())
            }
        } finally {base.deleteRecursively()}
    }
    @Test fun symlinkSidecarRejectedBeforeAnyOtherSidecarCleanup() {
        for(kind in 0..2) {
            val base=Files.createTempDirectory("audit-sidecar").toFile()
            try {
                val foreign=File(base,"user-data").apply{writeText("keep")}
                Files.createSymbolicLink(File(base,names[kind]+".tmp").toPath(),foreign.toPath())
                assertThrows(Exception::class.java){recovery(base,kind)()}
                assertEquals("keep",foreign.readText())
            } finally {base.deleteRecursively()}
        }
    }
}
