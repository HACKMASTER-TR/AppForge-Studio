package com.appforge.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.appforge.studio.model.DEFAULT_CONTROL_PLANE_URL
import com.appforge.studio.security.APPFORGE_LIFETIME_PRODUCT_ID
import com.appforge.studio.security.ProStatus
import com.appforge.studio.security.StudioBillingManager
import com.appforge.studio.security.StudioPlanPrice
import com.appforge.studio.security.StudioPurchaseResult
import com.appforge.studio.security.StudioSecurityClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.UnknownHostException
import java.net.ConnectException
import java.net.SocketTimeoutException

/** A failed verification must never unlock Pro. Only network failures get an internet warning. */
private fun purchaseErrorMessage(error: Throwable): String {
    val connectionFailure = generateSequence(error) { it.cause }.any { cause ->
        cause is UnknownHostException ||
            cause is ConnectException ||
            cause is SocketTimeoutException
    }
    return if (connectionFailure) {
        "İnternet bağlantısı kurulamadı. Lütfen tekrar dene."
    } else {
        "İşlem tamamlanamadı. Lütfen tekrar dene."
    }
}

private data class PurchaseCenterState(
    val loading: Boolean = true,
    val message: String = "",
    val pro: ProStatus? = null,
    val prices: StudioPlanPrice = StudioPlanPrice(),
    val serverReady: Boolean = false
)

class ProPurchasesActivity : ComponentActivity() {
    private var state by mutableStateOf(PurchaseCenterState())
    private var billing: StudioBillingManager? = null
    private val security by lazy {
        StudioSecurityClient(this, DEFAULT_CONTROL_PLANE_URL, "")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadCenter()
        setContent {
            AppForgeTheme {
                Surface(Modifier.fillMaxSize()) {
                    LifetimeContent(
                        state = state,
                        onBack = ::finish,
                        onRefresh = ::loadCenter,
                        onBuy = { billing?.launchLifetime(this) },
                        onRestore = { billing?.restorePurchases() }
                    )
                }
            }
        }
    }

    private fun loadCenter() {
        state = state.copy(loading = true, message = "", serverReady = false)
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { security.config() }
            }.onSuccess { config ->
                if (config.proProductId != APPFORGE_LIFETIME_PRODUCT_ID ||
                    config.proMonthlyProductId.isNotBlank() ||
                    listOf(config.quota10ProductId, config.quota25ProductId,
                        config.quota50ProductId).any { it.isNotBlank() }
                ) {
                    state = state.copy(loading = false,
                        message = "Sunucu tek Pro Ömür Boyu ürünü için hazır değil.")
                    return@onSuccess
                }
                billing?.close()
                val manager = StudioBillingManager(
                    context = this@ProPurchasesActivity,
                    lifetimeProductId = config.proProductId,
                    onPurchase = ::verifyReceipt,
                    onMessage = { state = state.copy(message = it) }
                )
                billing = manager
                state = state.copy(loading = false, serverReady = true)
                manager.start {
                    manager.queryPlans { state = state.copy(prices = it) }
                    manager.restorePurchases()
                }
            }.onFailure { error ->
                state = state.copy(loading = false,
                    message = purchaseErrorMessage(error))
            }
        }
    }

    private fun verifyReceipt(receipt: StudioPurchaseResult) {
        if (receipt.productId != APPFORGE_LIFETIME_PRODUCT_ID) return
        state = state.copy(loading = true,
            message = "Satın alma Google Play üzerinden sunucuda doğrulanıyor...")
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    security.verifyLifetimePurchase(receipt.purchaseToken)
                }
            }.onSuccess { pro ->
                state = state.copy(loading = false, pro = pro,
                    message = "Pro Ömür Boyu sunucuda doğrulandı.")
            }.onFailure { error ->
                state = state.copy(loading = false,
                    message = purchaseErrorMessage(error) +
                        " Satın alımı geri yüklemeyi deneyebilirsin.")
            }
        }
    }

    override fun onDestroy() {
        billing?.close()
        billing = null
        super.onDestroy()
    }
}

@Composable
private fun LifetimeContent(
    state: PurchaseCenterState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onBuy: () -> Unit,
    onRestore: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding()
            .verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onBack) { Text("Geri") }
                Text("AppForge Pro", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = onRefresh) { Text("Yenile") }
            }
            Card {
                Column(Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Pro Ömür Boyu", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold)
                    Text("Tek seferlik Google Play ödemesi. Aylık abonelik veya ek paket yok.")
                    Text(if (state.pro?.active == true) "Pro doğrulandı"
                        else "Satın alma doğrulanmayı bekliyor",
                        fontWeight = FontWeight.SemiBold)
                    if (state.loading) CircularProgressIndicator()
                    Text(state.prices.lifetimePrice ?: "Fiyat Google Play'den yüklenir")
                    Button(
                        onClick = onBuy,
                        enabled = state.serverReady && state.prices.lifetimeAvailable &&
                            state.pro?.active != true && !state.loading,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("PRO'YU ÖMÜR BOYU AÇ") }
                    OutlinedButton(onClick = onRestore,
                        enabled = state.serverReady && !state.loading,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Satın alımı geri yükle") }
                    if (!state.serverReady) Text(
                        "Satın alma, gerçek sunucu doğrulaması tamamlandığında kullanılabilir.")
                }
            }
            if (state.message.isNotBlank()) Card {
                Text(state.message, Modifier.padding(14.dp))
            }
        }
    }
}
