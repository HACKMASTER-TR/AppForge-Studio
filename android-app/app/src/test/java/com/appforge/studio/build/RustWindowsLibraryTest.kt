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

class RustWindowsLibraryTest {
    private fun binary():ByteArray {
        val data=ByteArray(1024);val b=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        b.putShort(0,0x5a4d);b.putInt(60,64);b.putInt(64,0x4550);b.putShort(68,0x8664.toShort());b.putShort(70,2);b.putShort(84,240);b.putShort(86,0x2002);b.putShort(88,0x20b);b.putInt(196,16);b.putInt(200,0x2000);b.putInt(204,256)
        b.putInt(340,0x1000);b.putInt(344,256);b.putInt(348,512);b.putInt(364,0x60000020)
        b.putInt(380,0x2000);b.putInt(384,256);b.putInt(388,768);b.putInt(404,0x40000040)
        b.putInt(788,1);b.putInt(792,1);b.putInt(796,0x2028);b.putInt(800,0x202c);b.putInt(804,0x2030);b.putInt(808,0x1000);b.putInt(812,0x2034);b.putShort(816,0)
        "appforge_windows_answer\u0000".toByteArray().copyInto(data,820)
        return data
    }
    private fun fixture(test:(File,ProjectDraft,RustPreparedProjects,File)->Unit) {
        val base=Files.createTempDirectory("rust-dll-test").toFile()
        try {
            val root=File(base,"source").apply{mkdir()};File(root,"src").mkdir();File(root,"src/lib.rs").writeText("// dll")
            File(root,"Cargo.toml").writeText("[package]\nname=\"fixture\"\nversion=\"0.1.0\"\n");File(root,"Cargo.lock").writeText("version=4\n")
            File(root,"appforge-rust-windows.json").writeText("""{"schema":2,"target":"x86_64-pc-windows-gnu","library":"fixture.dll","export":"appforge_windows_answer"}""")
            val store=File(base,"store").apply{mkdir()};val workspace=File(store,UUID.randomUUID().toString()).apply{mkdir()}
            val draft=ProjectDraft(importedFolder=root.path,sourceBuildEngine="windows-native",buildOutput="native-exe")
            ProjectDependencyEvidence.copyReviewedSource(draft,File(workspace,"source"));val prepared=File(workspace,"source");val directory=File(prepared,"appforge-rust").apply{mkdir()};val dll=File(directory,"fixture.dll").apply{writeBytes(binary())}
            File(directory,"artifacts.json").writeText(JSONObject().put("schema",2).put("target","x86_64-pc-windows-gnu").put("artifactKind","dll").put("file",dll.name).put("sourceSha256",RustProjectPolicy.sourceDigest(prepared)).put("sha256",RustProjectPolicy.digest(dll)).toString())
            test(base,draft,RustPreparedProjects(store),workspace)
        }finally{base.deleteRecursively()}
    }
    @Test fun libraryPublicationNeverBecomesApplicationReadiness()=fixture{_,draft,store,workspace->
        val prepared=store.publish(draft,workspace,ProjectDependencyEvidence.analyze(draft).fingerprint){true}
        assertTrue(RustProjectPolicy.isWindowsLibrary(prepared));assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.requirePackageable(prepared)}
        assertNull(store.restore(draft));assertNotNull(store.restoreLibrary(draft));assertFalse(File(draft.importedFolder!!,"appforge-rust").exists())
    }
    @Test fun libraryHashMustStillMatchAfterProcessReopen()=fixture{base,draft,store,workspace->
        store.publish(draft,workspace,ProjectDependencyEvidence.analyze(draft).fingerprint){true}
        val restored=RustPreparedProjects(File(base,"store")).restoreLibrary(draft)!!;restored.appendText("changed")
        assertNull(store.restoreLibrary(draft))
    }
    @Test fun approvalFailureCannotPublishLibrary()=fixture{_,draft,store,workspace->assertThrows(IllegalArgumentException::class.java){store.publish(draft,workspace,ProjectDependencyEvidence.analyze(draft).fingerprint){false}};assertNull(store.restoreLibrary(draft))}
    @Test fun exportedFunctionMustPointToCodeNotForwardedDll()=fixture{_,_,_,workspace->val dll=File(workspace,"source/appforge-rust/fixture.dll");val bytes=ByteBuffer.wrap(dll.readBytes()).order(ByteOrder.LITTLE_ENDIAN);bytes.putInt(808,0x2034);dll.writeBytes(bytes.array());assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.windowsDllExports(dll)}}
    @Test fun exportedOrdinalAndRvaAreBounded()=fixture{_,_,_,workspace->val dll=File(workspace,"source/appforge-rust/fixture.dll");val bytes=ByteBuffer.wrap(dll.readBytes()).order(ByteOrder.LITTLE_ENDIAN);bytes.putShort(816,1);dll.writeBytes(bytes.array());assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.windowsDllExports(dll)}}
    @Test fun dllCannotBePublishedAsExe()=fixture{_,_,_,workspace->val dll=File(workspace,"source/appforge-rust/fixture.dll");assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.requireWindowsPe(dll)}}
}
