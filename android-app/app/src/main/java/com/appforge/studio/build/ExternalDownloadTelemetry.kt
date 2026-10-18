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
        if(transfer!=id || bytes<previousBytes) {transfer=id;meter=DownloadMeasurement(module,operation,now,bytes)}
        previousBytes=bytes
        meter!!.sample(bytes,total,now).copy(phase=if(event.optBoolean("finished",false))ModulePhase.PREPARING else ModulePhase.DOWNLOADING,detail="Araç arşivi indiriliyor; gerçek dosya baytları ölçülüyor.")
    }.getOrNull()
}
