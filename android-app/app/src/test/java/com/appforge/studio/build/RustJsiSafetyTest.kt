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

class RustJsiSafetyTest {
    private fun elf(symbol:String="appforge_jsi_answer",soname:String?="libappforge_jsi.so",needed:List<String> = listOf("libdl.so","libc.so")):ByteArray {
        val data=ByteArray(1536);val b=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        byteArrayOf(127,69,76,70,2,1).copyInto(data);b.putShort(16,3);b.putShort(18,183);b.putLong(32,64);b.putLong(40,128);b.putShort(54,56);b.putShort(56,1);b.putShort(58,64);b.putShort(60,4)
        b.putInt(64,1);b.putLong(96,1536);b.putLong(104,1536);b.putLong(112,16384)
        val strings=linkedMapOf<String,Int>();var end=561
        data[560]=0
        for(name in listOfNotNull(symbol,soname)+needed)if(name !in strings){strings[name]=end-560;val text=(name+"\u0000").toByteArray();text.copyInto(data,end);end+=text.size}
        b.putInt(196,11);b.putLong(216,512);b.putLong(224,24);b.putInt(232,2);b.putLong(248,24)
        b.putInt(260,3);b.putLong(280,560);b.putLong(288,(end-560).toLong());b.putInt(512,strings[symbol]!!);data[516]=0x12;b.putShort(518,1)
        b.putInt(324,6);b.putLong(344,1024);b.putInt(360,2);b.putLong(376,16)
        var pos=1024
        if(soname!=null){b.putLong(pos,14);b.putLong(pos+8,strings[soname]!!.toLong());pos+=16}
        for(name in needed){b.putLong(pos,1);b.putLong(pos+8,strings[name]!!.toLong());pos+=16}
        b.putLong(352,(pos-1024).toLong());return data
    }
    private fun fixture(test:(File,ProjectDraft,File)->Unit) {
        val base=Files.createTempDirectory("rust-jsi-test").toFile()
        try {
            val root=File(base,"source").apply{mkdir()};File(root,"src").mkdir();File(root,"src/lib.rs").writeText("// authored Rust")
            File(root,"Cargo.toml").writeText("[package]\nname=\"appforge_jsi\"\nversion=\"0.1.0\"\n");File(root,"Cargo.lock").writeText("version=4\n")
            File(root,"appforge-rust-jsi.json").writeText("""{"schema":1,"library":"appforge_jsi","bridgeLibrary":"appforge_rust_jsi","reactNative":"0.81","expoSdk":54,"minApi":26}""")
            File(root,"native").mkdir();for(name in listOf("RustJsi.cpp","RustJsiModule.java","RustJsiPackage.java","CMakeLists.txt"))File(root,"native/$name").writeText("// authored binding fixture")
            val dir=File(root,"appforge-rust").apply{mkdir()};val lib=File(dir,"libappforge_jsi.so").apply{writeBytes(elf())}
            File(dir,"artifacts.json").writeText(JSONObject().put("schema",2).put("target","aarch64-linux-android").put("integration","react-native-jsi").put("minApi",26).put("file",lib.name).put("sourceSha256",RustProjectPolicy.sourceDigest(root)).put("sha256",RustProjectPolicy.digest(lib)).toString())
            test(base,ProjectDraft(importedFolder=root.path,sourceBuildEngine="expo"),lib)
        }finally{base.deleteRecursively()}
    }
    @Test fun namedDependencyIsPortableAndAbsoluteBuildPathIsNot()=fixture{base,_,_->
        val good=File(base,"bridge.so").apply{writeBytes(elf(needed=listOf("libappforge_jsi.so","libjsi.so")))}
        assertTrue(RustProjectPolicy.androidDynamicLibraries(good).second.contains("libappforge_jsi.so"))
        good.writeBytes(elf(needed=listOf("/tmp/build/libappforge_jsi.so")))
        assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.androidDynamicLibraries(good)}
    }
    @Test fun missingSonameCannotBePreparedJsi()=fixture{_,draft,lib->
        lib.writeBytes(elf(soname=null));val record=File(lib.parentFile,"artifacts.json");record.writeText(JSONObject(record.readText()).put("sha256",RustProjectPolicy.digest(lib)).toString())
        assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.requireReactNativeJsi(draft)}
    }
    @Test fun bridgeAndRustBothRequiredAndIndependentlyChecked()=fixture{base,draft,lib->
        val file=File(base,"out.apk")
        fun write(bridge:ByteArray){ZipOutputStream(file.outputStream()).use{zip->for((name,bytes) in mapOf(lib.name to lib.readBytes(),"libappforge_rust_jsi.so" to bridge)){zip.putNextEntry(ZipEntry("lib/arm64-v8a/$name"));zip.write(bytes);zip.closeEntry()}}}
        write(elf("Java_com_appforge_rustjsi_RustJsiModule_installNative","libappforge_rust_jsi.so",listOf("libappforge_jsi.so","libjsi.so")));RustProjectPolicy.verifyJsiPackage(draft,"apk",file)
        write(elf());assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.verifyJsiPackage(draft,"apk",file)}
        assertFalse(base.listFiles()!!.any{it.name.startsWith(".jsi-elf-")})
    }
    @Test fun missingAuthoredRegistrationIsRejected()=fixture{_,draft,_->File(draft.importedFolder!!,"native/RustJsiPackage.java").delete();assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.requireReactNativeJsi(draft)}}
    @Test fun foreignStagedLibraryIsPreserved()=fixture{base,draft,_->val project=File(base,"build").apply{mkdir()};val file=File(project,"app/src/main/jniLibs/arm64-v8a/libappforge_jsi.so");file.parentFile.mkdirs();file.writeText("foreign");assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.stageReactNativeJsi(draft,project)};assertEquals("foreign",file.readText())}
}
