package com.appforge.studio.build
import com.appforge.studio.model.ProjectDraft
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RustUniversalProjectTest {
    private fun fixture(test:(File,ProjectDraft)->Unit) {
        val base=Files.createTempDirectory("rust-universal-test").toFile()
        try {
            val root=File(base,"original").apply{mkdir()}
            File(root,"appforge.universal.json").writeText("""{"format":"appforge-universal","formatVersion":1,"targets":{"android":{"root":"android"},"windows":{"root":"web"}}}""")
            for(name in listOf("android","web")) {
                val child=File(root,name).apply{mkdir()};File(child,"src").mkdir();File(child,"src/lib.rs").writeText("// target $name")
                File(child,"Cargo.toml").writeText("[package]\nname=\"fixture\"\nversion=\"0.1.0\"\n");File(child,"Cargo.lock").writeText("version=4\n")
            }
            File(root,"android/app").mkdir();File(root,"android/app/build.gradle.kts").writeText("// module")
            File(root,"android/appforge-rust-android.json").writeText("""{"schema":1,"module":"app","library":"fixture","minApi":26}""")
            File(root,"web/index.html").writeText("<html>Rust</html>")
            test(base,ProjectDraft(importedFolder=root.path,sourceBuildEngine="universal-cross-platform",buildOutput="all",allowDependencyDownloads=true))
        }finally{base.deleteRecursively()}
    }
    private fun prepare(root:File) {
        val web=File(root,"web");val wd=File(web,"appforge-rust").apply{mkdir()};val wasm=File(wd,"fixture.wasm").apply{writeBytes(byteArrayOf(0,97,115,109,1,0,0,0))}
        File(wd,"artifacts.json").writeText(JSONObject().put("schema",2).put("target","wasm32-unknown-unknown").put("sourceSha256",RustProjectPolicy.sourceDigest(web)).put("file",wasm.name).put("sha256",RustProjectPolicy.digest(wasm)).toString())
        val android=File(root,"android");val ad=File(android,"appforge-rust").apply{mkdir()};val lib=File(ad,"libfixture.so")
        val data=ByteArray(512);val b=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        byteArrayOf(127,69,76,70,2,1).copyInto(data);b.putShort(16,3);b.putShort(18,183);b.putLong(32,64);b.putLong(40,128);b.putShort(54,56);b.putShort(56,1);b.putShort(58,64);b.putShort(60,3)
        b.putInt(64,1);b.putLong(96,512);b.putLong(104,512);b.putLong(112,16384);b.putInt(196,11);b.putLong(216,320);b.putLong(224,24);b.putInt(232,2);b.putLong(248,24)
        val symbol="Java_com_example_Test_answer";val text=("\u0000"+symbol+"\u0000").toByteArray();b.putInt(260,3);b.putLong(280,352);b.putLong(288,text.size.toLong());b.putInt(320,1);data[324]=0x12;b.putShort(326,1);text.copyInto(data,352);lib.writeBytes(data)
        File(ad,"artifacts.json").writeText(JSONObject().put("schema",2).put("target","aarch64-linux-android").put("module","app").put("minApi",26).put("file",lib.name).put("sourceSha256",RustProjectPolicy.sourceDigest(android)).put("sha256",RustProjectPolicy.digest(lib)).put("jniExports",org.json.JSONArray(listOf(symbol))).toString())
    }
    @Test fun selectedTargetsUseSeparateExistingEngines()=fixture {_,draft->val projects=RustProjectPolicy.preparationProjects(draft);assertEquals(listOf("android","web"),projects.map{it.first});assertEquals(listOf("android-gradle","webview-static"),projects.map{it.second.sourceBuildEngine});assertEquals(listOf("both","exe"),projects.map{it.second.buildOutput});assertTrue(projects.none{it.second.allowDependencyDownloads})}
    @Test fun anyMissingTargetBlocksWholeProject()=fixture {_,draft->assertTrue(RustProjectPolicy.hasRust(draft));assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.requirePackageable(draft)};assertNotNull(OfflineModules.forProject(draft).error)}
    @Test fun preparedCombinedCopyPreservesProjectAndRevalidatesOnReopen()=fixture {base,draft->
        val store=File(base,"store").apply{mkdir()};val workspace=File(store,UUID.randomUUID().toString()).apply{mkdir()};val source=File(workspace,"source")
        ProjectDependencyEvidence.copyReviewedSource(draft,source);prepare(source)
        val prepared=RustPreparedProjects(store).publish(draft,workspace,ProjectDependencyEvidence.analyze(draft).fingerprint){true}
        assertEquals("universal-cross-platform",prepared.sourceBuildEngine);assertEquals("all",prepared.buildOutput);assertFalse(prepared.allowDependencyDownloads)
        assertNotNull(RustPreparedProjects(store).restore(draft));assertFalse(File(draft.importedFolder!!,"android/appforge-rust").exists())
        File(source,"web/appforge-rust/fixture.wasm").appendText("tamper");assertNull(RustPreparedProjects(store).restore(draft))
    }
    @Test fun nestedTargetCacheNotSourceInputButNativeCodeIs()=fixture {_,draft->
        val root=File(draft.importedFolder!!);val before=RustProjectPolicy.sourceDigest(root);val plan=ProjectDependencyEvidence.analyze(draft).fingerprint
        File(root,"android/target").mkdir();File(root,"android/target/cache").writeText("generated")
        assertEquals(before,RustProjectPolicy.sourceDigest(root));assertEquals(plan,ProjectDependencyEvidence.analyze(draft).fingerprint)
        File(root,"android/src/lib.rs").appendText("// changed");assertNotEquals(before,RustProjectPolicy.sourceDigest(root))
    }
    @Test fun unselectedTargetDoesNotRequireRustPreparation()=fixture {_,draft->assertEquals(listOf("web"),RustProjectPolicy.preparationProjects(draft.copy(buildOutput="exe")).map{it.first})}
    @Test fun traversalAndSymlinkAreRejected()=fixture {base,draft->
        val root=File(draft.importedFolder!!);val manifest=File(root,"appforge.universal.json");val j=JSONObject(manifest.readText());j.getJSONObject("targets").getJSONObject("windows").put("root","../foreign");manifest.writeText(j.toString());assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.preparationProjects(draft)}
        j.getJSONObject("targets").getJSONObject("windows").put("root","linked");manifest.writeText(j.toString());val foreign=File(base,"foreign").apply{mkdir()};Files.createSymbolicLink(File(root,"linked").toPath(),foreign.toPath());assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.preparationProjects(draft)};assertTrue(foreign.listFiles()!!.isEmpty())
    }
    @Test fun quotedTargetPathIsDataNotShellCode() {
        val relative="web'\$(printf unsafe) ; test"
        val quoted=RustProjectPolicy.shellProjectPath(relative)
        val process=ProcessBuilder("/bin/sh","-c","printf '%s' "+quoted).start()
        assertEquals("/workspace/source/$relative",process.inputStream.bufferedReader().readText());assertEquals(0,process.waitFor())
        assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.shellProjectPath("../escape")}
    }
    @Test fun rootWorkspaceNeverSilentlyFallsBack()=fixture {_,draft->File(draft.importedFolder!!,"Cargo.toml").writeText("[workspace]");assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.requirePackageable(draft)}}
}
