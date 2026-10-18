package com.appforge.studio.build

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONObject

/** Private, bounded operation metadata. No project, credential or SDK-consent state is persisted. */
internal class ModuleOperationJournal(private val file:File) {
    @Synchronized fun save(events:Map<OfflineModuleId,ModuleProgress>) {
        require(!Files.isSymbolicLink(file.toPath()))
        file.parentFile?.mkdirs()
        val json=JSONObject().put("schema",1)
        for((id,e) in events)json.put(id.name,JSONObject().put("operation",e.operationId.take(100)).put("phase",e.phase.name)
            .put("bytes",e.bytesDownloaded).put("total",e.bytesTotal).put("detail",e.detail.take(2000)))
        val part=File(file.path+".tmp")
        require(!Files.isSymbolicLink(part.toPath()))
        FileOutputStream(part).use {it.write(json.toString().toByteArray(Charsets.UTF_8));it.fd.sync()}
        Files.move(part.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
    }
    @Synchronized fun restore():Map<OfflineModuleId,ModuleProgress> = runCatching {
        if(!file.isFile || file.length()>32768 || Files.isSymbolicLink(file.toPath()))return@runCatching emptyMap()
        val json=JSONObject(file.readText());require(json.getInt("schema")==1)
        buildMap {
            for(id in OfflineModuleId.entries) {
                val e=json.optJSONObject(id.name)?:continue
                val previous=ModulePhase.valueOf(e.getString("phase"))
                val phase=if(previous in setOf(ModulePhase.ERROR,ModulePhase.CANCELED))previous else ModulePhase.PAUSED
                val bytes=e.getLong("bytes");require(bytes>=0)
                val total=if(e.has("total") && !e.isNull("total"))e.getLong("total").also{require(it>0 && it>=bytes)} else null
                put(id,ModuleProgress(id,e.getString("operation").take(100),phase,bytes,total,
                    detail="Önceki durum: ${previous.name}. Dosyalar yeniden doğrulanmadan hazır sayılmaz; kullanıcı onayıyla devam edin."))
            }
        }
    }.getOrDefault(emptyMap())
}
