package com.appforge.studio.build

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONObject

/** Exact plan approval belongs to this installer workspace/operation, never to imported project settings. */
internal object AptDownloadConsent {
    private data class Pending(val operation:String,val workspace:File,val hash:String)
    private var pending:Pending?=null
    @Synchronized fun poll(workspace:File,module:OfflineModuleId,operation:String):ModuleProgress? {
        val request=File(workspace,"apt-consent.request.json")
        if(!request.exists()){if(pending?.operation==operation)pending=null;return null}
        require(request.isFile && !Files.isSymbolicLink(request.toPath()) && request.length()<=4096)
        val j=JSONObject(request.readText());require(j.getInt("schema")==1 && j.getString("module")==module.name)
        val plan=File(workspace,"apt-consent.plan.tsv")
        require(plan.isFile && !Files.isSymbolicLink(plan.toPath()) && plan.length()<=2L*1024*1024)
        val hash=j.getString("sha256");require(hash.matches(Regex("[a-f0-9]{64}")) && VerifiedModuleDownload.sha(plan)==hash)
        val rows=plan.readLines().filter{it.isNotBlank()}.map{line->
            val f=line.split('\t');require(f.size==4 && f[0].matches(Regex("[A-Za-z0-9_+:.%~-]+[.]deb")) && f[2].matches(Regex("[a-f0-9]{64}")))
            require(f[3].startsWith("https://snapshot.ubuntu.com/ubuntu/${AptModuleLocks.SNAPSHOT}/pool/") && !f[3].contains(".."))
            f[0] to f[1].toLong().also{require(it>0)}
        }
        require(rows.size in 1..5000)
        pending=Pending(operation,workspace,hash)
        val bytes=rows.sumOf{it.second}
        return ModuleProgress(module,operation,ModulePhase.AWAITING_CONSENT,bytesTotal=bytes,errorCode="APT_DOWNLOAD_CONSENT",detail="APT snapshot ${AptModuleLocks.SNAPSHOT}: ${rows.size} eksik paket, $bytes bayt. Kesin paket/sürüm listesi:\n"+rows.joinToString("\n"){"${it.first} • ${it.second} bayt"}+"\nİndirme başlamadı; paket listesini onaylayın.")
    }
    @Synchronized fun approve(operation:String) {
        val p=pending?:error("Bekleyen doğrulanmış APT planı yok.");require(p.operation==operation)
        require(!File(p.workspace,"apt-consent.declined").exists()){ "Plan durduruldu; yeni işlem başlatın." }
        val file=File(p.workspace,"apt-consent.approved");val part=File(file.path+".tmp")
        require(!Files.isSymbolicLink(file.toPath()) && !Files.isSymbolicLink(part.toPath()))
        require(VerifiedModuleDownload.sha(File(p.workspace,"apt-consent.plan.tsv"))==p.hash)
        part.writeText(p.hash+"\n");Files.move(part.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
    }
    @Synchronized fun reject(operation:String) {
        val p=pending?:return
        require(p.operation==operation)
        val file=File(p.workspace,"apt-consent.declined");val part=File(file.path+".tmp")
        require(!Files.isSymbolicLink(file.toPath()) && !Files.isSymbolicLink(part.toPath()))
        require(VerifiedModuleDownload.sha(File(p.workspace,"apt-consent.plan.tsv"))==p.hash)
        part.writeText(p.hash+"\n")
        Files.move(part.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
    }
    @Synchronized fun clear(operation:String){if(pending?.operation==operation)pending=null}
}
