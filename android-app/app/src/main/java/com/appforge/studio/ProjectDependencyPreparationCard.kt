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

@Composable internal fun ProjectDependencyPreparationCard(draft:ProjectDraft) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
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
