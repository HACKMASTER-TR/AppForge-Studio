package com.appforge.studio.build

import com.appforge.studio.model.ProjectDraft
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RustAndroidJniTest {
    private fun elf(symbol:String="Java_com_example_Test_answer",alignment:Long=16384,defined:Boolean=true):ByteArray {
        val data=ByteArray(512);val b=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        byteArrayOf(127,69,76,70,2,1).copyInto(data);b.putShort(16,3);b.putShort(18,183)
        b.putLong(32,64);b.putLong(40,128);b.putShort(54,56);b.putShort(56,1);b.putShort(58,64);b.putShort(60,3)
        b.putInt(64,1);b.putLong(64+32,512);b.putLong(64+40,512);b.putLong(64+48,alignment)
        b.putInt(192+4,11);b.putLong(192+24,320);b.putLong(192+32,24);b.putInt(192+40,2);b.putLong(192+56,24)
        val strings=("\u0000"+symbol+"\u0000").toByteArray();b.putInt(256+4,3);b.putLong(256+24,352);b.putLong(256+32,strings.size.toLong())
        b.putInt(320,1);data[324]=0x12;b.putShort(326,if(defined)1 else 0);strings.copyInto(data,352);return data
    }
    private fun project(block:(File,ProjectDraft,File)->Unit) {
        val base=Files.createTempDirectory("rust-jni-").toFile()
        try {
            val root=File(base,"source").apply{mkdir()};File(root,"src").mkdir();File(root,"src/lib.rs").writeText("// fixture")
            File(root,"Cargo.toml").writeText("[package]\nname=\"fixture\"\nversion=\"0.1.0\"\n");File(root,"Cargo.lock").writeText("version=4\n")
            File(root,"app").mkdir();File(root,"app/build.gradle.kts").writeText("// explicit app module")
            File(root,"appforge-rust-android.json").writeText("""{"schema":1,"module":"app","library":"fixture","minApi":26}""")
            val dir=File(root,"appforge-rust").apply{mkdir()};val lib=File(dir,"libfixture.so").apply{writeBytes(elf())}
            File(dir,"artifacts.json").writeText(JSONObject().put("schema",2).put("target","aarch64-linux-android").put("minApi",26).put("module","app").put("file",lib.name).put("sourceSha256",RustProjectPolicy.sourceDigest(root)).put("sha256",RustProjectPolicy.digest(lib)).put("jniExports",org.json.JSONArray(listOf("Java_com_example_Test_answer"))).toString())
            block(base,ProjectDraft(importedFolder=root.path,sourceBuildEngine="android-gradle"),lib)
        }finally{base.deleteRecursively()}
    }
    @Test fun nativeReceiptAndPrivateStaging()=project{base,draft,lib->
        RustProjectPolicy.requirePackageable(draft);val copy=File(base,"build-copy").apply{mkdir()}
        RustProjectPolicy.stageAndroidJni(draft,copy)
        assertArrayEquals(lib.readBytes(),File(copy,"app/src/main/jniLibs/arm64-v8a/${lib.name}").readBytes())
        assertFalse(File(draft.importedFolder!!,"app/src/main/jniLibs").exists())
    }
    @Test fun foreignJniFilePreserved()=project{base,draft,lib->
        val target=File(base,"copy/app/src/main/jniLibs/arm64-v8a/${lib.name}");target.parentFile.mkdirs();target.writeText("foreign")
        assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.stageAndroidJni(draft,File(base,"copy"))};assertEquals("foreign",target.readText())
    }
    @Test fun apkAndAabMustContainExactReviewedBytes()=project{base,draft,lib->
        for(kind in listOf("apk","aab")) {
            val zip=File(base,"output.$kind");val path=(if(kind=="aab")"base/" else "")+"lib/arm64-v8a/${lib.name}"
            ZipOutputStream(zip.outputStream()).use{it.putNextEntry(ZipEntry(path));it.write(lib.readBytes());it.closeEntry()}
            RustProjectPolicy.verifyAndroidJniPackage(draft,kind,zip)
            ZipOutputStream(zip.outputStream()).use{it.putNextEntry(ZipEntry(path));it.write(byteArrayOf(1));it.closeEntry()}
            assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.verifyAndroidJniPackage(draft,kind,zip)}
        }
    }
    @Test fun windowsOutputCannotReuseJniReceipt()=project{_,draft,_->assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.requirePackageable(draft.copy(buildOutput="exe"))}}
    @Test fun nativeSourceDriftBlocked()=project{_,draft,_->File(draft.importedFolder!!,"app/build.gradle.kts").appendText("// changed");assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.requirePackageable(draft)}}
    @Test fun minApiMismatchBlocked()=project{_,draft,_->assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.requirePackageable(draft.copy(minSdk=25))}}
    @Test fun noJniSymbolRejected()=project{_,_,lib->lib.writeBytes(elf("c_api_only"));assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.androidJniExports(lib)}}
    @Test fun undefinedJniRejected()=project{_,_,lib->lib.writeBytes(elf(defined=false));assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.androidJniExports(lib)}}
    @Test fun fourKiBAlignmentRejected()=project{_,_,lib->lib.writeBytes(elf(alignment=4096));assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.androidJniExports(lib)}}
    @Test fun workspaceLinkRejected()=project{base,draft,_->
        val outside=File(base,"outside").apply{mkdir()};val copy=File(base,"copy").apply{mkdir()};Files.createSymbolicLink(File(copy,"app").toPath(),outside.toPath())
        assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.stageAndroidJni(draft,copy)};assertTrue(outside.listFiles()!!.isEmpty())
    }
}
