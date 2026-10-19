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
    val activeProgress=kotlinx.coroutines.flow.MutableStateFlow<ModuleProgress?>(null)
    val updates=kotlinx.coroutines.flow.MutableStateFlow<Map<OfflineModuleId,ModuleProgress>>(emptyMap())
    val busy=kotlinx.coroutines.flow.MutableStateFlow(false)
    private var job:Job?=null
    private var journal:ModuleOperationJournal?=null
    @Synchronized fun restore(context:android.content.Context) {
        if(journal!=null)return
        journal=ModuleOperationJournal(java.io.File(context.noBackupFilesDir,"offline-module-v3/operation.json"))
        updates.value=journal!!.restore()
    }
    val journalError=kotlinx.coroutines.flow.MutableStateFlow("")
    fun history():List<String> = journal?.history().orEmpty()
    private fun persist(){try{journal?.save(updates.value);journalError.value=""}catch(e:Exception){journalError.value="İşlem günlüğü yazılamadı; güvenli devam garantisi yok. Depolamayı kontrol edin.";throw e}}
    @Synchronized private fun publish(event:ModuleProgress) {
        activeProgress.value=event;updates.value=updates.value+(event.moduleId to event);persist()
    }
    @Volatile private var pauseRequested=false
    @Synchronized fun start(context:android.content.Context, selected:OfflineModuleId, consent:Boolean) {
        if(busy.value)return
        restore(context)
        busy.value=true;pauseRequested=false
        try {ModuleDownloadService.start(context.applicationContext)}catch(e:RuntimeException) {
            busy.value=false
            updates.value=updates.value+(selected to ModuleProgress(selected,"start-denied",ModulePhase.ERROR,detail="Android arka plan hizmetini başlatmadı; uygulama önplandayken yeniden deneyin."))
            runCatching{persist()};return
        }
        job=scope.launch {
            try {
                var retries=0
                while(true) {
                    val last=java.util.concurrent.atomic.AtomicReference<ModuleProgress?>(null)
                    try {
                        OfflineBuildPackManager.installModules(context.applicationContext,setOf(selected),consent){event->
                            last.set(event);publish(event)
                        }
                        break
                    }catch(e:CancellationException){throw e}
                    catch(e:Exception) {
                        val event=last.get()
                        if(event?.phase!=ModulePhase.WAITING_CONNECTION || retries>=5)throw e
                        retries++
                        val started=android.os.SystemClock.elapsedRealtime()
                        while(!OfflineBuildPackManager.hasValidatedInternet(context.applicationContext)) {
                            currentCoroutineContext().ensureActive()
                            require(android.os.SystemClock.elapsedRealtime()-started<24L*60*1000){"Bağlantı bekleme süresi doldu; kısmi arşiv korunur. Devam et ile tekrar deneyin."}
                            val waiting=event.copy(phase=ModulePhase.WAITING_CONNECTION,bytesTotal=null,estimatedRemainingMs=null,speedBytesPerSecond=null,detail="Bağlantı bekleniyor; onaylı işlem bağlantı gelince güvenle yeniden denenecek. Bekleme: ${(android.os.SystemClock.elapsedRealtime()-started)/1000} sn")
                            publish(waiting)
                            delay(1000)
                        }
                        val retry=event.copy(phase=ModulePhase.RECONNECTING,detail="Bağlantı geldi; kaynak kimliği, kısmi dosya ve hash yeniden doğrulanıyor. Deneme $retries/5")
                        publish(retry)
                        delay((1000L shl (retries-1)).coerceAtMost(30000L))
                    }
                }
            } catch(e:CancellationException) {
                updates.value=updates.value+(selected to ModuleProgress(selected,"canceled",if(pauseRequested)ModulePhase.PAUSED else ModulePhase.CANCELED,detail=if(pauseRequested)"Duraklatıldı; doğrulanmamış kısmi dosya devam için korunur." else "İptal edildi; çalışan modüller korunur."))
            } catch(e:Exception) {
                // Preserve the manager's connection-wait/error state for this operation.
                if(updates.value[selected]?.phase !in setOf(ModulePhase.WAITING_CONNECTION,ModulePhase.ERROR))
                updates.value=updates.value+(selected to ModuleProgress(selected,"failed",ModulePhase.ERROR,errorCode=e.javaClass.simpleName,detail=e.message?:"Kurulum başarısız."))
            } finally {try{runCatching{persist()}}finally{activeProgress.value=null;busy.value=false}}
        }
    }
    @Synchronized fun lifecycle(context:android.content.Context,id:OfflineModuleId,action:String) {
        require(action in setOf("remove","rollback"));if(busy.value)return
        restore(context);busy.value=true
        try{ModuleDownloadService.start(context.applicationContext)}catch(e:RuntimeException){busy.value=false;journalError.value="Android yaşam döngüsü hizmetini başlatmadı: ${e.message}";return}
        job=scope.launch {
            val operation=java.util.UUID.randomUUID().toString()
            try {
                publish(ModuleProgress(id,operation,if(action=="remove")ModulePhase.REMOVING else ModulePhase.ROLLING_BACK,detail="Onaylı işlem: bağımlılık, sahiplik, kullanım ve generation bütünlüğü yeniden doğrulanıyor."))
                if(action=="remove")OfflineBuildPackManager.removeOwnedModule(context.applicationContext,id)
                else OfflineBuildPackManager.rollbackModule(context.applicationContext,id)
                val resultPhase=if(action=="remove" || OfflineBuildPackManager.installedVersion(context,id)==null)ModulePhase.REMOVED else if(OfflineBuildPackManager.moduleReady(context,id))ModulePhase.READY else ModulePhase.PREPARING
                publish(ModuleProgress(id,operation,resultPhase,
                    detail=if(action=="remove")"Modül kaydı güvenle kaldırıldı. Arşivlenen runtime/host dosyaları korunur; kullanıcı kaynakları silinmedi." else if(resultPhase==ModulePhase.READY)"Önceki generation SHA-256 envanteriyle doğrulanarak etkinleştirildi." else "Önceki ortam dosyaları geri getirildi; güncel motor önkontrolü geçmeden HAZIR sayılmaz."))
            } catch(e:CancellationException){publish(ModuleProgress(id,operation,ModulePhase.PAUSED,detail="İşlem kesildi; yeniden açılışta generation günlüğü ve dosyalar doğrulanacak."));throw e}
            catch(e:Exception){publish(ModuleProgress(id,operation,ModulePhase.ERROR,errorCode=e.javaClass.simpleName,detail="İşlem engellendi: ${e.message}"))}
            finally{activeProgress.value=null;busy.value=false}
        }
    }
    @Synchronized fun cancel(){pauseRequested=false;job?.cancel()}
    @Synchronized fun pause(){pauseRequested=true;job?.cancel()}
}

@Composable internal fun OfflineBuildPackScreen(onBack:()->Unit,firstRun:Boolean=false,onFirstRunComplete:((Boolean)->Unit)?=null,project:ProjectDraft?=null) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val journalError by ModuleInstallOperations.journalError.collectAsState()
    val events by ModuleInstallOperations.updates.collectAsState()
    val busy by ModuleInstallOperations.busy.collectAsState()
    val usageRevision by BuildModuleUsage.changes.collectAsState()
    var recovered by remember{mutableStateOf(false)}
    var lifecycleStates by remember{mutableStateOf<Map<OfflineModuleId,ModuleLifecycleStatus>>(emptyMap())}
    var pendingLifecycle by remember{mutableStateOf<Pair<OfflineModuleId,String>?>(null)}
    var ready by remember { mutableStateOf<Map<OfflineModuleId,Boolean>>(emptyMap()) }
    var history by remember{mutableStateOf<List<String>>(emptyList())}
    var chosen by remember { mutableStateOf<OfflineModuleId?>(null) }
    var sdkConsent by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf("") }
    var availableUpdates by remember{mutableStateOf<Set<OfflineModuleId>>(emptySet())}
    var confirmPrune by remember{mutableStateOf(false)}
    var pruneBytes by remember{mutableStateOf<Long?>(null)}
    var pruneReason by remember{mutableStateOf<String?>(null)}
    var installedVersions by remember{mutableStateOf<Map<OfflineModuleId,String?>>(emptyMap())}
    val plan=remember(project){project?.let{OfflineModules.forProject(it)}}
    val readinessChanges=events.mapValues{it.value.phase==ModulePhase.READY}
    LaunchedEffect(Unit){withContext(Dispatchers.IO){ModuleInstallOperations.restore(context);runCatching{OfflineBuildPackManager.recoverPending(context)}.onFailure{detail="Kurtarma güvenli biçimde durdu: ${it.message}"}};recovered=true}
    LaunchedEffect(readinessChanges,busy,usageRevision,recovered){
        if(!recovered)return@LaunchedEffect
        val cleanup=withContext(Dispatchers.IO){runCatching{OfflineBuildPackManager.prunableSnapshotBytes(context)}}
        pruneBytes=cleanup.getOrNull();pruneReason=cleanup.exceptionOrNull()?.message
        lifecycleStates=withContext(Dispatchers.IO){OfflineModules.all.associate{it.id to OfflineBuildPackManager.lifecycleStatus(context,it.id)}}
        availableUpdates=withContext(Dispatchers.IO){OfflineModules.all.filter{OfflineBuildPackManager.updateAvailable(context,it.id)}.map{it.id}.toSet()}
        installedVersions=withContext(Dispatchers.IO){OfflineModules.all.associate{it.id to OfflineBuildPackManager.installedVersion(context,it.id)}}
        history=withContext(Dispatchers.IO){ModuleInstallOperations.history()}
ready=withContext(Dispatchers.IO){OfflineModules.all.associate{it.id to (OfflineBuildPackManager.moduleReady(context,it.id) && !OfflineBuildPackManager.hasPendingLifecycle(context,it.id))}}
    }
    fun begin(id:OfflineModuleId) {
        ModuleInstallOperations.start(context,id,sdkConsent)
    }
    pendingLifecycle?.let{(id,action)->
        val permission=if(action=="remove")lifecycleStates[id]?.remove else lifecycleStates[id]?.rollback
        AlertDialog(onDismissRequest={pendingLifecycle=null},title={Text(if(action=="remove")"Modülü kaldırmayı onayla" else "Önceki sürüme dönmeyi onayla")},
            text={Text("${OfflineModules.spec(id).title}: sahiplik, bağımlılar, aktif kullanım ve bütünlük tekrar kontrol edilir. Kullanıcı kaynakları/anahtarları korunur. Rootfs son geçişten sonra değişmişse bütün ortam geri alma engellenir. Runtime ve Portable kaldırılması dosyaları arşivler; disk boşalttığı varsayılmaz.")},
            confirmButton={TextButton(enabled=!busy && permission?.allowed==true,onClick={pendingLifecycle=null;ModuleInstallOperations.lifecycle(context,id,action)}){Text("Onayla")}},
            dismissButton={TextButton(onClick={pendingLifecycle=null}){Text("Vazgeç")}})
    }
    if(confirmPrune)AlertDialog(onDismissRequest={confirmPrune=false},title={Text("Kullanılmayan eski sürümleri temizle")},text={Text("Aktif ortam, en son geri alma sürümü ve modül sahiplik kayıtlarının kullandığı snapshot'lar korunur. Daha eski geri alma geçmişi kısalabilir. Yalnız hash envanteri doğrulanan özel snapshot kopyaları silinir; kullanıcı projeleri silinmez.")},confirmButton={TextButton(enabled=!busy,onClick={confirmPrune=false;scope.launch{detail=withContext(Dispatchers.IO){runCatching{OfflineBuildPackManager.pruneUnusedSnapshots(context)}.fold({"${it/1024/1024} MiB eski snapshot temizlendi."},{"Temizleme engellendi: ${it.message}"})}}}){Text("Onayla ve temizle")}},dismissButton={TextButton(onClick={confirmPrune=false}){Text("Vazgeç")}})
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
                val lifecycle=lifecycleStates[module.id]
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
                    Text(if(busy && state!=null && state.phase !in setOf(ModulePhase.READY,ModulePhase.REMOVED))state.phase.turkishLabel() else if(!ready.containsKey(module.id))"Bütünlük doğrulanıyor..." else if(installed)"Hazır • gerçek bütünlük kontrolü geçti" else if(module.id in availableUpdates)"Güncelleme gerekli • kayıtlı ortam korunuyor" else state?.phase?.turkishLabel()?:"Kurulu değil / doğrulanmadı")
                    if(state!=null && (busy || !installed)) {
                        Text(state.detail)
                        if(state.phase==ModulePhase.DOWNLOADING) {
                            val pct=state.percent
                            if(pct!=null) {LinearProgressIndicator(progress={pct/100f},modifier=Modifier.fillMaxWidth());Text("%$pct • ${state.bytesDownloaded/1024/1024} / ${state.bytesTotal!!/1024/1024} MiB")} else {LinearProgressIndicator(modifier=Modifier.fillMaxWidth());Text("${state.bytesDownloaded/1024/1024} MiB • toplam boyut bilinmiyor")}
                            Text("Hız: "+(state.speedBytesPerSecond?.let{"%.1f MiB/sn".format(it/1024/1024)}?:"Hesaplanıyor..."))
                            Text("Ortalama: "+(state.averageSpeedBytesPerSecond?.let{"%.1f MiB/sn".format(it/1024/1024)}?:"Hesaplanıyor..."))
                            Text(if(state.speedBytesPerSecond!=null)"Bağlantı: veri aktarılıyor • hız hareketli ortalamadır" else "Yeni bayt bekleniyor; bağlantı durumu henüz doğrulanmadı")
                            Text("Kalan: "+(state.estimatedRemainingMs?.let{"${it/1000} sn"}?:"Hesaplanıyor...")+" • Geçen: ${state.elapsedMs/1000} sn")
                        } else if(busy && state.phase !in setOf(ModulePhase.READY,ModulePhase.ERROR,ModulePhase.CANCELED))LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
                    }
                    Button(enabled=!busy && ready.containsKey(module.id) && unsupported==null && (installed || lifecycle?.installedVersion==null || lifecycle.update.allowed),onClick={if(installed){scope.launch{val good=withContext(Dispatchers.IO){OfflineBuildPackManager.moduleReady(context,module.id)};detail=if(good)"Bütünlük doğrulandı." else "Bozuk kurulum; hazır değil."}}else chosen=module.id}){Text(if(installed)"Doğrula" else if(state?.phase in setOf(ModulePhase.PAUSED,ModulePhase.WAITING_CONNECTION))"Devam et" else if(state?.phase==ModulePhase.ERROR)"Yeniden dene" else "Kurulum ayrıntıları")}
                    if(busy && state!=null && state.phase !in setOf(ModulePhase.READY,ModulePhase.ERROR)){ if((module.id in setOf(OfflineModuleId.NODE_WEB,OfflineModuleId.WINDOWS_PORTABLE,OfflineModuleId.LINUX_RUNTIME) || state.detail.startsWith("Doğrulanmış native aktarım")) && state.phase==ModulePhase.DOWNLOADING)OutlinedButton(onClick={ModuleInstallOperations.pause()}){Text("Duraklat")};OutlinedButton(onClick={ModuleInstallOperations.cancel()}){Text("İptal et")} }
                    if(lifecycle!=null) {
                        Text("Sahiplik envanteri: ${lifecycle.ownedFiles} dosya • generation: ${lifecycle.generation?:"doğrulanmış kayıt yok"}")
                        lifecycle.previousVersion?.let{Text("Önceki doğrulanmış sürüm: $it")}
                        lifecycle.minimumStagingBytes?.let{Text("İzole işlem için en az ${it/1024/1024} MiB boş alan; yeni arşiv boyutu ayrıca gerekir.")}
                        OutlinedButton(enabled=!busy && lifecycle.remove.allowed,onClick={pendingLifecycle=module.id to "remove"}){Text(if(module.id in setOf(OfflineModuleId.LINUX_RUNTIME,OfflineModuleId.WINDOWS_PORTABLE))"Kaldır • özel arşivde koru" else "Sahiplik doğrulamasıyla kaldır")}
                        lifecycle.remove.reason?.let{Text("Kaldırma: $it",style=MaterialTheme.typography.bodySmall)}
                        OutlinedButton(enabled=!busy && lifecycle.rollback.allowed,onClick={pendingLifecycle=module.id to "rollback"}){Text("Doğrulanmış önceki generation'a dön")}
                        lifecycle.rollback.reason?.let{Text("Geri alma: $it",style=MaterialTheme.typography.bodySmall)}
                        Text("Kaldırma: ${if(lifecycle.remove.allowed)"güvenli yol mevcut" else "güvenlik nedeniyle kapalı"} • geri alma: ${if(lifecycle.rollback.allowed)"doğrulanmış" else "kapalı"}",style=MaterialTheme.typography.bodySmall)
                    }
                    installedVersions[module.id]?.let{Text("Kayıtlı sürüm: $it")}
                    OutlinedButton(enabled=!busy && lifecycle?.update?.allowed==true,onClick={chosen=module.id}){Text(if(module.id in availableUpdates)"Gömülü doğrulanmış tarif ile güncelle / onar" else "Güncel • doğrulanmış yeni tarif yok")}
                    lifecycle?.update?.reason?.let{Text("Güncelleme: $it",style=MaterialTheme.typography.bodySmall)}
                    if(module.id!=OfflineModuleId.WINDOWS_PORTABLE)Text("Genel dosya silme kapalı. Son doğrulanmış izole sürüm, bağımlılık kullanımı yoksa geri alınabilir; daha eski modüller önce bağımlılık sırasıyla çözülmelidir.",style=MaterialTheme.typography.bodySmall)
                }}
            }
            if(ready[OfflineModuleId.LINUX_RUNTIME]==true)item{OutlinedButton(enabled=!busy && BuildModuleUsage.mutationBlockedReason()==null && pruneBytes!=null,onClick={confirmPrune=true}){Text("Eski sürümleri temizle • "+(pruneBytes?.let{"${it/1024/1024} MiB doğrulanmış dosya"}?:"boyut doğrulanamadı"))}}
            pruneReason?.let{reason->item{Text("Eski sürüm temizleme kapalı: $reason")}}
            if(project!=null && !FastHtmlApkPolicy.isCandidate(project))item { ProjectDependencyPreparationCard(project) }
            if(ready[OfflineModuleId.WINDOWS_PORTABLE]==true)item { WindowsModuleSmokeCard() }
            item { Text("Toplam işlem: "+if(busy)"devam ediyor; kurulum iş miktarı ölçülemediği için toplam yüzde yok" else "bekliyor / son modül durumlarını inceleyin") }
            item { Text("Kullanıcının başlattığı kurulum Android veri aktarımı servisiyle izlenir. Android zaman sınırı veya süreç ölümü işlemi durdurabilir. Yeniden açınca kayıt geri yüklenir; devam için onay gerekir, dosyalar yeniden doğrulanır.") }
            if(journalError.isNotBlank())item{Text(journalError)}
            if(history.isNotEmpty())item{Column {Text("Son işlem kayıtları (hazır olma kanıtı değildir)");history.takeLast(8).forEach{Text(it)}}}
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
    ModulePhase.REMOVING -> "Kaldırma doğrulanıyor"
    ModulePhase.REMOVED -> "Kaldırıldı"
    ModulePhase.ROLLING_BACK -> "Önceki generation'a dönülüyor"
    ModulePhase.PAUSED -> "Duraklatıldı"
    ModulePhase.WAITING_CONNECTION -> "Bağlantı bekleniyor; yeniden deneyin"
    ModulePhase.RECONNECTING -> "Yeniden bağlanıyor"
    ModulePhase.CANCELED -> "İptal edildi"
    ModulePhase.ERROR -> "Hata oluştu"
}
