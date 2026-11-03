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

/** Debug-only, explicit-consent, SHA-verified prefetch. Does not install or mark READY. */
@Composable internal fun V9R2CandidatePrefetchCard() {
    if (!BuildConfig.DEBUG) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val arch = remember {
        when {
            Build.SUPPORTED_ABIS.contains("arm64-v8a") -> "arm64"
            Build.SUPPORTED_ABIS.contains("x86_64") -> "amd64"
            else -> null
        }
    }
    val moduleIds = remember { listOf(
        OfflineModuleId.NODE_WEB, OfflineModuleId.ANDROID_CORE,
        OfflineModuleId.PYTHON_ANDROID, OfflineModuleId.EXPO_REACT_NATIVE,
        OfflineModuleId.WINDOWS_NATIVE
    ) }
    var chosen by remember { mutableStateOf(OfflineModuleId.NODE_WEB) }
    var manifest by remember { mutableStateOf<ByteArray?>(null) }
    var signature by remember { mutableStateOf<ByteArray?>(null) }
    var pending by remember { mutableStateOf<V9SelectiveR2ModuleCache.SignedSelection?>(null) }
    var proposed by remember { mutableStateOf<V9SelectiveR2ModuleCache.CacheStatus?>(null) }
    var approving by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("Dosyaları seçin; üretim kurulumu kapalıdır.") }

    val chooseManifest = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        pending = null; proposed = null; manifest = null
        if (uri != null && !active) scope.launch {
            result = "Manifest okunuyor..."
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use {
                        V9R2BundleReader.bounded(it, V9R2BundleReader.MAX_MANIFEST)
                    } ?: error("Manifest dosyası açılamadı")
                }
            }.onSuccess { manifest = it; result = "Manifest okundu; imza ayrıca doğrulanmalı." }
                .onFailure { result = "Manifest reddedildi: ${it.message}" }
        }
    }
    val chooseSignature = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        pending = null; proposed = null; signature = null
        if (uri != null && !active) scope.launch {
            result = "İmza okunuyor..."
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use {
                        V9R2BundleReader.bounded(it, V9R2BundleReader.SIGNATURE_BYTES)
                    } ?: error("İmza dosyası açılamadı")
                }.also { require(it.size == V9R2BundleReader.SIGNATURE_BYTES) }
            }.onSuccess { signature = it; result = "İmza okundu; yalnızca sabit Android açık anahtarı kabul edilir." }
                .onFailure { result = "İmza reddedildi: ${it.message}" }
        }
    }

    if (approving && pending != null && proposed != null) {
        val plan = pending!!
        val status = proposed!!
        AlertDialog(
            onDismissRequest = { if (!active) approving = false },
            title = { Text("İmzalı R2 indirmesini onayla") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Modül: ${OfflineModules.spec(chosen).title} / $arch")
                    Text("İmzalı içerik: ${plan.objects.size} dosya")
                    Text("Önbellekte: ${status.existing.size} dosya")
                    Text("Ağdan gerekli: ${status.networkBytes} bayt (${status.networkGetRequests} tahmini GET)")
                    Text("Yalnızca doğrulanmış, ölçüsüz ve VPN'siz Wi-Fi kullanılacak. İndirilen dosyalar kurulmaz veya HAZIR sayılmaz.")
                }
            },
            confirmButton = {
                Button(enabled = !active, onClick = {
                    val m = manifest ?: return@Button
                    val s = signature ?: return@Button
                    if (arch == null) return@Button
                    approving = false
                    active = true
                    result = "İndirme başlatılıyor; bu işlem kurulum değildir."
                    scope.launch {
                        val operation = coroutineContext
                        try {
                            val count = withContext(Dispatchers.IO) {
                                check(DownloadNetwork.allowed(DownloadNetworkPolicy.WIFI_PREFERRED)) {
                                    "Doğrulanmış, VPN'siz ve ölçüsüz Wi-Fi bulunamadı"
                                }
                                DownloadNetwork.withPolicy(DownloadNetworkPolicy.WIFI_PREFERRED) {
                                    V9SelectiveR2ModuleCache.fetchSelectedWithConsent(
                                        context, m, s, 2026101801L,
                                        setOf(chosen), arch, userApproved = true,
                                        canceled = { !operation.isActive },
                                        onProgress = { index, total, done, bytes ->
                                            scope.launch { progress = "Dosya $index/$total • $done/$bytes bayt" }
                                        }
                                    ).size
                                }
                            }
                            val finalStatus = withContext(Dispatchers.IO) {
                                V9SelectiveR2ModuleCache.status(context, plan)
                            }
                            check(finalStatus.missing.isEmpty() && finalStatus.networkGetRequests == 0L)
                            result = "$count dosya SHA-256 doğrulandı ve özel önbelleğe alındı. Kurulum yapılmadı."
                        } catch (e: CancellationException) {
                            result = "İndirme durduruldu. Mevcut doğrulanmış dosyalar korunur."
                            throw e
                        } catch (e: Exception) {
                            result = "İndirme engellendi: ${e.message ?: e.javaClass.simpleName}"
                        } finally {
                            active = false
                            progress = ""
                            pending = null
                            proposed = null
                        }
                    }
                }) { Text("Yalnızca dosyaları indir") }
            },
            dismissButton = { TextButton(onClick = { approving = false }) { Text("Vazgeç") } }
        )
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("V9 İmzalı R2 Paket Önbelleği • Debug", style = MaterialTheme.typography.titleMedium)
            Text("Ed25519 imzası doğrulanmış aday paketleri kullanıcı onayıyla indirir. Üretim yönlendirmesi, paket kurulumu ve HAZIR geçişi kapalıdır.")
            Text("Mimari: ${arch ?: "DESTEKLENMİYOR"}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !active, onClick = { chooseManifest.launch(arrayOf("*/*")) }) {
                    Text(if (manifest == null) "Manifest seç" else "Manifest ✓")
                }
                OutlinedButton(enabled = !active, onClick = { chooseSignature.launch(arrayOf("*/*")) }) {
                    Text(if (signature == null) "İmza seç" else "İmza ✓")
                }
            }
            Text("Modül seçimi (yalnızca bir modül):")
            moduleIds.forEach { id ->
                FilterChip(
                    selected = chosen == id,
                    enabled = !active,
                    onClick = { chosen = id; pending = null; proposed = null },
                    label = { Text(OfflineModules.spec(id).title) }
                )
            }
            Button(enabled = !active && arch != null && manifest != null && signature != null, onClick = {
                val m = manifest ?: return@Button
                val s = signature ?: return@Button
                val a = arch ?: return@Button
                active = true
                result = "Sabit Ed25519 açık anahtarı ve önbellek doğrulanıyor..."
                scope.launch {
                    try {
                        val (plan, status) = withContext(Dispatchers.IO) {
                            val plan = V9SelectiveR2ModuleCache.signedPlan(
                                context, m, s, 2026101801L, setOf(chosen), a
                            )
                            plan to V9SelectiveR2ModuleCache.status(context, plan)
                        }
                        pending = plan
                        proposed = status
                        approving = true
                        result = "İmza, paket kataloğu ve önbellek doğrulandı. Kullanıcı onayı bekleniyor."
                    } catch (e: Exception) {
                        result = "Aday reddedildi: ${e.message ?: e.javaClass.simpleName}"
                        pending = null; proposed = null
                    } finally { active = false }
                }
            }) { Text("İmzayı doğrula ve indirme planını göster") }
            if (active) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (progress.isNotBlank()) Text(progress)
            Text(result)
        }
    }
}
