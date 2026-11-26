package com.appforge.studio

import android.os.Build
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.appforge.studio.build.DownloadNetwork
import com.appforge.studio.build.DownloadNetworkPolicy
import com.appforge.studio.build.OfflineModuleId
import com.appforge.studio.build.OfflineModules
import com.appforge.studio.build.V9SelectiveR2ModuleCache
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Limits untrusted SAF inputs before the pinned Ed25519 candidate verifier sees them. */
internal object V9R2BundleReader {
    const val MAX_MANIFEST = 12 * 1024 * 1024
    const val SIGNATURE_BYTES = 64

    fun bounded(input: InputStream, limit: Int): ByteArray {
        require(limit in 1..MAX_MANIFEST)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var size = 0
        var zeros = 0
        while (true) {
            val n = input.read(buffer)
            if (n == -1) break
            if (n == 0) {
                require(++zeros <= 32) { "SAF input stalled" }
                continue
            }
            zeros = 0
            require(n <= limit - size) { "Oversized V9 candidate file" }
            output.write(buffer, 0, n)
            size += n
        }
        require(size != 0) { "Empty V9 candidate file" }
        return output.toByteArray()
    }
}

/** One service-owned workflow: source selection -> download -> verify -> install -> test. */
@Composable internal fun V9R2CandidatePrefetchCard(
    selected: OfflineModuleId, installEnabled: Boolean, showCacheDetails: Boolean,
    onBusyChange: (Boolean)->Unit, onInstall: (OfflineModuleId)->Unit
) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val architecture=remember {
        if(Build.SUPPORTED_ABIS.contains("arm64-v8a"))"arm64"
        else if(Build.SUPPORTED_ABIS.contains("x86_64"))"amd64" else null
    }
    val aptSource=selected !in setOf(OfflineModuleId.FAST_HTML,OfflineModuleId.WINDOWS_PORTABLE,OfflineModuleId.LINUX_RUNTIME)
    val previewModule=when(selected) {
        OfflineModuleId.RUST->OfflineModuleId.PYTHON_ANDROID
        OfflineModuleId.RUST_ANDROID_NDK->OfflineModuleId.EXPO_REACT_NATIVE
        else->selected
    }
    var active by remember { mutableStateOf(false) }
    var sourceReady by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var cacheStatus by remember(selected) { mutableStateOf("") }
    LaunchedEffect(active) { onBusyChange(active) }
    DisposableEffect(Unit) { onDispose { onBusyChange(false) } }
    LaunchedEffect(aptSource,active) {
        if(active || !aptSource)return@LaunchedEffect
        sourceReady=withContext(Dispatchers.IO) {
            runCatching{com.appforge.studio.build.V9R2ReleaseStore.load(context)}.isSuccess
        }
    }
    LaunchedEffect(selected,showCacheDetails,active) {
        if(active || !showCacheDetails || !aptSource || architecture==null)return@LaunchedEffect
        cacheStatus=withContext(Dispatchers.IO) {
            runCatching {
                val selection=V9SelectiveR2ModuleCache.preview(context,setOf(previewModule),architecture)
                val dir=com.appforge.studio.build.V9SignedR2CandidateDownload.privateCacheDirectory(context.noBackupFilesDir)
                val found=V9SelectiveR2ModuleCache.inspectCache(dir,selection.packages)
                "Paket önbelleği: ${found.existing.size}/${selection.packages.size} doğrulanmış .deb; ${found.missing.size} eksik."
            }.getOrElse{"Önbellek engeli: ${it.message}"}
        }
    }
    if(aptSource) {
        if(sourceReady && showCacheDetails) {
            Text(
                "İmzalı R2 kaynağı hazır • Ed25519 doğrulandı. " +
                    "Manuel manifest ve imza seçimi gerekmiyor.",
                style=MaterialTheme.typography.bodySmall
            )
        }
        if(showCacheDetails)Text(cacheStatus.ifBlank{"Paket önbelleği denetleniyor…"},style=MaterialTheme.typography.bodySmall)
        if(showCacheDetails)Text("Kaynak: R2 ve doğrulanmış yerel önbellek. Ubuntu Snapshot yalnız sabit köken kaydıdır.",style=MaterialTheme.typography.bodySmall)
        if(showCacheDetails)Text("APT için ayrıca 4 Ubuntu imzası ve 16 Packages indeksi doğrulanır. Eksik metadata kurulumu engeller; paket sayısı READY anlamına gelmez.",style=MaterialTheme.typography.bodySmall)
    }
    val requiresSource=aptSource && selected!=OfflineModuleId.NODE_WEB
    Button(enabled=installEnabled && !active && (!requiresSource || sourceReady),
        modifier=Modifier.fillMaxWidth(),onClick={
            val target=selected
            active=true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        if(requiresSource)com.appforge.studio.build.V9R2ReleaseStore.load(context)
                    }
                    status="İmzalı R2 doğrulandı. İndir, doğrula, kur ve test et."
                    onInstall(target)
                } catch(e:Exception){status="R2 doğrulaması başarısız: ${e.message}"}
                finally{active=false}
            }
        }){Text("İndir ve Kur")}
    if(requiresSource && !sourceReady)Text(
        "İmzalı R2 sürümü eksik; güvenli kurulum başlamadı.",
        style=MaterialTheme.typography.bodySmall)
    if(active)LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
    if(status.isNotBlank())Text(status,style=MaterialTheme.typography.bodySmall)
}
