package com.appforge.studio

import android.app.usage.StorageStatsManager
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import com.appforge.studio.build.R69StorageInventory
import com.appforge.studio.build.BuildModuleUsage
import com.appforge.studio.build.OfflineModules
import com.appforge.studio.build.OfflineModuleId
import java.io.File

private data class AppForgeMeasuredStorage(
    val app:Long?,val data:Long?,val cache:Long?,val total:Long?,
    val diskTotal:Long,val diskFree:Long
)

/** Read-only own-package statistics. No usage-access, all-files or deletion grant. */
@Composable
internal fun AppForgeStorageDetailsScreen(onBack:()->Unit) {
    val context=LocalContext.current
    var report by remember { mutableStateOf<AppForgeMeasuredStorage?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh) {
        error=null
        try {
            report=withContext(Dispatchers.IO) {
                val disk=StatFs(context.filesDir.absolutePath)
                val stats=runCatching {
                    val service=context.getSystemService(StorageStatsManager::class.java)
                        ?:throw IllegalStateException("StorageStatsManager unavailable")
                    service.queryStatsForPackage(StorageManager.UUID_DEFAULT,
                        context.packageName,Process.myUserHandle())
                }.getOrNull()
                AppForgeMeasuredStorage(stats?.appBytes,stats?.dataBytes,
                    stats?.cacheBytes,stats?.let {it.appBytes+it.dataBytes},
                    disk.totalBytes,disk.availableBytes)
            }
        } catch(e:Exception) { error="Depolama istatistikleri okunamadı." }
    }
    fun amount(value:Long?):String = if(value==null)"Ölçülemedi"
        else "%.2f GB".format(java.util.Locale.US,value/1000000000.0)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp)) {
        TextButton(onClick=onBack){Text("← Ayarlara dön")}
        Text("AppForge Studio • Depolama",style=MaterialTheme.typography.titleLarge)
        Text("Android sisteminin yalnızca AppForge paketine ait ölçümleri. Önbellek veri boyutunun içinde bulunabilir.",
            style=MaterialTheme.typography.bodySmall)
        val r=report
        if(r==null)LinearProgressIndicator(Modifier.fillMaxWidth()) else {
            AppForgeStorageRow("Uygulama",amount(r.app))
            AppForgeStorageRow("Veri",amount(r.data))
            AppForgeStorageRow("Önbellek",amount(r.cache))
            HorizontalDivider()
            AppForgeStorageRow("Toplam kullanılan",amount(r.total))
            AppForgeStorageRow("Toplam disk",amount(r.diskTotal))
            AppForgeStorageRow("Kullanılabilir disk",amount(r.diskFree))
            if(r.app==null)Text("Sistem paket istatistikleri bu cihazda erişilebilir değil; rakam uydurulmadı.",
                style=MaterialTheme.typography.bodySmall)
        }
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        OutlinedButton(onClick={refresh++}) {Text("Yeniden ölç")}
        HorizontalDivider()
        R69StorageAnalysis()
        Text("Bu ekran yalnızca görüntüler. Uygulama verileri, modüller ve önbellek silinmez.",
            style=MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AppForgeStorageRow(label:String,value:String) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        Text(label);Text(value)
    }
}


@Composable
private fun R69StorageAnalysis() {
    val context=LocalContext.current
    var request by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableStateOf<R69StorageInventory.Progress?>(null) }
    var report by remember { mutableStateOf<R69StorageInventory.Report?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(request) {
        if(request<=0)return@LaunchedEffect
        running=true;error=null;report=null;scanProgress=null
        try {
            report=withContext(Dispatchers.IO) {
                val job=currentCoroutineContext()
                fun checkpoint() {
                    job.ensureActive()
                    check(!ModuleInstallOperations.busy.value && BuildModuleUsage.mutationBlockedReason()==null) {
                        "Etkin derleme veya kurulum var; analiz durduruldu."
                    }
                }
                checkpoint()
                R69StorageInventory.scan(File(context.applicationInfo.dataDir),
                    canceled={checkpoint()},progress={scanProgress=it})
            }
        } catch(e:CancellationException){throw e}
          catch(e:Exception){error="Analiz tamamlanamadı: "+(e.message?:"Okuma hatası")}
        finally{running=false}
    }
    fun gb(n:Long)="%.2f GB".format(java.util.Locale("tr","TR"),n/1_000_000_000.0)
    fun label(name:String)=OfflineModuleId.entries.firstOrNull { it.name==name }?.let { OfflineModules.spec(it).title } ?:name
    Text("Gelişmiş depolama analizi",style=MaterialTheme.typography.titleMedium)
    Text("Yalnızca uygulamanın özel alanını okur. 1 MiB ve üzeri araç dosyalarında SHA-256 ile içerik tekrarı araştırılır. Proje içeriği hash'lenmez.",
        style=MaterialTheme.typography.bodySmall)
    if(running) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        scanProgress?.let { p ->
            Text(if(p.hashing)"Doğrulanan dosya verisi: "+gb(p.hashedBytes) else "Taranan öğe: "+p.entries,
                style=MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick={request=0}){Text("Analizi durdur")}
    } else Button(onClick={request=(request+1).coerceAtLeast(1)},modifier=Modifier.fillMaxWidth()){
        Text(if(report==null)"Depolamayı analiz et" else "Yeniden analiz et")
    }
    error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    report?.let { r ->
        AppForgeStorageRow("Mantıksal dosya boyutu",gb(r.logicalBytes))
        AppForgeStorageRow("Dosya sayısı",r.files.toString())
        AppForgeStorageRow("Symlink (izlenmedi)",r.links.toString())
        AppForgeStorageRow("Ayrılmış blok toplamı",r.allocatedBlockSum?.let{gb(it)}?:"Ölçülemedi / kısmi")
        Text("Blok toplamı ortak reflink/hardlink alanını birden fazla sayabilir.",style=MaterialTheme.typography.bodySmall)
        Text("Modül ve alan",style=MaterialTheme.typography.titleSmall)
        r.byOwner.forEach { AppForgeStorageRow(label(it.name),gb(it.bytes)) }
        Text("Veri türüne göre",style=MaterialTheme.typography.titleSmall)
        r.byCategory.forEach { AppForgeStorageRow(it.name,gb(it.bytes)) }
        Text("İçerik tekrarı analizi",style=MaterialTheme.typography.titleSmall)
        AppForgeStorageRow("Tekrarlı içerik grupları",r.duplicateGroups.toString())
        AppForgeStorageRow("Mantıksal fazla kopyalar",gb(r.logicalExtraBytes))
        AppForgeStorageRow("Hash okunan veri",gb(r.hashedBytes))
        Text("Bu rakam silinebilir alan veya fiziksel tasarruf değildir. Kopyaların sahipliği, bağımlılıkları ve fiziksel blok paylaşımı ayrıca incelenmelidir.",
            style=MaterialTheme.typography.bodySmall)
        if(r.incomplete)Text("Kısmi analiz: "+r.skipped+" öğe atlandı veya tarama sınırına ulaşıldı.",color=MaterialTheme.colorScheme.error)
        if(r.identityUnknown)Text("Bazı inode kimlikleri okunamadı; bağlantılı kopyalar ayrı sayılmış olabilir.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={
            val summary=buildString {
                appendLine("AppForge R69 — salt okunur depolama analizi")
                appendLine("Mantıksal toplam: "+gb(r.logicalBytes))
                appendLine("Blok toplamı: "+(r.allocatedBlockSum?.let{gb(it)}?:"ölçülemedi"))
                appendLine("Dosyalar: "+r.files+" | Symlink: "+r.links)
                appendLine("Modüller:");r.byOwner.forEach { appendLine(label(it.name)+": "+gb(it.bytes)) }
                appendLine("Veri türleri:");r.byCategory.forEach { appendLine(it.name+": "+gb(it.bytes)) }
                appendLine("Tekrar grubu: "+r.duplicateGroups+" | Mantıksal fazla kopya: "+gb(r.logicalExtraBytes))
                appendLine("Hash okunan: "+gb(r.hashedBytes)+" | Kısmi: "+r.incomplete+" | Atlanan: "+r.skipped+" | Inode belirsiz: "+r.identityUnknown)
                appendLine("En büyük tekrar grupları (en fazla 20; dosya yolları yok):")
                r.duplicates.forEach { appendLine(it.copies.toString()+" x "+gb(it.bytesPerFile)+" | "+it.owners.joinToString{label(it)}) }
                appendLine("Fiziksel tasarruf veya silme izni değildir.")
            }
            context.getSystemService(android.content.ClipboardManager::class.java)?.setPrimaryClip(
                android.content.ClipData.newPlainText("AppForge depolama raporu",summary))
        },modifier=Modifier.fillMaxWidth()){Text("Analiz raporunu kopyala")}
    }
}
