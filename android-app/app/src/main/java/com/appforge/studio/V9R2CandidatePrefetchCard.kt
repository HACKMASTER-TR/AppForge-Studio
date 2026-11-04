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
    selected: OfflineModuleId, installEnabled: Boolean,
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
    var manifest by remember { mutableStateOf<ByteArray?>(null) }
    var signature by remember { mutableStateOf<ByteArray?>(null) }
    var active by remember { mutableStateOf(false) }
    var sourceReady by remember { mutableStateOf(false) }
    var showSource by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var cacheStatus by remember { mutableStateOf("") }
    LaunchedEffect(active) { onBusyChange(active) }
    DisposableEffect(Unit) { onDispose { onBusyChange(false) } }
    LaunchedEffect(selected,active) {
        if(active || !aptSource || architecture==null)return@LaunchedEffect
        val result=withContext(Dispatchers.IO) {
            val authorized=runCatching{com.appforge.studio.build.V9R2ReleaseStore.load(context)}.isSuccess
            val counts=runCatching {
                val selection=V9SelectiveR2ModuleCache.preview(context,setOf(previewModule),architecture)
                val dir=com.appforge.studio.build.V9SignedR2CandidateDownload.privateCacheDirectory(context.noBackupFilesDir)
                val found=V9SelectiveR2ModuleCache.inspectCache(dir,selection.packages)
                "Paket önbelleği: ${found.existing.size}/${selection.packages.size} doğrulanmış .deb; ${found.missing.size} eksik."
            }.getOrElse{"Önbellek engeli: ${it.message}"}
            authorized to counts
        }
        sourceReady=result.first
        cacheStatus=result.second
    }
    LaunchedEffect(manifest,signature) {
        val m=manifest;val sig=signature
        if(m!=null && sig!=null) {
            active=true
            try {
                withContext(Dispatchers.IO){com.appforge.studio.build.V9R2ReleaseStore.save(context,m,sig)}
                sourceReady=true
                status="Ed25519 R2 kaynağı doğrulandı ve kaydedildi; indirme başlamadı."
            } catch(e:Exception){sourceReady=false;status="R2 kaynağı reddedildi: ${e.message}"}
            finally{active=false}
        }
    }
    val chooseManifest=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if(uri!=null && !active)scope.launch {
            active=true;manifest=null
            try {
                manifest=withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use{V9R2BundleReader.bounded(it,V9R2BundleReader.MAX_MANIFEST)}
                        ?:error("Manifest okunamadı")
                }
                status="Manifest seçildi; özgün baytlar Ed25519 ile doğrulanacak."
            } catch(e:Exception){status="Manifest reddedildi: ${e.message}"}
            finally{active=false}
        }
    }
    val chooseSignature=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if(uri!=null && !active)scope.launch {
            active=true;signature=null
            try {
                signature=withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use{V9R2BundleReader.bounded(it,V9R2BundleReader.SIGNATURE_BYTES)}
                        ?.also{require(it.size==64)}?:error("64 baytlık imza okunamadı")
                }
                status="İmza seçildi."
            } catch(e:Exception){status="İmza reddedildi: ${e.message}"}
            finally{active=false}
        }
    }
    if(aptSource) {
        Text(cacheStatus,style=MaterialTheme.typography.bodySmall)
        Text("Kaynak: R2 ve doğrulanmış yerel önbellek. Ubuntu Snapshot yalnız sabit köken kaydıdır.",style=MaterialTheme.typography.bodySmall)
        Text("APT için ayrıca 4 Ubuntu imzası ve 16 Packages indeksi doğrulanır. Eksik metadata kurulumu engeller; paket sayısı READY anlamına gelmez.",style=MaterialTheme.typography.bodySmall)
        TextButton(enabled=!active,onClick={showSource=!showSource}) {
            Text(if(sourceReady)"İmzalı R2 kaynağını değiştir" else "İmzalı R2 kaynağını seç")
        }
        if(showSource || !sourceReady)Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled=!active,onClick={chooseManifest.launch(arrayOf("*/*"))}){Text(if(manifest==null)"Manifest seç" else "Manifest ✓")}
            OutlinedButton(enabled=!active,onClick={chooseSignature.launch(arrayOf("*/*"))}){Text(if(signature==null)"İmza seç" else "İmza ✓")}
        }
    }
    val partial=(manifest!=null || signature!=null) && (manifest==null || signature==null)
    val requiresSource=aptSource && selected!=OfflineModuleId.NODE_WEB
    Button(enabled=installEnabled && !active && !partial &&
        (!requiresSource || sourceReady || manifest!=null && signature!=null),
        modifier=Modifier.fillMaxWidth(),onClick={
            val target=selected;val m=manifest;val sig=signature
            active=true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        if(m!=null && sig!=null)com.appforge.studio.build.V9R2ReleaseStore.save(context,m,sig)
                        else if(requiresSource)com.appforge.studio.build.V9R2ReleaseStore.load(context)
                    }
                    status="Kaynak doğrulandı. Tek işlem indirme, doğrulama, kurulum ve fonksiyon testini yürütecek."
                    onInstall(target)
                } catch(e:Exception){status="R2 kaynağı reddedildi: ${e.message}"}
                finally{active=false}
            }
        }){Text("Paketleri indir, doğrula ve kur")}
    if(partial)Text("Manifest ve imza birlikte seçilmeli.",color=MaterialTheme.colorScheme.error)
    if(requiresSource && !sourceReady && manifest==null)Text("İmzalı R2 sürümü gerekli. Eksik Packages indeksleri varsa işlem güvenle durur.",style=MaterialTheme.typography.bodySmall)
    if(active)LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
    if(status.isNotBlank())Text(status,style=MaterialTheme.typography.bodySmall)
}
