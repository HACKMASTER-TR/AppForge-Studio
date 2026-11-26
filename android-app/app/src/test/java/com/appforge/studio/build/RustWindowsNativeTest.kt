package com.appforge.studio.build

import com.appforge.studio.model.ProjectDraft
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RustWindowsNativeTest {
    private fun fixture(test:(File,ProjectDraft,File)->Unit) {
        val base=Files.createTempDirectory("rust-windows-test").toFile()
        try {
            val root=File(base,"source").apply{mkdir()};File(root,"src").mkdir()
            File(root,"src/main.rs").writeText("fn main() {}")
            File(root,"Cargo.toml").writeText("[package]\nname=\"demo\"\nversion=\"0.1.0\"\n")
            File(root,"Cargo.lock").writeText("version=4\n")
            File(root,"appforge-rust-windows.json").writeText("""{"schema":1,"target":"x86_64-pc-windows-gnu","executable":"demo.exe"}""")
            val dir=File(root,"appforge-rust").apply{mkdir()};val exe=File(dir,"demo.exe")
            val bytes=ByteArray(512);val b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            b.putShort(0,0x5a4d);b.putInt(60,64);b.putInt(64,0x4550);b.putShort(68,0x8664.toShort());b.putShort(70,1);b.putShort(84,112);b.putShort(86,2);b.putShort(88,0x20b);b.putInt(216,32);b.putInt(220,300)
            exe.writeBytes(bytes)
            File(dir,"artifacts.json").writeText(JSONObject().put("schema",2).put("target","x86_64-pc-windows-gnu").put("file","demo.exe").put("sha256",RustProjectPolicy.digest(exe)).put("sourceSha256",RustProjectPolicy.sourceDigest(root)).toString())
            test(base,ProjectDraft(importedFolder=root.path,sourceBuildEngine="windows-native",buildOutput="native-exe"),exe)
        }finally{base.deleteRecursively()}
    }
    @Test fun nativeExeStagesOnlyExactReviewedBytes()=fixture {base,draft,exe->
        RustProjectPolicy.requirePackageable(draft)
        val out=RustProjectPolicy.stageWindowsNative(draft,File(base,"build").apply{mkdir()})
        assertArrayEquals(exe.readBytes(),out.readBytes());assertFalse(File(draft.importedFolder!!,"windows-native-out").exists())
    }
    @Test fun foreignOutputIsPreserved()=fixture {base,draft,_->
        val root=File(base,"build").apply{mkdir()};val file=File(root,"windows-native-out/demo.exe");file.parentFile.mkdirs();file.writeText("foreign")
        assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.stageWindowsNative(draft,root)};assertEquals("foreign",file.readText())
    }
    @Test fun sourceDriftRequiresRebuild()=fixture{_,draft,_->File(draft.importedFolder!!,"src/main.rs").appendText("// drift");assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.requirePackageable(draft)}}
    @Test fun corruptHashCannotPublish()=fixture{_,draft,exe->exe.appendText("changed");assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.requirePackageable(draft)}}
    @Test fun dllCannotMasqueradeAsExe()=fixture{_,_,exe->val b=exe.readBytes();b[87]=0x20;exe.writeBytes(b);assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.requireWindowsPe(exe)}}
    @Test fun peSectionBoundsAreValidated()=fixture{_,_,exe->val b=ByteBuffer.wrap(exe.readBytes()).order(ByteOrder.LITTLE_ENDIAN);b.putInt(216,1000);exe.writeBytes(b.array());assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.requireWindowsPe(exe)}}
    @Test fun otherOutputsCannotReuseWindowsReceipt()=fixture{_,draft,_->assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.requirePackageable(draft.copy(buildOutput="apk"))}}
    @Test fun outputDirectorySymlinkIsRejected()=fixture{base,draft,_->val workspace=File(base,"build").apply{mkdir()};val foreign=File(base,"foreign").apply{mkdir()};Files.createSymbolicLink(File(workspace,"windows-native-out").toPath(),foreign.toPath());assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.stageWindowsNative(draft,workspace)};assertTrue(foreign.listFiles()!!.isEmpty())}
    @Test fun singlePreparationDecisionPreservesJniAndWindows() {
        assertEquals("aarch64-linux-android",RustProjectPolicy.preparationTarget(ProjectDraft(sourceBuildEngine="android-gradle")))
        assertEquals("x86_64-pc-windows-gnu",RustProjectPolicy.preparationTarget(ProjectDraft(sourceBuildEngine="windows-native")))
        assertEquals("wasm32-unknown-unknown",RustProjectPolicy.preparationTarget(ProjectDraft(sourceBuildEngine="node-web")))
        assertThrows(IllegalStateException::class.java){RustProjectPolicy.preparationTarget(ProjectDraft(sourceBuildEngine="unknown"))}
    }
}
