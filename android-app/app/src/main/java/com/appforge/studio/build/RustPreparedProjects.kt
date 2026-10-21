package com.appforge.studio.build

import com.appforge.studio.model.ProjectDraft
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONObject

/** Private prepared copies, never original-project mutation. Records do not grant
 * source execution/network consent and are revalidated before selection. */
internal class RustPreparedProjects(private val directory:File) {
    private fun safe(file:File) {
        RecoveryPathAnchor.check(directory)
        require(file.absoluteFile.toPath().normalize()==file.absoluteFile.toPath())
        require(file.canonicalFile.toPath().startsWith(directory.canonicalFile.toPath()))
        var current:File?=file.absoluteFile
        while(current!=null && current.toPath().startsWith(directory.absoluteFile.toPath())) {
            require(!Files.isSymbolicLink(current.toPath())){"Rust hazırlık kaydı symlink içeremez"};current=current.parentFile
        }
    }
    private fun record(fingerprint:String):File {require(fingerprint.matches(Regex("[a-f0-9]{64}")));return File(directory,"$fingerprint.json").also{safe(it)}}
    /** Rebuild only the two known generated files in a new private copy. Never
     * recursively delete an export or touch the original project/foreign files. */
    fun clearCopiedExport(workspace:File) {
        safe(workspace);require(workspace.name.matches(Regex("[a-f0-9-]{36}")))
        val directory=File(workspace,"source/appforge-rust");safe(directory)
        if(!directory.exists())return
        require(directory.isDirectory)
        val receipt=File(directory,"artifacts.json");safe(receipt)
        require(receipt.isFile && receipt.length() in 1..16384){"Önceki Rust çıktısı sahipliği doğrulanamadı; özgün dosyalar korunur"}
        val j=JSONObject(receipt.readText());require(j.getInt("schema") in 1..2 && j.getString("target")=="wasm32-unknown-unknown")
        val name=j.getString("file");require(name.matches(Regex("""[A-Za-z0-9_-]+\.wasm""")))
        val artifact=File(directory,name);safe(artifact)
        require(directory.listFiles()!!.map{it.name}.toSet()==setOf("artifacts.json",name)){"Rust çıktı klasöründe yabancı dosya var; kopyada bile silinmedi"}
        require(artifact.isFile && RustProjectPolicy.digest(artifact)==j.getString("sha256")){"Eski Rust çıktısı bozuk; otomatik yenileme reddedildi"}
        Files.delete(artifact.toPath());Files.delete(receipt.toPath());Files.delete(directory.toPath())
    }
    fun publish(original:ProjectDraft,workspace:File,expectedFingerprint:String,accept:()->Boolean):ProjectDraft {
        safe(workspace);require(workspace.name.matches(Regex("[a-f0-9-]{36}")))
        val prepared=File(workspace,"source");safe(prepared)
        require(ProjectDependencyEvidence.analyze(original).fingerprint==expectedFingerprint){"Rust hazırlığı sırasında özgün proje değişti"}
        val draft=preparedDraft(original,prepared)
        RustProjectPolicy.requirePackageable(draft)
        require(RustProjectPolicy.sourceDigest(File(original.importedFolder!!))==RustProjectPolicy.sourceDigest(prepared)){"Rust girdileri hazırlık sırasında değişti"}
        val bytes=JSONObject().put("schema",1).put("sourceFingerprint",expectedFingerprint).put("workspace",workspace.name)
            .put("preparedFingerprint",ProjectDependencyEvidence.analyze(draft).fingerprint)
            .put("state","PREPARED_NOT_ANDROID_ACCEPTED").toString().toByteArray(Charsets.UTF_8)
        val destination=record(expectedFingerprint);val temporary=File(destination.path+".tmp");safe(temporary)
        FileOutputStream(temporary).use{it.write(bytes);it.fd.sync()}
        require(accept()){"Rust hazırlık onayı/ağ doğrulaması değişti; kayıt yayımlanmadı"}
        Files.move(temporary.toPath(),destination.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
        return draft
    }
    fun restore(original:ProjectDraft):ProjectDraft?=runCatching {
        val fingerprint=ProjectDependencyEvidence.analyze(original).fingerprint
        val receipt=record(fingerprint);if(!receipt.exists())return@runCatching null
        require(receipt.isFile && receipt.length() in 1..4096)
        val j=JSONObject(receipt.readText());require(j.getInt("schema")==1 && j.getString("state")=="PREPARED_NOT_ANDROID_ACCEPTED" && j.getString("sourceFingerprint")==fingerprint)
        val id=j.getString("workspace");require(id.matches(Regex("[a-f0-9-]{36}")))
        val source=File(directory,"$id/source");safe(source)
        val draft=preparedDraft(original,source);RustProjectPolicy.requirePackageable(draft)
        require(ProjectDependencyEvidence.analyze(draft).fingerprint==j.getString("preparedFingerprint"))
        require(RustProjectPolicy.sourceDigest(File(original.importedFolder!!))==RustProjectPolicy.sourceDigest(source))
        draft
    }.getOrNull()
    companion object {
        fun preparedDraft(original:ProjectDraft,source:File):ProjectDraft {
            val root=File(original.importedFolder?:error("Rust kaynağı yok")).canonicalFile
            val start=original.startPage?.let{path->val f=File(path);if(f.isAbsolute){require(f.canonicalFile.toPath().startsWith(root.toPath()));f.canonicalFile.relativeTo(root).invariantSeparatorsPath}else path}
            return original.copy(importedFolder=source.path,startPage=start,allowDependencyDownloads=false)
        }
    }
}
