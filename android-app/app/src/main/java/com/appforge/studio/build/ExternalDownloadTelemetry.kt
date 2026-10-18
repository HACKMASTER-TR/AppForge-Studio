package com.appforge.studio.build

import org.json.JSONObject

/** Only structured file-byte telemetry produced by our curl adapter. Never parse terminal percentages. */
internal class ExternalDownloadTelemetry(private val module:OfflineModuleId,private val operation:String) {
    private var transfer:String?=null
    private var meter:DownloadMeasurement?=null
    private var previousBytes=0L
    fun accept(text:String,now:Long):ModuleProgress?=runCatching {
        require(text.length<=2048)
        val event=JSONObject(text);require(event.getInt("schema")==1)
        val id=event.getString("transfer");require(id.length in 1..100)
        val bytes=event.getLong("bytes");require(bytes>=0)
        val total=if(event.isNull("total"))null else event.getLong("total").also{require(it>0 && bytes<=it)}
        if(transfer!=id || bytes<previousBytes) {
            transfer=id
            val elapsed=event.optLong("elapsedMs",0).coerceIn(0,now.coerceAtMost(72L*60*60*1000))
            meter=DownloadMeasurement(module,operation,now-elapsed,if(elapsed>0)0 else bytes)
        }
        previousBytes=bytes
        meter!!.sample(bytes,total,now).copy(phase=if(event.optBoolean("finished",false))ModulePhase.PREPARING else ModulePhase.DOWNLOADING,detail=event.optString("component","Araç arşivi").take(200)+if(event.optBoolean("finished",false))" • aktarım sonlandı; kurulum/son doğrulama sürüyor." else " • gerçek aktarım baytları ölçülüyor.")
    }.getOrNull()
}
