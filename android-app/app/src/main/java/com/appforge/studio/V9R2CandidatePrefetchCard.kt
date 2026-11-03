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

/** Optional signed R2 prefetch feeds the existing installer; never marks READY. */
@Composable internal fun V9R2CandidatePrefetchCard(
    selected: OfflineModuleId,
    installEnabled: Boolean,
    onBusyChange: (Boolean)->Unit,
    onInstall: (OfflineModuleId)->Unit
) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val arch=remember {
        when {
            Build.SUPPORTED_ABIS.contains("arm64-v8a") -> "arm64"
            Build.SUPPORTED_ABIS.contains("x86_64") -> "amd64"
            else -> null
        }
    }
    val eligible=BuildConfig.DEBUG && selected in setOf(
        OfflineModuleId.NODE_WEB,OfflineModuleId.ANDROID_CORE,
        OfflineModuleId.PYTHON_ANDROID,OfflineModuleId.EXPO_REACT_NATIVE,
        OfflineModuleId.WINDOWS_NATIVE
    ) && arch!=null

    var showSignedOptions by remember { mutableStateOf(false) }
    var manifest by remember { mutableStateOf<ByteArray?>(null) }
    var signature by remember { mutableStateOf<ByteArray?>(null) }
    var active by remember { mutableStateOf(false) }
    var approving by remember { mutableStateOf(false) }
    var planned by remember {
        mutableStateOf<V9SelectiveR2ModuleCache.SignedSelection?>(null)
    }
    var proposed by remember {
        mutableStateOf<V9SelectiveR2ModuleCache.CacheStatus?>(null)
    }
    var plannedTarget by remember { mutableStateOf<OfflineModuleId?>(null) }
    var cacheStatus by remember { mutableStateOf("R2 önbelleği kontrol ediliyor...") }
    var status by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf("") }

    LaunchedEffect(active) { onBusyChange(active) }
    LaunchedEffect(selected) {
        status=""
        planned=null
        proposed=null
        approving=false
    }
    LaunchedEffect(selected,active) {
        if(!eligible) {
            cacheStatus="Bu modül için R2 APT paket önbelleği kullanılmıyor."
            return@LaunchedEffect
        }
        cacheStatus="R2 önbelleği SHA-256 ile kontrol ediliyor..."
        cacheStatus=withContext(Dispatchers.IO) {
            runCatching {
                val selection=V9SelectiveR2ModuleCache.preview(
                    context,setOf(selected),arch!!
                )
                val directory=com.appforge.studio.build
                    .V9SignedR2CandidateDownload.privateCacheDirectory(
                        context.noBackupFilesDir
                    )
                val found=V9SelectiveR2ModuleCache.inspectCache(
                    directory,selection.packages
                )
                "R2: ${found.existing.size}/${selection.packages.size} doğrulanmış paket; eksik ${found.missing.size}. İndirilmiş paketler yeniden alınmaz."
            }.getOrElse{
                "R2 önbellek doğrulaması başarısız: ${it.message}"
            }
        }
    }

    val chooseManifest=rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if(uri!=null && !active) scope.launch {
            manifest=null
            status="Manifest okunuyor..."
            runCatching{
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use {
                        V9R2BundleReader.bounded(
                            it,V9R2BundleReader.MAX_MANIFEST
                        )
                    } ?: error("Manifest okunamadı")
                }
            }.onSuccess{
                manifest=it
                status="Manifest alındı; Ed25519 imzası kurulum öncesi doğrulanacak."
            }.onFailure{
                status="Manifest reddedildi: ${it.message}"
            }
        }
    }

    val chooseSignature=rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if(uri!=null && !active) scope.launch {
            signature=null
            status="İmza okunuyor..."
            runCatching{
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use {
                        V9R2BundleReader.bounded(
                            it,V9R2BundleReader.SIGNATURE_BYTES
                        )
                    } ?: error("İmza okunamadı")
                }.also{
                    require(it.size==V9R2BundleReader.SIGNATURE_BYTES)
                }
            }.onSuccess{
                signature=it
                status="İmza dosyası alındı."
            }.onFailure{
                status="İmza reddedildi: ${it.message}"
            }
        }
    }

    if(approving && planned!=null &&
       proposed!=null && plannedTarget!=null) {
        val p=planned!!
        val q=proposed!!
        val target=plannedTarget!!
        AlertDialog(
            onDismissRequest={if(!active)approving=false},
            title={Text("İmzalı R2 paket aktarımını onayla")},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("Modül: ${OfflineModules.spec(target).title}")
                    Text("Önbellekte doğrulanan: ${q.existing.size}/${p.objects.size} paket")
                    Text("Ağdan alınacak: ${q.missing.size} paket • ${q.networkBytes} bayt")
                    Text("Yalnız doğrulanmış VPN'siz ve ölçüsüz Wi-Fi. İmza/SHA-256 kontrolü ve normal APT kurulumu ayrı doğrulanır.")
                }
            },
            confirmButton={
                Button(enabled=!active,onClick={
                    val m=manifest?:return@Button
                    val sig=signature?:return@Button
                    val architecture=arch?:return@Button
                    approving=false
                    active=true
                    status="İmzalı R2 paketleri indiriliyor ve doğrulanıyor..."
                    scope.launch {
                        val work=coroutineContext
                        try {
                            val count=withContext(Dispatchers.IO) {
                                check(DownloadNetwork.allowed(
                                    DownloadNetworkPolicy.WIFI_PREFERRED
                                )) {
                                    "R2 için ölçüsüz, VPN'siz doğrulanmış Wi-Fi gerekli"
                                }
                                DownloadNetwork.withPolicy(
                                    DownloadNetworkPolicy.WIFI_PREFERRED
                                ) {
                                    V9SelectiveR2ModuleCache.fetchSelectedWithConsent(
                                        context,m,sig,2026101801L,
                                        setOf(target),architecture,
                                        userApproved = true,
                                        canceled = {!work.isActive},
                                        onProgress={index,total,done,bytes->
                                            scope.launch{
                                                progress="Paket $index/$total • $done/$bytes bayt"
                                            }
                                        }
                                    ).size
                                }
                            }
                            val after=withContext(Dispatchers.IO) {
                                V9SelectiveR2ModuleCache.status(context,p)
                            }
                            check(after.missing.isEmpty() &&
                                  after.networkGetRequests==0L)
                            status="$count R2 paketi SHA-256 doğrulandı. Kurulum onayı açılıyor."
                            onInstall(target)
                        } catch(e:CancellationException) {
                            status="R2 indirme durdu; doğrulanmış paketler korunuyor."
                            throw e
                        } catch(e:Exception) {
                            status="R2 aktarımı engellendi: ${e.message}"
                        } finally {
                            active=false
                            planned=null
                            proposed=null
                            plannedTarget=null
                            progress=""
                        }
                    }
                }){Text("Paketleri indir ve kuruluma geç")}
            },
            dismissButton={
                TextButton(onClick={approving=false}){Text("Vazgeç")}
            }
        )
    }

    Text(cacheStatus,style=MaterialTheme.typography.bodySmall)

    if(eligible) {
        TextButton(
            enabled=!active,
            onClick={showSignedOptions=!showSignedOptions}
        ) {
            Text(if(showSignedOptions)
                "İmzalı R2 kaynaklarını gizle"
                else "İmzalı R2 kaynakları (isteğe bağlı)")
        }
        if(showSignedOptions) {
            Text(
                "Yeni R2 nesnelerini indirmek için imzalı manifest ve 64 baytlık imza seçin. Dosyalar yoksa mevcut imzalı APT kurulum yolu çalışır.",
                style=MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled=!active,
                    onClick={chooseManifest.launch(arrayOf("*/*"))}
                ){Text(if(manifest==null)"Manifest seç" else "Manifest ✓")}
                OutlinedButton(
                    enabled=!active,
                    onClick={chooseSignature.launch(arrayOf("*/*"))}
                ){Text(if(signature==null)"İmza seç" else "İmza ✓")}
            }
        }
    }

    val incompleteR2=eligible &&
        (manifest!=null || signature!=null) &&
        (manifest==null || signature==null)

    Button(
        enabled=installEnabled && !active && !incompleteR2,
        modifier=Modifier.fillMaxWidth(),
        onClick={
            val target=selected
            val m=manifest
            val sig=signature
            val architecture=arch

            if(eligible && m!=null && sig!=null &&
               architecture!=null) {
                active=true
                status="Ed25519 imzası, SHA-256 katalog ve R2 önbelleği doğrulanıyor..."
                scope.launch {
                    try {
                        val pair=withContext(Dispatchers.IO) {
                            val p=V9SelectiveR2ModuleCache.signedPlan(
                                context,m,sig,2026101801L,
                                setOf(target),architecture
                            )
                            p to V9SelectiveR2ModuleCache.status(context,p)
                        }
                        if(pair.second.missing.isEmpty()) {
                            status="İmzalı R2 önbelleği doğrulandı; yeni indirme yok."
                            onInstall(target)
                        } else {
                            planned=pair.first
                            proposed=pair.second
                            plannedTarget=target
                            approving=true
                            status="İmza doğrulandı; ${pair.second.missing.size} eksik paket için ağ izni bekleniyor."
                        }
                    } catch(e:Exception) {
                        status="R2 imza veya paket kontrolü başarısız: ${e.message}"
                    } finally {
                        active=false
                    }
                }
            } else onInstall(target)
        }
    ){Text("Paketleri indir, doğrula ve kur")}

    if(incompleteR2)
        Text(
            "R2 için manifest ve imza birlikte seçilmeli.",
            color=MaterialTheme.colorScheme.error
        )

    if(active)
        LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
    if(progress.isNotBlank())
        Text(progress,style=MaterialTheme.typography.bodySmall)
    if(status.isNotBlank())
        Text(status,style=MaterialTheme.typography.bodySmall)
}
