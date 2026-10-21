package com.appforge.studio

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.appforge.studio.build.*
import com.appforge.studio.model.ProjectDraft
import kotlinx.coroutines.*

@Composable internal fun ProjectDependencyPreparationCard(draft:ProjectDraft,onPrepared:((ProjectDraft)->Unit)?=null) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    val preparing by ProjectDependencyPreparation.busy.collectAsState()
    var preparedNodeRust by remember(draft){mutableStateOf(false)}
    LaunchedEffect(draft,preparing){preparedNodeRust=withContext(Dispatchers.IO){RustProjectPolicy.canPrepareNodeDependencies(draft)}}
    if(RustProjectPolicy.sourceRoot(draft)!=null && !preparedNodeRust){RustProjectPreparationCard(draft,onPrepared);return}
    var plan by remember(draft){mutableStateOf<ProjectDependencyPlan?>(null)}
    var info by remember{mutableStateOf("")};var ready by remember{mutableStateOf(false)}
    var confirmation by remember{mutableStateOf<Boolean?>(null)};var trusted by remember{mutableStateOf(false)}
    val transfer by ProjectDependencyPreparation.transfer.collectAsState()
    val busy by ProjectDependencyPreparation.busy.collectAsState();val status by ProjectDependencyPreparation.status.collectAsState()
    LaunchedEffect(draft,busy){withContext(Dispatchers.IO){runCatching{ProjectDependencyEvidence.analyze(draft)}.onSuccess{plan=it;ready=runCatching{ProjectDependencyPreparation.ready(context,it)}.getOrDefault(false)}.onFailure{info=it.message?:"Analiz başarısız"}}}
    if(confirmation!=null)AlertDialog(onDismissRequest={confirmation=null},title={Text("Proje kodunu çalıştırma onayı")},text={Column {
        Text("Kaynak projenin build/lifecycle kodu çalışacak. Hazırlık internet kullanabilir. Doğrulama için ağların kapalı olması gerekir. Yalnız güvendiğiniz kaynakla devam edin; Proot bir güvenlik sandbox'ı değildir.")
        Row{Checkbox(trusted,{trusted=it});Text("Bu kaynak koduna güveniyorum ve bu çalıştırmayı onaylıyorum")}
    }},confirmButton={TextButton(enabled=trusted && !busy,onClick={val offline=confirmation!!;confirmation=null;runCatching{ProjectDependencyPreparation.start(context,draft,true,offline)}.onFailure{info=it.message?:"Hazırlık engellendi"};trusted=false}){Text("Onayla ve çalıştır")}},dismissButton={TextButton(onClick={confirmation=null}){Text("Vazgeç")}})
    Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Proje bağımlılıklarını hazırla",style=MaterialTheme.typography.titleMedium)
        Text("Araç paketi hazır olması proje cache'i hazır demek değildir.")
        plan?.let{p->Text("Motor: ${p.engine} • ${p.manifests.size} manifest • ${p.dependencies.size} kilitli kayıt")
            Text("İndirme: Boyut bilinmiyor; Maven/npm/SDK alt işlemlerinde doğrulanmış toplam yok.")
            p.dependencies.take(8).forEach{Text(it)};p.blockers.forEach{Text(it)}
            Text(if(ready)"Son gerçek bağlantısız build ve kaynak/cache bütünlüğü geçerli." else "Proje çevrimdışı hazır olarak doğrulanmadı.")
            Button(enabled=!busy && p.blockers.isEmpty(),onClick={trusted=false;confirmation=false}){Text("Bağımlılıkları hazırlamayı onayla")}
            OutlinedButton(enabled=!busy && p.blockers.isEmpty(),onClick={trusted=false;confirmation=true}){Text("Ağlar kapalıyken gerçek build doğrula")}
        }
        Text(status);if(info.isNotBlank())Text(info)
        if(busy){
            transfer?.let{e->
                Text(e.detail)
                if(e.phase==ModulePhase.DOWNLOADING && e.percent!=null)LinearProgressIndicator(progress={e.percent!!/100f},modifier=Modifier.fillMaxWidth())else LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("${e.bytesDownloaded/1024/1024} MiB / "+(e.bytesTotal?.let{"${it/1024/1024} MiB • %${e.percent}"}?:"Boyut bilinmiyor"))
                Text("Hız: "+(e.speedBytesPerSecond?.let{"%.1f MiB/sn".format(it/1024/1024)}?:"Hesaplanıyor"))
                Text("Ortalama: "+(e.averageSpeedBytesPerSecond?.let{"%.1f MiB/sn".format(it/1024/1024)}?:"Hesaplanıyor"))
                Text("Kalan: "+(e.estimatedRemainingMs?.let{"${it/1000} sn"}?:"Hesaplanıyor")+" • ${e.elapsedMs/1000} sn geçti")
            }?:LinearProgressIndicator(Modifier.fillMaxWidth())
OutlinedButton(enabled=!ProjectDependencyPreparation.stopPending.value,onClick={ProjectDependencyPreparation.pause()}){Text("Kontrol noktasında duraklat")};OutlinedButton(enabled=!ProjectDependencyPreparation.stopPending.value,onClick={ProjectDependencyPreparation.cancel()}){Text("İptal iste; çalışan komutu bitir")}}
    }}
}

@Composable private fun RustProjectPreparationCard(draft:ProjectDraft,onPrepared:((ProjectDraft)->Unit)?) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    val sourceBusy by ProjectDependencyPreparation.busy.collectAsState()
    val installing by ModuleInstallOperations.busy.collectAsState()
    val busy=sourceBusy || installing
    val status by ProjectDependencyPreparation.status.collectAsState()
    val activeFolder by ProjectDependencyPreparation.rustProjectFolder.collectAsState()
    val transfer by ProjectDependencyPreparation.transfer.collectAsState()
    var mobileConsent by remember{mutableStateOf(false)}
    var confirm by remember{mutableStateOf<Boolean?>(null)};var trusted by remember{mutableStateOf(false)}
    var moduleReady by remember{mutableStateOf(false)};var prepared by remember{mutableStateOf<ProjectDraft?>(null)}
    var detail by remember{mutableStateOf("")}
    fun store()=RustPreparedProjects(RecoveryPathAnchor.androidPlatformPath(java.io.File(context.filesDir,"rust-prepared-v1")))
    LaunchedEffect(draft,busy){val restored=withContext(Dispatchers.IO){OfflineBuildPackManager.moduleReady(context,OfflineModuleId.RUST) to store().restore(draft)};moduleReady=restored.first;prepared=restored.second}
    fun begin(offline:Boolean){runCatching{ProjectDependencyPreparation.start(context,draft,true,offline,rustOnly=true)}.onFailure{DownloadNetwork.restorePreference();detail=it.message?:"Rust hazırlığı başlamadı"}}
    if(mobileConsent)AlertDialog(onDismissRequest={mobileConsent=false},title={Text("Bu işlem için ağ kullanım onayı")},text={Text("Mobil/ölçümlü bağlantı ve VPN dahil etkin doğrulanmış ağ, yalnız bu işlem için kullanılabilir. Kalan toplam boyut bilinmiyor. Sonraki işlemlerin izni değişmez.")},confirmButton={TextButton(enabled=!busy,onClick={mobileConsent=false;runCatching{DownloadNetwork.allowMobileOnce();begin(false)}.onFailure{DownloadNetwork.restorePreference();detail=it.message?:"Ağ onayı alınamadı"}}){Text("Yalnız bu işleme izin ver")}},dismissButton={TextButton(onClick={mobileConsent=false}){Text("Reddet • Wi-Fi bekle")}})
    if(confirm!=null)AlertDialog(onDismissRequest={confirm=null},title={Text(if(confirm==true)"Rust kaynak kodunu çalıştırma onayı" else "Kilitli Cargo bağımlılıklarını indirme onayı")},text={Column {
        Text(if(confirm==true)"Cargo build.rs/proc macro kodu çalışabilir. Proot güvenlik sandbox'ı değildir. Yalnız güvendiğiniz kaynağı onaylayın. Bütün ağları kapatın; eksik Cargo cache'inde güvenle durur. Özgün proje yerine ayrı özel kopya hazırlanır." else "Cargo.lock içindeki crates.io arşivleri sabit SHA-256 ile yönetilen indiriciden alınacak. Ağ tercihiniz korunur. Proje kodu bu aşamada çalıştırılmaz. Toplam boyut bilinmiyor; her aktarım gerçek baytlarla izlenir.")
        Row{Checkbox(trusted,{trusted=it});Text("Bu kaynak için bu çalıştırmayı açıkça onaylıyorum")}
    }},confirmButton={TextButton(enabled=trusted && !busy,onClick={val offline=confirm!!;confirm=null;if(!offline && DownloadNetwork.policy==DownloadNetworkPolicy.WIFI_PREFERRED && !DownloadNetwork.allowed())mobileConsent=true else begin(offline);trusted=false}){Text("Bu işlemi onayla")}},dismissButton={TextButton(onClick={confirm=null;trusted=false}){Text("Vazgeç")}})
    Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Rust projesini hazırla",style=MaterialTheme.typography.titleMedium)
        Text("Hedef: wasm32-unknown-unknown • Cargo.lock ve doğrulanmış Rust modülü gerekir. Android JNI/PyO3/JSI bu akışta desteklenmez.")
        if(!moduleReady)Text("Önce Rust + Cargo modülünü kurun. Android SDK/JDK/Gradle gerekmez.")
        OutlinedButton(enabled=!busy && moduleReady && draft.sourceBuildEngine in setOf("webview-static","node-web"),onClick={trusted=false;confirm=false}){Text("Kilitli Cargo bağımlılıklarını indirmeyi onayla")}
        Button(enabled=!busy && moduleReady && draft.sourceBuildEngine in setOf("webview-static","node-web"),onClick={trusted=false;confirm=true}){Text("Ağlar kapalıyken Rust WASM hazırla")}
        if(prepared!=null) {
            Text("Özel kopyada WASM ve kaynak hash'leri doğrulandı. Uygulamada çalışma kabulü ayrıdır.")
            OutlinedButton(enabled=!busy && onPrepared!=null,onClick={scope.launch {
                val checked=withContext(Dispatchers.IO){store().restore(draft)}
                if(checked==null){prepared=null;detail="Kaynak/çıktı değişti; hazırlanmış kopya seçilmedi."}else onPrepared?.invoke(checked)
            }}){Text("Hazırlanan kopyayı bu projede kullan")}
        }
        Text(if(activeFolder==draft.importedFolder)"Son işlem (hazır kanıtı değildir): $status" else "Bu projenin hazır durumu yalnız kendi kaynak/çıktı kaydıyla doğrulanır.");if(detail.isNotBlank())Text(detail)
        if(installing && !sourceBusy)Text("Paket işlemi sürüyor; proje hazırlığı başlamaz.")
        if(sourceBusy && activeFolder==draft.importedFolder){
            val event=transfer
            if(event!=null && event.phase==ModulePhase.DOWNLOADING) {
                if(event.percent!=null)LinearProgressIndicator(progress={event.percent!!/100f},modifier=Modifier.fillMaxWidth())else LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(event.detail)
                Text("${event.bytesDownloaded/1024/1024} MiB / "+(event.bytesTotal?.let{"${it/1024/1024} MiB • %${event.percent}"}?:"Boyut bilinmiyor"))
                Text("Hız: "+(event.speedBytesPerSecond?.let{"%.1f MiB/sn".format(it/1024/1024)}?:"Hesaplanıyor")+" • Ortalama: "+(event.averageSpeedBytesPerSecond?.let{"%.1f MiB/sn".format(it/1024/1024)}?:"Hesaplanıyor"))
                Text("Kalan: "+(event.estimatedRemainingMs?.let{"${it/1000} sn"}?:"Hesaplanıyor")+" • Geçen: ${event.elapsedMs/1000} sn")
            }else {LinearProgressIndicator(Modifier.fillMaxWidth());Text("Derleme/kurulum iş miktarı ölçülemiyor; toplam yüzde ve ETA gösterilmez.")}

            OutlinedButton(onClick={ProjectDependencyPreparation.pause()},enabled=!ProjectDependencyPreparation.stopPending.value){Text("Kontrol noktasında duraklat")}
            OutlinedButton(onClick={ProjectDependencyPreparation.cancel()},enabled=!ProjectDependencyPreparation.stopPending.value){Text("İptal iste")}
        }
    }}
}
