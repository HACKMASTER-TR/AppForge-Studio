package com.appforge.studio.build

import org.json.JSONObject

/** Only structured telemetry from trusted transfer observers; distinguish payload, wire and file bytes. Never parse terminal percentages. */
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
        val metric=event.optString("byteMetric")
        val failed=event.optString("state")=="error"
        val measured=metric !in setOf("staged-files","unavailable")
        val finished=event.optBoolean("finished",false) && !failed
        val phase=when { failed->ModulePhase.ERROR; finished || !measured->ModulePhase.PREPARING; else->ModulePhase.DOWNLOADING }
        val detail=when {
            failed->" • aktarım kesildi; indirme tamamlanmadı."
            !measured->" • ağ aktarımı ölçülemiyor; boyut bilinmiyor, süre hesaplanıyor."
            finished->" • aktarım sonlandı; kurulum/son doğrulama sürüyor."
            metric=="wire-bytes"->" • soket baytları; HTTP içerik toplamı bilinmiyor."
            total==null->" • gerçek aktarım baytları; toplam boyut bilinmiyor."
            else->" • gerçek aktarım baytları ölçülüyor."
        }
        val sample=meter!!.sample(bytes,if(measured)total else null,now)
        sample.copy(
            phase=phase,errorCode=if(failed)event.optString("errorCode","TRANSFER_INTERRUPTED").take(100) else null,
            speedBytesPerSecond=if(measured && !failed)sample.speedBytesPerSecond else null,
            averageSpeedBytesPerSecond=if(measured && !failed)sample.averageSpeedBytesPerSecond else null,
            estimatedRemainingMs=if(measured && !failed)sample.estimatedRemainingMs else null,
            detail=event.optString("component","Araç arşivi").take(200)+detail)
    }.getOrNull()
}
