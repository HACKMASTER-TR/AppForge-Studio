package com.appforge.studio.build

import org.json.JSONObject

/** Only structured telemetry from trusted transfer observers; distinguish payload, wire and file bytes. Never parse terminal percentages. */
internal class ExternalDownloadTelemetry(private val module:OfflineModuleId,private val operation:String) {
    private data class Counter(val meter:DownloadMeasurement,var bytes:Long,var terminal:Boolean=false)
    private val transfers=linkedMapOf<String,Counter>()
    fun accept(text:String,now:Long):ModuleProgress?=runCatching {
        require(text.length<=2048)
        val event=JSONObject(text);require(event.getInt("schema")==1)
        val id=event.getString("transfer");require(id.length in 1..100)
        val bytes=event.getLong("bytes");require(bytes>=0)
        val total=if(event.isNull("total"))null else event.getLong("total").also{require(it>0 && bytes<=it)}
        val elapsed=event.optLong("elapsedMs",0).coerceIn(0,now.coerceAtMost(72L*60*60*1000))
        val baseline=event.optLong("initialBytes",0).also{require(it in 0..bytes)}
        var counter=transfers[id]
        if(counter?.terminal==true)return@runCatching null
        if(counter==null || bytes<counter.bytes) {
            if(transfers.size>=128)transfers.remove(transfers.keys.first())
            counter=Counter(DownloadMeasurement(module,operation,now-elapsed,if(elapsed>0)baseline else bytes),bytes)
            transfers[id]=counter
        }
        counter.bytes=bytes
        val metric=event.optString("byteMetric")
        val failed=event.optString("state")=="error"
        val measured=metric !in setOf("staged-files","unavailable","cached")
        val finished=event.optBoolean("finished",false) && !failed
        counter.terminal=finished || failed || metric=="cached"
        val phase=when { failed->ModulePhase.ERROR; finished || !measured->ModulePhase.PREPARING; else->ModulePhase.DOWNLOADING }
        val detail=when {
            failed->" • aktarım kesildi; indirme tamamlanmadı."
            metric=="cached"->" • APT önbelleği kullanıldı; ağdan yeniden indirilmedi."
            !measured->" • ağ aktarımı ölçülemiyor; boyut bilinmiyor, süre hesaplanıyor."
            finished->" • aktarım sonlandı; kurulum/son doğrulama sürüyor."
            metric=="wire-bytes"->" • soket baytları; HTTP içerik toplamı bilinmiyor."
            total==null->" • gerçek aktarım baytları; toplam boyut bilinmiyor."
            else->" • gerçek aktarım baytları ölçülüyor."
        }
        val sample=counter.meter.sample(bytes,if(measured)total else null,now)
        sample.copy(
            bytesDownloaded=if(measured)sample.bytesDownloaded else 0L,
            phase=phase,errorCode=if(failed)event.optString("errorCode","TRANSFER_INTERRUPTED").take(100) else null,
            speedBytesPerSecond=if(measured && !failed)sample.speedBytesPerSecond else null,
            averageSpeedBytesPerSecond=if(measured && !failed)sample.averageSpeedBytesPerSecond else null,
            estimatedRemainingMs=if(measured && !failed)sample.estimatedRemainingMs else null,
            detail=event.optString("component","Araç arşivi").take(200)+detail)
    }.getOrNull()
}
