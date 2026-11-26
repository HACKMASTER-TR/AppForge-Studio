package com.appforge.studio.build
import java.io.File
import java.nio.file.Files
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import com.appforge.studio.model.ProjectDraft

class RustPreparedProjectsTest {
    private fun fixture(test:(File,ProjectDraft,File,RustPreparedProjects)->Unit) {
        val root=Files.createTempDirectory("rust-prepared").toFile()
        try {
            val original=File(root,"original").apply{mkdir()}
            File(original,"Cargo.toml").writeText("[package]\nname=\"demo\"\nversion=\"0.1.0\"\n")
            File(original,"Cargo.lock").writeText("version=4\n")
            File(original,"src").mkdir();File(original,"src/lib.rs").writeText("pub fn demo() {}")
            File(original,"index.html").writeText("<html>demo</html>")
            val draft=ProjectDraft(importedFolder=original.path,startPage=File(original,"index.html").path,allowDependencyDownloads=true)
            val directory=File(root,"store").apply{mkdir()};val workspace=File(directory,UUID.randomUUID().toString()).apply{mkdir()}
            val source=File(workspace,"source");ProjectDependencyEvidence.copyReviewedSource(draft,source)
            val artifacts=File(source,"appforge-rust").apply{mkdir()};val wasm=File(artifacts,"demo.wasm").apply{writeBytes(byteArrayOf(0,97,115,109,1,0,0,0))}
            File(artifacts,"artifacts.json").writeText(JSONObject().put("schema",2).put("target","wasm32-unknown-unknown").put("sourceSha256",RustProjectPolicy.sourceDigest(source)).put("file",wasm.name).put("sha256",RustProjectPolicy.digest(wasm)).toString())
            test(root,draft,workspace,RustPreparedProjects(directory))
        }finally{root.deleteRecursively()}
    }
    @Test fun publishAndProcessReopenRevalidateWithoutInheritingNetworkPermission()=fixture {root,draft,workspace,store->
        val before=ProjectDependencyEvidence.analyze(draft).fingerprint
        val candidate=store.publish(draft,workspace,before){true}
        assertFalse(candidate.allowDependencyDownloads);assertEquals("index.html",candidate.startPage)
        val recovered=RustPreparedProjects(File(root,"store")).restore(draft)!!
        assertEquals(candidate.importedFolder,recovered.importedFolder)
        assertEquals(before,ProjectDependencyEvidence.analyze(draft).fingerprint)
        assertEquals(draft.packageName,recovered.packageName);assertEquals(draft.buildOutput,recovered.buildOutput)
    }
    @Test fun rejectedFinalApprovalCannotPublishPartialResult()=fixture {root,draft,workspace,store->
        assertThrows(IllegalArgumentException::class.java){store.publish(draft,workspace,ProjectDependencyEvidence.analyze(draft).fingerprint){false}}
        assertNull(store.restore(draft));assertFalse(File(root,"original/appforge-rust").exists())
    }
    @Test fun sourceDriftRejectsRecovery()=fixture {_,draft,workspace,store->
        store.publish(draft,workspace,ProjectDependencyEvidence.analyze(draft).fingerprint){true}
        File(draft.importedFolder!!,"src/lib.rs").appendText("// changed")
        assertNull(store.restore(draft))
    }
    @Test fun compiledCopyDriftRejectsRecovery()=fixture {_,draft,workspace,store->
        store.publish(draft,workspace,ProjectDependencyEvidence.analyze(draft).fingerprint){true}
        File(workspace,"source/appforge-rust/demo.wasm").appendText("corrupt")
        assertNull(store.restore(draft))
    }
    @Test fun unknownSchemaAndTraversalCannotRecover()=fixture {root,draft,workspace,store->
        val fingerprint=ProjectDependencyEvidence.analyze(draft).fingerprint
        store.publish(draft,workspace,fingerprint){true}
        val record=File(root,"store/$fingerprint.json")
        val original=JSONObject(record.readText())
        record.writeText(JSONObject(original.toString()).put("schema",999).toString());assertNull(store.restore(draft))
        record.writeText(JSONObject(original.toString()).put("workspace","../../original").toString());assertNull(store.restore(draft))
    }
    @Test fun recordSymlinkCannotBeFollowed()=fixture {root,draft,workspace,store->
        val fingerprint=ProjectDependencyEvidence.analyze(draft).fingerprint;store.publish(draft,workspace,fingerprint){true}
        val record=File(root,"store/$fingerprint.json");val outside=File(root,"foreign").apply{writeText(record.readText())}
        record.delete();Files.createSymbolicLink(record.toPath(),outside.toPath());assertNull(store.restore(draft))
        assertTrue(outside.isFile)
    }

    @Test fun rebuildClearsOnlyKnownGeneratedFilesInNewCopy()=fixture {_,draft,workspace,store->
        val original=ProjectDependencyEvidence.analyze(draft).fingerprint
        store.clearCopiedExport(workspace)
        assertFalse(File(workspace,"source/appforge-rust").exists())
        assertEquals(original,ProjectDependencyEvidence.analyze(draft).fingerprint)
    }
    @Test fun foreignExportFileBlocksEvenPrivateCopyCleanup()=fixture {_,draft,workspace,store->
        val foreign=File(workspace,"source/appforge-rust/user-script.js").apply{writeText("preserve")}
        assertThrows(IllegalArgumentException::class.java){store.clearCopiedExport(workspace)}
        assertEquals("preserve",foreign.readText());assertTrue(File(workspace,"source/appforge-rust/demo.wasm").isFile)
        assertFalse(File(draft.importedFolder!!,"appforge-rust").exists())
    }
}
