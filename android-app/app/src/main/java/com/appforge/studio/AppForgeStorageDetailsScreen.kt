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
