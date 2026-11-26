package com.appforge.studio.build

import com.appforge.studio.model.ProjectDraft
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RustPythonPackageTest {
    private fun fixture(test:(File,ProjectDraft,File)->Unit) {
        val base=Files.createTempDirectory("pyo3-test").toFile()
        try {
            val root=File(base,"source").apply{mkdir()};File(root,"src").mkdir();File(root,"src/lib.rs").writeText("// PyO3 fixture")
            File(root,"Cargo.toml").writeText("[package]\nname=\"native\"\nversion=\"0.1.0\"\n");File(root,"Cargo.lock").writeText("version=4\n")
            File(root,"appforge-rust-python.json").writeText("""{"schema":1,"module":"native","pythonVersion":"3.12","minApi":26}""")
            val data=ByteArray(512);val b=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            byteArrayOf(127,69,76,70,2,1).copyInto(data);b.putShort(16,3);b.putShort(18,183);b.putLong(32,64);b.putLong(40,128);b.putShort(54,56);b.putShort(56,1);b.putShort(58,64);b.putShort(60,3)
            b.putInt(64,1);b.putLong(96,512);b.putLong(104,512);b.putLong(112,16384)
            b.putInt(196,11);b.putLong(216,320);b.putLong(224,24);b.putInt(232,2);b.putLong(248,24)
            val text="\u0000PyInit_native\u0000".toByteArray();b.putInt(260,3);b.putLong(280,352);b.putLong(288,text.size.toLong());b.putInt(320,1);data[324]=0x12;b.putShort(326,1);text.copyInto(data,352)
            val directory=File(root,"appforge-rust").apply{mkdir()};val lib=File(directory,"native.so").apply{writeBytes(data)}
            File(directory,"artifacts.json").writeText(JSONObject().put("schema",2).put("target","aarch64-linux-android").put("integration","pyo3").put("pythonVersion","3.12").put("minApi",26).put("module","native").put("file",lib.name).put("sourceSha256",RustProjectPolicy.sourceDigest(root)).put("sha256",RustProjectPolicy.digest(lib)).toString())
            test(base,ProjectDraft(importedFolder=root.path,sourceBuildEngine="python-android"),lib)
        }finally{base.deleteRecursively()}
    }
    private fun archive(file:File,entries:Map<String,ByteArray>) {ZipOutputStream(file.outputStream()).use{zip->entries.forEach{(name,bytes)->zip.putNextEntry(ZipEntry(name));zip.write(bytes);zip.closeEntry()}}}
    @Test fun moduleStagesWithoutChangingOriginal()=fixture{base,draft,lib->val project=File(base,"build").apply{mkdir()};RustProjectPolicy.stagePythonModule(draft,project);assertArrayEquals(lib.readBytes(),File(project,"app/src/main/python/native.so").readBytes());assertFalse(File(draft.importedFolder!!,"app").exists())}
    @Test fun exactNestedApkAndAabPayloadRequired()=fixture{base,draft,lib->
        val inner=File(base,"app.imy");archive(inner,mapOf("native.so" to lib.readBytes()))
        for(kind in listOf("apk","aab")) {
            val file=File(base,"out.$kind");val path=(if(kind=="aab")"base/" else "")+"assets/chaquopy/app.imy"
            archive(file,mapOf(path to inner.readBytes()));RustProjectPolicy.verifyPythonPackage(draft,kind,file)
            archive(inner,mapOf("native.so" to byteArrayOf(1)));archive(file,mapOf(path to inner.readBytes()))
            assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.verifyPythonPackage(draft,kind,file)}
            archive(inner,mapOf("native.so" to lib.readBytes()))
        }
        assertFalse(base.listFiles()!!.any{it.name.startsWith(".pyo3-app-")})
    }
    @Test fun missingNestedModuleIsRejected()=fixture{base,draft,_->val inner=File(base,"app.imy");archive(inner,mapOf("other.so" to byteArrayOf(1)));val apk=File(base,"out.apk");archive(apk,mapOf("assets/chaquopy/app.imy" to inner.readBytes()));assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.verifyPythonPackage(draft,"apk",apk)}}
    @Test fun nestedTraversalRejected()=fixture{base,draft,lib->val inner=File(base,"app.imy");archive(inner,mapOf("native.so" to lib.readBytes(),"../foreign" to byteArrayOf(1)));val apk=File(base,"out.apk");archive(apk,mapOf("assets/chaquopy/app.imy" to inner.readBytes()));assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.verifyPythonPackage(draft,"apk",apk)}}
    @Test fun foreignPythonModulePreserved()=fixture{base,draft,_->val project=File(base,"build").apply{mkdir()};val file=File(project,"app/src/main/python/native.so");file.parentFile.mkdirs();file.writeText("foreign");assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.stagePythonModule(draft,project)};assertEquals("foreign",file.readText())}
    @Test fun unrelatedCAbiNotPyo3()=fixture{_,draft,lib->val b=lib.readBytes();"c_api_export\u0000".toByteArray().copyInto(b,353);lib.writeBytes(b);assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.requirePackageable(draft)}}
    @Test fun foreignExportCannotHideFromSourceFingerprint()=fixture{_,draft,lib->File(lib.parentFile,"foreign.py").writeText("ignored input attack");assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.requirePackageable(draft)}}
}
