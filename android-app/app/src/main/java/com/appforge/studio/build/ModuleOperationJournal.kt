package com.appforge.studio.build

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONObject

/** Private, bounded operation metadata. No project, credential or SDK-consent state is persisted. */
internal class ModuleOperationJournal(private val file:File,private val availableBytes:(File)->Long={it.usableSpace}) {
    // Free-form shell/network details are deliberately not persisted. A denylist
    // cannot reliably distinguish credentials, URLs, paths or future error text.
    private enum class Diagnostic { DRIVER_FAILED, NETWORK_WAIT, CONSENT_REQUIRED, USER_STOP, PROGRESS }
    private fun correlation(value:String):String = if(value.matches(Regex("id_[a-f0-9]{64}")))value
        else "id_"+java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
    private fun diagnostic(phase:ModulePhase):String=when(phase) {
        ModulePhase.ERROR->Diagnostic.DRIVER_FAILED
        ModulePhase.WAITING_CONNECTION,ModulePhase.RECONNECTING->Diagnostic.NETWORK_WAIT
        ModulePhase.AWAITING_CONSENT->Diagnostic.CONSENT_REQUIRED
        ModulePhase.CANCELED,ModulePhase.PAUSED->Diagnostic.USER_STOP
        else->Diagnostic.PROGRESS
    }.name
    private fun safeHistory(raw:org.json.JSONArray?):org.json.JSONArray {
        val safe=org.json.JSONArray()
        if(raw==null)return safe
        for(i in (raw.length()-32).coerceAtLeast(0) until raw.length()) {
            val row=raw.getJSONObject(i)
            val module=OfflineModuleId.valueOf(row.getString("module"))
            val phase=ModulePhase.valueOf(row.getString("phase"))
            safe.put(JSONObject().put("module",module.name).put("operation",correlation(row.getString("operation")))
                .put("phase",phase.name).put("detail",diagnostic(phase)).put("time",row.getLong("time")))
        }
        return safe
    }
    private fun requireReadable() {
        var parent:File?=file.absoluteFile
        while(parent!=null){require(!Files.isSymbolicLink(parent.toPath())){"Modül günlüğü symlink olamaz"};parent=parent.parentFile}
        if(!file.exists())return
        require(file.isFile && file.length() in 1..32768){"Bozuk modül günlüğü"}
        val json=JSONObject(file.readText());require(json.getInt("schema")==1){"Tanınmayan modül günlüğü şeması"}
    }
    @Synchronized fun save(events:Map<OfflineModuleId,ModuleProgress>) {
        requireReadable()
        require(!Files.isSymbolicLink(file.toPath()))
        val parent=requireNotNull(file.absoluteFile.parentFile);parent.mkdirs()
        for((_,e) in events) {
            require(e.operationId.isNotBlank()){"Modül işlem kimliği boş olamaz"}
            require(e.bytesDownloaded>=0 && (e.bytesTotal==null || e.bytesTotal>=e.bytesDownloaded)){"Geçersiz modül günlüğü bayt sayacı"}
        }
        val previous=readBounded()
        val history=safeHistory(previous?.optJSONArray("history"))
        for((id,e) in events) {
            val old=previous?.optJSONObject(id.name)
            if(old?.optString("operation")!=correlation(e.operationId) || old?.optString("phase")!=e.phase.name)
                history.put(JSONObject().put("module",id.name).put("operation",correlation(e.operationId)).put("phase",e.phase.name).put("detail",diagnostic(e.phase)).put("time",System.currentTimeMillis()))
        }
        val bounded=org.json.JSONArray()
        for(i in (history.length()-32).coerceAtLeast(0) until history.length())bounded.put(history.getJSONObject(i))
        val json=JSONObject().put("schema",1).put("history",bounded)
        for((id,e) in events)json.put(id.name,JSONObject().put("operation",correlation(e.operationId)).put("phase",e.phase.name)
            .put("bytes",e.bytesDownloaded).put("total",e.bytesTotal).put("detail",diagnostic(e.phase)))
        val bytes=json.toString().toByteArray(Charsets.UTF_8);require(bytes.size<=32768){"Modül günlüğü boyut sınırını aşıyor"}
        require(availableBytes(parent)>bytes.size+65536L){"Modül günlüğü için depolama alanı yetersiz; önceki kayıt korunur."}
        val part=File(file.path+".tmp")
        require(!Files.isSymbolicLink(part.toPath()))
        try {
            FileOutputStream(part).use {it.write(bytes);it.fd.sync()}
            Files.move(part.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
        } catch(e:Throwable) {
            if(!Files.isSymbolicLink(part.toPath()))Files.deleteIfExists(part.toPath())
            throw e
        }
    }
    private fun readBounded():JSONObject?=runCatching {
        require(file.isFile && file.length()<=32768 && !Files.isSymbolicLink(file.toPath()))
        JSONObject(file.readText()).also{require(it.getInt("schema")==1)}
    }.getOrNull()
    @Synchronized fun history():List<String> {
        requireReadable()
        val records=safeHistory(readBounded()?.optJSONArray("history"))
        return ((records.length()-32).coerceAtLeast(0) until records.length()).mapNotNull{index->runCatching{val e=records.getJSONObject(index);e.getString("module").take(40)+" • "+e.getString("phase").take(40)+" • "+e.getString("detail").take(240)}.getOrNull()}
    }
    /** Legacy read-only callers get no trusted state on corruption. Drivers must use restoreStrict. */
    @Synchronized fun restore():Map<OfflineModuleId,ModuleProgress> = runCatching{restoreStrict()}.getOrDefault(emptyMap())
    @Synchronized fun restoreStrict():Map<OfflineModuleId,ModuleProgress> {
        requireReadable()
        return runCatching {
        if(!file.isFile || file.length()>32768 || Files.isSymbolicLink(file.toPath()))return@runCatching emptyMap()
        val json=JSONObject(file.readText());require(json.getInt("schema")==1)
        buildMap {
            for(id in OfflineModuleId.entries) {
                val e=json.optJSONObject(id.name)?:continue
                val previous=ModulePhase.valueOf(e.getString("phase"))
                val phase=if(previous in setOf(ModulePhase.ERROR,ModulePhase.CANCELED,ModulePhase.REMOVED))previous else ModulePhase.PAUSED
                val bytes=e.getLong("bytes");require(bytes>=0)
                val total=if(e.has("total") && !e.isNull("total"))e.getLong("total").also{require(it>0 && it>=bytes)} else null
                put(id,ModuleProgress(id,correlation(e.getString("operation")),phase,bytes,total,
                    detail="Önceki durum: ${previous.name}. Dosyalar yeniden doğrulanmadan hazır sayılmaz; kullanıcı onayıyla devam edin."))
            }
        }
    }.getOrElse{throw IllegalStateException("Modül günlüğü reddedildi; kaynaklardan yeniden doğrulama gerekir.",it)}
    }
}
