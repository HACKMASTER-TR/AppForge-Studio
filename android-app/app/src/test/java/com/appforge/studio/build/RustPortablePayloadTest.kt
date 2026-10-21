package com.appforge.studio.build
import com.appforge.studio.model.ProjectDraft
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

class RustPortablePayloadTest {
 private fun project(block:(File,ProjectDraft)->Unit) {
  val root=Files.createTempDirectory("rust-portable").toFile()
  try {
   File(root,"src").mkdir();File(root,"src/lib.rs").writeText("// source");File(root,"Cargo.toml").writeText("[package]");File(root,"Cargo.lock").writeText("version=4")
   val dir=File(root,"appforge-rust").apply{mkdir()};val wasm=File(dir,"example.wasm").apply{writeBytes(byteArrayOf(0,97,115,109,1,0,0,0))}
   File(dir,"artifacts.json").writeText(JSONObject().put("schema",2).put("target","wasm32-unknown-unknown").put("sourceSha256",RustProjectPolicy.sourceDigest(root)).put("file",wasm.name).put("sha256",RustProjectPolicy.digest(wasm)).toString())
   block(root,ProjectDraft(importedFolder=root.path,sourceBuildEngine="webview-static",buildOutput="exe"))
  }finally{root.deleteRecursively()}
 }
 private fun zip(name:String,bytes:ByteArray):ByteArray = ByteArrayOutputStream().also{out->ZipOutputStream(out).use{it.putNextEntry(ZipEntry(name));it.write(bytes);it.closeEntry()}}.toByteArray()
 private fun verify(draft:ProjectDraft,bytes:ByteArray) {
  val file=Files.createTempFile("rust-webzip-",".zip").toFile()
  try{file.writeBytes(bytes);RustProjectPolicy.verifyWebArchive(draft,file)}finally{file.delete()}
 }
 @Test fun validPayloadAccepted()=project{root,draft->RustProjectPolicy.requireWebSite(draft,root);verify(draft,zip("appforge-rust/example.wasm",File(root,"appforge-rust/example.wasm").readBytes()))}
 @Test fun droppedPayloadBlocked()=project{_,draft->assertThrows(IllegalArgumentException::class.java){verify(draft,zip("index.html",byteArrayOf(1)))}}
 @Test fun alteredPayloadBlocked()=project{_,draft->assertThrows(IllegalArgumentException::class.java){verify(draft,zip("appforge-rust/example.wasm",byteArrayOf(1)))}}
 @Test fun pathTraversalBlockedWithoutExtracting()=project{_,draft->assertThrows(IllegalArgumentException::class.java){verify(draft,zip("../escape",byteArrayOf(1)))}}
 @Test fun siteWithoutPreparedWasmRejected()=project{root,draft->val empty=File(root,"empty").apply{mkdir()};assertThrows(IllegalArgumentException::class.java){RustProjectPolicy.requireWebSite(draft,empty)}}
 @Test fun sourceDriftInvalidatesArchive()=project{root,draft->File(root,"src/lib.rs").appendText("// drift");assertThrows(IllegalArgumentException::class.java){verify(draft,zip("appforge-rust/example.wasm",File(root,"appforge-rust/example.wasm").readBytes()))}}
 @Test fun windowsDriveAndAdsRejected()=project{_,draft->for(name in listOf("C:/escape","data:stream","folder/CON.txt","name."," "))assertThrows(IllegalArgumentException::class.java){verify(draft,zip(name,byteArrayOf(1)))}}
 @Test fun caseCollisionRejected()=project{root,draft->
  val out=ByteArrayOutputStream();ZipOutputStream(out).use{z->for(name in listOf("appforge-rust/example.wasm","APPFORGE-RUST/EXAMPLE.WASM")){z.putNextEntry(ZipEntry(name));z.write(File(root,"appforge-rust/example.wasm").readBytes());z.closeEntry()}}
  assertThrows(IllegalArgumentException::class.java){verify(draft,out.toByteArray())}
 }
 @Test fun centralDirectoryDuplicateRejected()=project{root,draft->
  val out=ByteArrayOutputStream();ZipOutputStream(out).use{z->for(name in listOf("example","invalid")){z.putNextEntry(ZipEntry("appforge-rust/$name.wasm"));z.write(if(name=="example")File(root,"appforge-rust/example.wasm").readBytes()else byteArrayOf(1));z.closeEntry()}}
  val bytes=out.toByteArray();val search="appforge-rust/invalid.wasm".toByteArray();val replace="appforge-rust/example.wasm".toByteArray()
  val at=(0..bytes.size-search.size).last{index->search.indices.all{bytes[index+it]==search[it]}};replace.copyInto(bytes,at)
  assertThrows(IllegalArgumentException::class.java){verify(draft,bytes)}
 }
 @Test fun ordinaryProjectsDoNotRequireRustArchive() {verify(ProjectDraft(),byteArrayOf(1,2))}
}
