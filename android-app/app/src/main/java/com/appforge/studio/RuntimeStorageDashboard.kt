package com.appforge.studio

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.appforge.studio.build.*
import kotlinx.coroutines.*

/** Read-only scan is explicit, cancellable and never changes module readiness. */
@Composable
internal fun RuntimeStorageDashboard(onChanged:()->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var report by remember{mutableStateOf<RuntimeStorageBudget.Report?>(null)}
    var plan by remember{mutableStateOf<RuntimeGenerationStore.CleanupPlan?>(null)}
    var work by remember{mutableStateOf<Job?>(null)}
    var error by remember{mutableStateOf("")}
    var confirm by remember{mutableStateOf(false)}
    var outcome by remember{mutableStateOf("")}
    val busy=work?.isActive==true
    fun amount(bytes:Long)="%.2f GiB".format(bytes/1073741824.0)
    fun scan() {
        work=scope.launch {
            error=""
            try {
                val job=currentCoroutineContext().job
                val result=withContext(Dispatchers.IO) {
                    val measured=OfflineBuildPackManager.storageReport(context){job.ensureActive()}
                    val cleanup=OfflineBuildPackManager.storageCleanupPlan(context){job.ensureActive()}
                    measured to cleanup
                }
                report=result.first;plan=result.second
            } catch(e:CancellationException){throw e}
            catch(e:Exception){error=e.message?:"Depolama incelemesi tamamlanamadı; dosyalar korundu."}
            finally {work=null}
        }
    }
    Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Disk alanı • güvenli depolama",style=MaterialTheme.typography.titleMedium)
        Text("Kurulum engellense de mevcut HAZIR modüller, dosyalar ve çevrimdışı derleme kullanılabilir.")
        report?.let { r ->
            listOf("Aktif rootfs" to r.active,"Geri alma için korunuyor" to r.rollback,
                "Karantina • korunuyor" to r.quarantine,"Sahipsiz/şüpheli ve staging" to r.unknown,
                "İndirilen arşiv önbelleği" to r.cache).forEach { (label,u) ->
                Text("$label: ${amount(u.logicalBytes)} • ${u.files} öğe")
                Text("Ayrılmış bloklar: ${u.allocatedBytes?.let(::amount)?:"ölçülemedi"}",style=MaterialTheme.typography.bodySmall)
            }
            Text("Boş alan: ${amount(r.available)} • staging + güvenlik payı: ${amount(r.required)}")
            Text("Modüle ait azami genişleme rezervi kurulum öncesinde ayrıca kontrol edilir.")
            Text("Eski kopyalar: ${r.obsoleteSnapshotCount} • " +
                "${r.obsoleteSnapshotBytes} mantıksal bayt (otomatik silinmez)")
            Text("En fazla 15 eski kopya; kurulum öncesi gerçek boş alan ve 256 MiB güvenlik payı ayrıca denetlenir.")
            Text("Korunan geri alma konumu: ${r.retainedCopies}")
            r.blockedReason?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            Text("Onayla temizlenebilir: ${amount(plan?.logicalBytes?:0)} (mantıksal boyut)")
            Text("Ayrılmış bloklar paylaşılan reflink alanını içerebilir; boşalacak fiziksel alan garantisi değildir.",style=MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled=!busy,onClick={scan()}){Text("İncele / yeniden ölç")}
            if(busy)TextButton(onClick={work?.cancel()}){Text("İptal et")}
        }
        Button(enabled=!busy && plan?.candidates?.isNotEmpty()==true && BuildModuleUsage.mutationBlockedReason()==null,
            onClick={confirm=true}){Text("Temizleme planını incele")}
        if(outcome.isNotBlank())Text(outcome)
        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
    }}
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Doğrulanmış kopyaları temizle?")},
        text={Column {
            Text("Aktif ortam, projeler, önbellek, gerekli geri alma ve şüpheli içerikler korunur.")
            Text("${plan?.candidates?.size?:0} sahipliği doğrulanmış gereksiz kopya • ${amount(plan?.logicalBytes?:0)}")
            plan?.candidates?.keys?.take(20)?.forEach{Text(it)}
            Text("Onay sonrası plan yeniden doğrulanır. Değişiklik varsa işlem durur.")
        }},confirmButton={TextButton(onClick={
            val approved=plan?:return@TextButton
            confirm=false
            work=scope.launch {
                try {
                    val before=report?.available
                    val bytes=withContext(Dispatchers.IO){OfflineBuildPackManager.executeDuplicateCleanup(context,approved)}
                    val job=currentCoroutineContext().job
                    report=withContext(Dispatchers.IO){OfflineBuildPackManager.storageReport(context){job.ensureActive()}}
                    plan=null
                    outcome="Silinen mantıksal içerik: ${amount(bytes)} • yeni boş alan: ${amount(report!!.available)}"+
                        (before?.let{" • ölçülen boş alan farkı: ${amount(report!!.available-it)}"}?:"")+". Kurulumu yeniden deneyebilirsiniz."
                    onChanged()
                } catch(e:CancellationException){throw e}
                catch(e:Exception){error=e.message?:"Temizlik durdu; yeniden inceleyin."}
                finally {work=null}
            }
        }){Text("Planı onayla")}},dismissButton={TextButton(onClick={confirm=false}){Text("Vazgeç")}})
}
