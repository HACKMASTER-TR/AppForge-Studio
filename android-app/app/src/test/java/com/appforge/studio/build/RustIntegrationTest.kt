package com.appforge.studio.build

import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import com.appforge.studio.model.ProjectDraft
import com.appforge.studio.model.SourceMode

class RustIntegrationTest {
    private fun project(block:(File)->Unit) {
        val root=Files.createTempDirectory("rust-policy").toFile()
        try {
            File(root,"Cargo.toml").writeText("[package]\nname=\"demo\"\nversion=\"0.1.0\"\n")
            File(root,"Cargo.lock").writeText("version=4\n")
            File(root,"src").mkdir();File(root,"src/lib.rs").writeText("pub fn demo() {}")
            block(root)
        } finally {root.deleteRecursively()}
    }
    private fun draft(root:File)=ProjectDraft(sourceMode=SourceMode.LOCAL,importedFolder=root.path,sourceBuildEngine="webview-static")
    private fun receipt(root:File) {
        val dir=File(root,"appforge-rust").apply{mkdir()};val wasm=File(dir,"demo.wasm").apply{writeBytes(byteArrayOf(0,97,115,109,1,0,0,0))}
        File(dir,"artifacts.json").writeText(JSONObject().put("schema",2).put("target","wasm32-unknown-unknown").put("sourceSha256",RustProjectPolicy.sourceDigest(root)).put("file",wasm.name).put("sha256",RustProjectPolicy.digest(wasm)).toString())
    }
    @Test fun rustModuleOnlyNeedsLinuxAndDoesNotPullAndroidOrNode() {
        assertEquals(listOf(OfflineModuleId.LINUX_RUNTIME,OfflineModuleId.RUST),OfflineModules.resolve(setOf(OfflineModuleId.RUST)))
    }
    @Test fun unpreparedRustFailsBeforeSilentHtmlPackaging()=project { root ->
        assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.requirePackageable(draft(root))}
        assertTrue(OfflineModules.forProject(draft(root)).modules.contains(OfflineModuleId.RUST))
    }
    @Test fun sourceAndArtifactDriftInvalidatePrebuiltWasm()=project { root ->
        receipt(root);RustProjectPolicy.requirePackageable(draft(root))
        File(root,"src/lib.rs").appendText("//changed")
        assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.requirePackageable(draft(root))}
    }
    @Test fun corruptedWasmRejected()=project { root ->
        receipt(root);File(root,"appforge-rust/demo.wasm").appendText("tamper")
        assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.requirePackageable(draft(root))}
    }
    @Test fun nativeRustIsNotAdvertisedAsIntegrated()=project {root ->
        receipt(root)
        assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.requirePackageable(draft(root).copy(sourceBuildEngine="android-gradle"))}
    }
    @Test fun rustSourceSymlinkRejected()=project {root ->
        Files.createSymbolicLink(File(root,"src/escape.rs").toPath(),File(root,"Cargo.lock").toPath())
        assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.sourceDigest(root)}
    }
    @Test fun ordinaryWorkspaceAliasIsNeverWhitelisted() {
        val base=Files.createTempDirectory("rust-path").toFile()
        try {
            val real=File(base,"real").apply{mkdir()};val link=File(base,"link")
            Files.createSymbolicLink(link.toPath(),real.toPath())
            val candidate=File(link,"missing/rootfs")
            assertEquals(candidate,RecoveryPathAnchor.androidPlatformPath(candidate))
            assertThrows(IllegalArgumentException::class.java){RecoveryPathAnchor.check(candidate)}
        }finally{base.deleteRecursively()}
    }
    @Test fun plainRecoveryPathAndTraversal() {
        val base=Files.createTempDirectory("rust-plain").toFile()
        try {RecoveryPathAnchor.check(File(base,"missing/rootfs"));assertThrows(IllegalArgumentException::class.java){RecoveryPathAnchor.androidPlatformPath(File(base,"x/../rootfs"))}}
        finally{base.deleteRecursively()}
    }

    @Test fun failedRustGenerationPreservesOldToolsAndUserData() {
        val base=Files.createTempDirectory("rust-generation").toFile()
        try {
            val root=File(base,"rootfs").apply{mkdir()}
            File(root,"user-project").writeText("keep")
            File(root,"opt/appforge-device/rust-1.85.1/bin").mkdirs()
            File(root,"opt/appforge-device/rust-1.85.1/bin/rustc").writeText("old fixture")
            RuntimeGenerationStore(root).prepare(OfflineModuleId.RUST).use { staged ->
                File(staged.root,"opt/appforge-device/rust-1.85.1/bin/rustc").writeText("partial fixture")
                // Closing an uncommitted stage cannot replace an active toolchain.
            }
            assertEquals("old fixture",File(root,"opt/appforge-device/rust-1.85.1/bin/rustc").readText())
            assertEquals("keep",File(root,"user-project").readText())
        }finally{base.deleteRecursively()}
    }
    @Test fun rustOwnershipDetectsForeignChildrenAndBlocksRemoval() {
        val base=Files.createTempDirectory("rust-owned").toFile()
        try {
            val root=File(base,"rootfs").apply{mkdir()};File(root,"user-project").writeText("keep")
            BaseRuntimeOwnership.captureFresh(root)
            RuntimeGenerationStore(root).prepare(OfflineModuleId.RUST).use { tx ->
                File(tx.root,"opt/appforge-device/rust-1.85.1/bin").mkdirs()
                File(tx.root,"opt/appforge-device/rust-1.85.1/bin/rustc").writeText("owned fixture")
                ModuleOwnershipStore(tx.root).record(OfflineModuleId.RUST,tx.previousRootName,RuntimeGenerationStore(root).snapshot(),RuntimeGenerationStore(tx.root).snapshot(),root)
                tx.commit(verify={})
            }
            assertTrue(ModuleOwnershipStore(root).verifyNamespace(OfflineModuleId.RUST))
            File(root,"opt/appforge-device/rust-1.85.1/bin/foreign-key").writeText("keep")
            assertFalse(ModuleOwnershipStore(root).verifyNamespace(OfflineModuleId.RUST))
            assertNotNull(ModuleOwnershipStore(root).safeRemovalReason(OfflineModuleId.RUST))
            assertEquals("keep",File(root,"user-project").readText())
        }finally{base.deleteRecursively()}
    }

    @Test fun nodeOutputReceivesExactRustWasmWithoutJavascriptInjection()=project {root ->
        receipt(root);val build=File(root,"target/build-copy").apply{mkdirs()};val site=File(build,"dist").apply{mkdir()}
        File(site,"index.html").writeText("<html>authored JS only</html>")
        val draft=draft(root).copy(sourceBuildEngine="node-web")
        RustProjectPolicy.stageNodeWebPayload(draft,build,site)
        assertEquals(RustProjectPolicy.digest(File(root,"appforge-rust/demo.wasm")),RustProjectPolicy.digest(File(site,"appforge-rust/demo.wasm")))
        assertEquals("<html>authored JS only</html>",File(site,"index.html").readText())
        RustProjectPolicy.stageNodeWebPayload(draft,build,site) // same verified output is reusable
    }
    @Test fun conflictingNodeOutputAndForeignDirectoriesArePreserved()=project {root ->
        receipt(root);val build=File(root,"target/build-copy").apply{mkdirs()};val site=File(build,"dist").apply{mkdir()}
        val foreign=File(site,"appforge-rust").apply{mkdir()};File(foreign,"demo.wasm").writeText("foreign")
        assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.stageNodeWebPayload(draft(root).copy(sourceBuildEngine="node-web"),build,site)}
        assertEquals("foreign",File(foreign,"demo.wasm").readText())
        assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.stageNodeWebPayload(draft(root),build,root)}
    }

    @Test fun nonRustDataInputsAndBuildScriptsInvalidateWasmButTargetCacheDoesNot()=project {root ->
        File(root,"data.bin").writeText("input");receipt(root)
        val before=RustProjectPolicy.sourceDigest(root)
        File(root,"target/debug").mkdirs();File(root,"target/debug/cache").writeText("generated")
        assertEquals(before,RustProjectPolicy.sourceDigest(root))
        File(root,"data.bin").writeText("changed")
        assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.requirePackageable(draft(root))}
    }
    @Test fun preparedNodeRetainsSeparateDependencyPreparationAndRejectsDrift()=project {root ->
        receipt(root);val node=draft(root).copy(sourceBuildEngine="node-web")
        assertTrue(RustProjectPolicy.canPrepareNodeDependencies(node))
        File(root,"src/lib.rs").appendText("// stale")
        assertFalse(RustProjectPolicy.canPrepareNodeDependencies(node))
    }
}
