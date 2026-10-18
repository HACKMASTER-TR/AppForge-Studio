@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.appforge.studio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.appforge.studio.build.*
import com.appforge.studio.model.ProjectDraft
import kotlinx.coroutines.*

/** App-scoped operation state survives navigation/rotation; process death is never reported READY. */
internal object ModuleInstallOperations {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    val updates=kotlinx.coroutines.flow.MutableStateFlow<Map<OfflineModuleId,ModuleProgress>>(emptyMap())
    val busy=kotlinx.coroutines.flow.MutableStateFlow(false)
    private var job:Job?=null
    private var journal:ModuleOperationJournal?=null
    @Synchronized fun restore(context:android.content.Context) {
        if(journal!=null)return
        journal=ModuleOperationJournal(java.io.File(context.noBackupFilesDir,"offline-module-v3/operation.json"))
        updates.value=journal!!.restore()
    }
    private fun persist(){runCatching{journal?.save(updates.value)}}
    @Volatile private var pauseRequested=false
    @Synchronized fun start(context:android.content.Context, selected:OfflineModuleId, consent:Boolean) {
        if(busy.value)return
        restore(context)
        busy.value=true;pauseRequested=false
        try {ModuleDownloadService.start(context.applicationContext)}catch(e:RuntimeException) {
            busy.value=false
            updates.value=updates.value+(selected to ModuleProgress(selected,"start-denied",ModulePhase.ERROR,detail="Android arka plan hizmetini başlatmadı; uygulama önplandayken yeniden deneyin."))
            persist();return
        }
        job=scope.launch {
            try { OfflineBuildPackManager.installModules(context.applicationContext,setOf(selected),consent){event->
                updates.value=updates.value+(event.moduleId to event)
                persist()
            }} catch(e:CancellationException) {
                updates.value=updates.value+(selected to ModuleProgress(selected,"canceled",if(pauseRequested)ModulePhase.PAUSED else ModulePhase.CANCELED,detail=if(pauseRequested)"Duraklatıldı; doğrulanmamış kısmi dosya devam için korunur." else "İptal edildi; çalışan modüller korunur."))
            } catch(e:Exception) {
                // Preserve the manager's connection-wait/error state for this operation.
                if(updates.value[selected]?.phase !in setOf(ModulePhase.WAITING_CONNECTION,ModulePhase.ERROR))
                updates.value=updates.value+(selected to ModuleProgress(selected,"failed",ModulePhase.ERROR,errorCode=e.javaClass.simpleName,detail=e.message?:"Kurulum başarısız."))
            } finally {persist();busy.value=false}
        }
    }
    @Synchronized fun cancel(){pauseRequested=false;job?.cancel()}
    @Synchronized fun pause(){pauseRequested=true;job?.cancel()}
}

@Composable internal fun OfflineBuildPackScreen(onBack:()->Unit,firstRun:Boolean=false,onFirstRunComplete:((Boolean)->Unit)?=null,project:ProjectDraft?=null) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val events by ModuleInstallOperations.updates.collectAsState()
    val busy by ModuleInstallOperations.busy.collectAsState()
    var ready by remember { mutableStateOf<Map<OfflineModuleId,Boolean>>(emptyMap()) }
    var chosen by remember { mutableStateOf<OfflineModuleId?>(null) }
    var sdkConsent by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf("") }
    var rollbackAllowed by remember {mutableStateOf<Map<OfflineModuleId,Boolean>>(emptyMap())}
    val plan=remember(project){project?.let{OfflineModules.forProject(it)}}
    val readinessChanges=events.mapValues{it.value.phase==ModulePhase.READY}
    LaunchedEffect(Unit){withContext(Dispatchers.IO){ModuleInstallOperations.restore(context)}}
    LaunchedEffect(readinessChanges,busy){ready=withContext(Dispatchers.IO){OfflineModules.all.associate{it.id to OfflineBuildPackManager.moduleReady(context,it.id)}}
        rollbackAllowed=withContext(Dispatchers.IO){OfflineModules.all.associate{it.id to OfflineBuildPackManager.canRollback(context,it.id)}}}
    fun begin(id:OfflineModuleId) {
        ModuleInstallOperations.start(context,id,sdkConsent)
    }
    if(chosen!=null) {
        val id=chosen!!;val missing=OfflineModules.resolve(setOf(id)).filter{ready[it]!=true}
        val needsSdk=OfflineModuleId.ANDROID_CORE in missing || OfflineModuleId.EXPO_REACT_NATIVE in missing
        AlertDialog(onDismissRequest={chosen=null},title={Text("Kurulumu onayla")},text={Column {
            Text("Yalnız eksik modüller: "+missing.joinToString{OfflineModules.spec(it).title})
            Text("Boyutu doğrulanmamış indirmelerde toplam boyut bilinmiyor gösterilir. Bağımlılıklar ortak kullanılır.")
            if(needsSdk){Text("Android SDK lisansı yalnız bu kurulum için gereklidir. SDK Manager lisansı cihazda ayrıca doğrular.");TextButton(onClick={android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://developer.android.com/studio/terms")).also{context.startActivity(it)}}){Text("Android SDK lisansını incele")};Row{Checkbox(sdkConsent,{sdkConsent=it});Text("SDK lisansını kabul ediyorum")}}
        }},confirmButton={TextButton(enabled=!busy && (!needsSdk||sdkConsent),onClick={chosen=null;begin(id)}){Text("Onayla ve kur")}},dismissButton={TextButton(onClick={chosen=null}){Text("Vazgeç")}})
    }
    Scaffold(topBar={TopAppBar(title={Text("Modüler Çevrimdışı Derleme V3")},navigationIcon={TextButton(onClick=onBack){Text("Geri")}})}){padding->
        LazyColumn(modifier=Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item { Text("İlk açılışta araç indirme zorunluluğu yok. Fast HTML gömülüdür. Yalnız seçtiğiniz motorun araçlarını kurun.") }
            item { Text("Boş alan: ${context.noBackupFilesDir.usableSpace/1024/1024} MiB. APT/Gradle/npm toplamları proje ve mimariye bağlıdır; doğrulanmış toplam yoksa yüzde gösterilmez.") }
            plan?.let{p->item{Text(p.error?:"Bu proje için: "+p.modules.joinToString{OfflineModules.spec(it).title})}}
            items(OfflineModules.all.filter{(it.id!=OfflineModuleId.LINUX_RUNTIME || plan?.modules?.contains(it.id)==true || events.containsKey(it.id)) && (plan==null || it.id in plan.modules)}) { module ->
                val state=events[module.id];val installed=ready[module.id]==true
                val unsupported=if(module.id !in setOf(OfflineModuleId.FAST_HTML,OfflineModuleId.WINDOWS_PORTABLE))DeviceBuildRuntimeV3.platformError() else null
                Card(modifier=Modifier.fillMaxWidth()) {Column(modifier=Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(module.title,style=MaterialTheme.typography.titleMedium)
                    Text(module.version)
                    Text(when(module.id){
                        OfflineModuleId.FAST_HTML->"Yerel HTML/HTTPS için gömülü Android şablonları"
                        OfflineModuleId.LINUX_RUNTIME->"Normal motorların ortak, projelerden ayrı Linux ortamı"
                        OfflineModuleId.ANDROID_CORE->"Gradle tabanlı Android APK/AAB projeleri"
                        OfflineModuleId.NODE_WEB->"Node tabanlı web kaynaklarının hazırlanması"
                        OfflineModuleId.PYTHON_ANDROID->"Chaquopy ile Android Python projeleri"
                        OfflineModuleId.EXPO_REACT_NATIVE->"Desteklenen Expo/React Native Android yolu"
                        OfflineModuleId.WINDOWS_PORTABLE->"Android SDK gerektirmeyen Portable EXE host'u"
                        OfflineModuleId.WINDOWS_NATIVE->"Deneysel Windows C/C++ çapraz derleme"
                    })
                    if(unsupported!=null)Text(unsupported)
                    Text("Bağımlılıklar: "+module.dependencies.joinToString{OfflineModules.spec(it).title}.ifBlank{"Yok"})
                    Text("İndirme: "+(module.downloadBytes?.let{"${it/1024/1024} MiB"}?:"Toplam boyut bilinmiyor"))
                    Text("Disk: "+(module.diskBytes?.let{"${it/1024/1024} MiB"}?:"Güvenilir toplam tahmin yok"))
                    Text(if(installed)"Hazır • gerçek bütünlük kontrolü geçti" else state?.phase?.turkishLabel()?:"Kurulu değil / doğrulanmadı")
                    if(state!=null && !installed) {
                        Text(state.detail)
                        if(state.phase==ModulePhase.DOWNLOADING) {
                            val pct=state.percent
                            if(pct!=null) {LinearProgressIndicator(progress={pct/100f},modifier=Modifier.fillMaxWidth());Text("%$pct • ${state.bytesDownloaded/1024/1024} / ${state.bytesTotal!!/1024/1024} MiB")} else {LinearProgressIndicator(modifier=Modifier.fillMaxWidth());Text("${state.bytesDownloaded/1024/1024} MiB • toplam boyut bilinmiyor")}
                            Text("Hız: "+(state.speedBytesPerSecond?.let{"%.1f MiB/sn".format(it/1024/1024)}?:"Hesaplanıyor..."))
                            Text("Ortalama: "+(state.averageSpeedBytesPerSecond?.let{"%.1f MiB/sn".format(it/1024/1024)}?:"Hesaplanıyor..."))
                            Text("Bağlantı: veri aktarılıyor • hız hareketli ortalamadır")
                            Text("Kalan: "+(state.estimatedRemainingMs?.let{"${it/1000} sn"}?:"Hesaplanıyor...")+" • Geçen: ${state.elapsedMs/1000} sn")
                        } else if(busy && state.phase !in setOf(ModulePhase.READY,ModulePhase.ERROR,ModulePhase.CANCELED))LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
                    }
                    Button(enabled=!busy && unsupported==null,onClick={if(installed){scope.launch{val good=withContext(Dispatchers.IO){OfflineBuildPackManager.moduleReady(context,module.id)};detail=if(good)"Bütünlük doğrulandı." else "Bozuk kurulum; hazır değil."}}else chosen=module.id}){Text(if(installed)"Doğrula" else if(state?.phase in setOf(ModulePhase.PAUSED,ModulePhase.WAITING_CONNECTION))"Devam et" else if(state?.phase==ModulePhase.ERROR)"Yeniden dene" else "Kurulum ayrıntıları")}
                    if(busy && state!=null && state.phase !in setOf(ModulePhase.READY,ModulePhase.ERROR)){ if(module.id in setOf(OfflineModuleId.NODE_WEB,OfflineModuleId.WINDOWS_PORTABLE) && state.phase==ModulePhase.DOWNLOADING)OutlinedButton(onClick={ModuleInstallOperations.pause()}){Text("Duraklat")};OutlinedButton(onClick={ModuleInstallOperations.cancel()}){Text("İptal et")} }
                    if(installed && module.id==OfflineModuleId.WINDOWS_PORTABLE)OutlinedButton(enabled=!busy,onClick={
                        scope.launch {detail=withContext(Dispatchers.IO){runCatching {
                            BuildModuleUsage.mutationLease().use {OwnedModuleFiles.removePortable(context.noBackupFilesDir,WindowsPortableHostStore.HOST_SHA256)}
                        }.fold({"Yalnız doğrulanmış Windows Portable dosyaları kaldırıldı."},{"Kaldırma engellendi: ${it.message}"})};ready=ready+(module.id to OfflineBuildPackManager.moduleReady(context,module.id))}
                    }){Text("Windows Portable paketini kaldır")}
                    if(rollbackAllowed[module.id]==true)OutlinedButton(enabled=!busy,onClick={scope.launch {
                        detail=withContext(Dispatchers.IO){runCatching{OfflineBuildPackManager.rollbackModule(context,module.id)}.fold({"Son izole kurulum geri alındı; önceki ortam korundu."},{"Geri alma engellendi: ${it.message}"})}
                        ready=withContext(Dispatchers.IO){OfflineModules.all.associate{it.id to OfflineBuildPackManager.moduleReady(context,it.id)}}
                        rollbackAllowed=withContext(Dispatchers.IO){OfflineModules.all.associate{it.id to OfflineBuildPackManager.canRollback(context,it.id)}}
                    }}){Text("Son izole kurulumu geri al / kaldır")}
                    OutlinedButton(enabled=false,onClick={}){Text("Güncelleme: doğrulanmış yeni sürüm yok")}
                    if(module.id!=OfflineModuleId.WINDOWS_PORTABLE)Text("Genel dosya silme kapalı. Son doğrulanmış izole sürüm, bağımlılık kullanımı yoksa geri alınabilir; daha eski modüller önce bağımlılık sırasıyla çözülmelidir.",style=MaterialTheme.typography.bodySmall)
                }}
            }
            if(ready[OfflineModuleId.WINDOWS_PORTABLE]==true)item { WindowsModuleSmokeCard() }
            item { Text("Toplam işlem: "+if(busy)"devam ediyor; kurulum iş miktarı ölçülemediği için toplam yüzde yok" else "bekliyor / son modül durumlarını inceleyin") }
            item { Text("Kullanıcının başlattığı kurulum Android veri aktarımı servisiyle izlenir. Android zaman sınırı veya süreç ölümü işlemi durdurabilir. Yeniden açınca kayıt geri yüklenir; devam için onay gerekir, dosyalar yeniden doğrulanır.") }
            if(detail.isNotBlank())item{Text(detail)}
            if(firstRun)item{Button(onClick={onFirstRunComplete?.invoke(true)}){Text("Araç indirmeden devam et")}}
        }
    }
}


@Composable private fun WindowsModuleSmokeCard() {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var smoke by remember{mutableStateOf<java.io.File?>(null)}
    var busy by remember{mutableStateOf(false)}
    var detail by remember{mutableStateOf("")}
    val save=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream")){uri->
        if(uri!=null && smoke?.isFile==true)scope.launch {
            detail=withContext(Dispatchers.IO){runCatching {context.contentResolver.openOutputStream(uri,"w")!!.use{output->smoke!!.inputStream().use{it.copyTo(output)}}}.fold({"Windows test dosyası kaydedildi; fiziksel Windows'ta açın."},{"Kaydetme hatası: ${it.message}"})}
        }
    }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Button(enabled=!busy && !ModuleInstallOperations.busy.value,onClick={busy=true;scope.launch {
            runCatching {WindowsPortableExePackager.createAcceptanceSmoke(context){}}.onSuccess{smoke=it;detail="Cihaz-local test EXE'si oluşturuldu; Windows çalışması henüz doğrulanmadı."}.onFailure{detail=it.message?:"EXE testi başarısız"};busy=false
        }}){Text("CİHAZ EXE KABUL DOSYASI OLUŞTUR")}
        if(smoke?.isFile==true)OutlinedButton(onClick={save.launch("AppForge-Windows-Smoke.exe")}){Text("WINDOWS TEST EXE'SİNİ KAYDET")}
        if(detail.isNotBlank())Text(detail)
    }
}

private fun ModulePhase.turkishLabel():String = when(this) {
    ModulePhase.RESOLVING -> "Bağımlılıklar hesaplanıyor"
    ModulePhase.STORAGE_CHECK -> "Depolama kontrol ediliyor"
    ModulePhase.AWAITING_CONSENT -> "Kullanıcı onayı bekleniyor"
    ModulePhase.CONNECTING -> "Bağlantı hazırlanıyor"
    ModulePhase.DOWNLOADING -> "İndiriliyor"
    ModulePhase.VERIFYING -> "Bütünlük doğrulanıyor"
    ModulePhase.EXTRACTING -> "Arşiv açılıyor"
    ModulePhase.INSTALLING -> "Kuruluyor"
    ModulePhase.PREPARING -> "Bağımlılıklar hazırlanıyor"
    ModulePhase.OFFLINE_TEST -> "Çevrimdışı kontrol ediliyor"
    ModulePhase.READY -> "Hazır"
    ModulePhase.PAUSED -> "Duraklatıldı"
    ModulePhase.WAITING_CONNECTION -> "Bağlantı bekleniyor; yeniden deneyin"
    ModulePhase.RECONNECTING -> "Yeniden bağlanıyor"
    ModulePhase.CANCELED -> "İptal edildi"
    ModulePhase.ERROR -> "Hata oluştu"
}
