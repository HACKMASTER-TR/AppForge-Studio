package com.appforge.studio.build

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONObject

/** Private, bounded operation metadata. No project, credential or SDK-consent state is persisted. */
internal class ModuleOperationJournal(private val file:File,private val availableBytes:(File)->Long={it.usableSpace}) {
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
            require(e.operationId.isNotBlank() && !e.operationId.contains("://") && !e.detail.contains("https://") && !e.detail.contains("http://")){"Modül günlüğü URL veya gizli kaynak içeremez"}
            require(e.bytesDownloaded>=0 && (e.bytesTotal==null || e.bytesTotal>=e.bytesDownloaded)){"Geçersiz modül günlüğü bayt sayacı"}
        }
        val previous=readBounded()
        val history=previous?.optJSONArray("history")?:org.json.JSONArray()
        for((id,e) in events) {
            val old=previous?.optJSONObject(id.name)
            if(old?.optString("operation")!=e.operationId.take(100) || old?.optString("phase")!=e.phase.name)
                history.put(JSONObject().put("module",id.name).put("operation",e.operationId.take(100)).put("phase",e.phase.name).put("detail",e.detail.take(240)).put("time",System.currentTimeMillis()))
        }
        val bounded=org.json.JSONArray()
        for(i in (history.length()-32).coerceAtLeast(0) until history.length())bounded.put(history.getJSONObject(i))
        val json=JSONObject().put("schema",1).put("history",bounded)
        for((id,e) in events)json.put(id.name,JSONObject().put("operation",e.operationId.take(100)).put("phase",e.phase.name)
            .put("bytes",e.bytesDownloaded).put("total",e.bytesTotal).put("detail",e.detail.take(2000)))
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
        val records=readBounded()?.optJSONArray("history")?:return emptyList()
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
                put(id,ModuleProgress(id,e.getString("operation").take(100),phase,bytes,total,
                    detail="Önceki durum: ${previous.name}. Dosyalar yeniden doğrulanmadan hazır sayılmaz; kullanıcı onayıyla devam edin."))
            }
        }
    }.getOrElse{throw IllegalStateException("Modül günlüğü reddedildi; kaynaklardan yeniden doğrulama gerekir.",it)}
    }
}
