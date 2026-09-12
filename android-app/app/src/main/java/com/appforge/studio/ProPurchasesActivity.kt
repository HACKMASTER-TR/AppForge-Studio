package com.appforge.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.appforge.studio.model.DEFAULT_BUILD_SERVICE_URL
import com.appforge.studio.security.QuotaStatus
import com.appforge.studio.security.SecureAccountStore
import com.appforge.studio.security.SecurityConfig
import com.appforge.studio.security.StudioBillingManager
import com.appforge.studio.security.StudioPlanPrice
import com.appforge.studio.security.ProStatus
import com.appforge.studio.security.StudioPurchaseResult
import com.appforge.studio.security.StudioSecurityClient
import kotlinx.coroutines.launch

private data class PurchaseCenterState(
    val loading: Boolean = true,
    val message: String? = null,
    val config: SecurityConfig? = null,
    val pro: ProStatus? = null,
    val quota: QuotaStatus? = null,
    val prices: StudioPlanPrice = StudioPlanPrice()
)

class ProPurchasesActivity : ComponentActivity() {
    private var state by mutableStateOf(PurchaseCenterState())
    private var billing: StudioBillingManager? = null
    private var security: StudioSecurityClient? = null
    private var userId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val session = SecureAccountStore.loadSession(this)
        if (session == null) {
            state = PurchaseCenterState(
                loading = false,
                message = "Pro ve satın alma bilgilerini görmek için önce AppForge hesabına giriş yapmalısın."
            )
        } else {
            userId = session.userId
            security = StudioSecurityClient(
                context = this,
                baseUrl = DEFAULT_BUILD_SERVICE_URL,
                accessToken = session.token
            )
            loadCenter()
        }

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    ProPurchasesContent(
                        state = state,
                        onBack = ::finish,
                        onRefresh = ::loadCenter,
                        onBuyMonthly = {
                            billing?.launchMonthly(this)
                        },
                        onBuyAddon = { productId ->
                            billing?.launchQuotaAddon(this, productId)
                        },
                        onRestore = {
                            billing?.restorePurchases {
                                refreshServerState()
                            }
                        },
                        onManage = {
                            billing?.openManageSubscription(this)
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        billing?.close()
        billing = null
        super.onDestroy()
    }

    private fun loadCenter() {
        val client = security ?: return
        state = state.copy(loading = true, message = null)

        lifecycleScope.launch {
            runCatching {
                val cfg = client.config()
                val pro = client.proStatus(userId)
                val quota = client.quotaStatus()
                Triple(cfg, pro, quota)
            }.onSuccess { (cfg, pro, quota) ->
                state = state.copy(
                    loading = false,
                    config = cfg,
                    pro = pro,
                    quota = quota,
                    message = null
                )
                setupBilling(cfg)
            }.onFailure { error ->
                state = state.copy(
                    loading = false,
                    message = cleanMessage(error)
                )
            }
        }
    }

    private fun setupBilling(cfg: SecurityConfig) {
        billing?.close()

        val manager = StudioBillingManager(
            context = this,
            lifetimeProductId = cfg.proProductId,
            monthlyProductId = cfg.proMonthlyProductId,
            quotaAddonProductIds = listOf(
                cfg.quota10ProductId,
                cfg.quota25ProductId,
                cfg.quota50ProductId
            ).filter { it.isNotBlank() },
            onPurchase = ::handlePurchase,
            onMessage = { message ->
                state = state.copy(message = message)
            }
        )

        billing = manager
        manager.start {
            manager.queryPlans { prices ->
                state = state.copy(prices = prices)
            }
        }
    }

    private fun handlePurchase(purchase: StudioPurchaseResult) {
        val client = security ?: return
        val cfg = state.config ?: return

        state = state.copy(
            loading = true,
            message = "Google Play satın alması AppForge sunucusunda doğrulanıyor…"
        )

        lifecycleScope.launch {
            runCatching {
                when (purchase.productId) {
                    cfg.proMonthlyProductId -> {
                        client.activatePro(
                            userId = userId,
                            purchaseToken = purchase.purchaseToken,
                            plan = "monthly"
                        )
                    }

                    cfg.quota10ProductId,
                    cfg.quota25ProductId,
                    cfg.quota50ProductId -> {
                        client.redeemQuotaAddon(
                            userId = userId,
                            productId = purchase.productId,
                            purchaseToken = purchase.purchaseToken
                        )
                    }

                    else -> error("Bu Google Play ürünü AppForge Pro merkezinde tanınmıyor.")
                }
            }.onSuccess {
                state = state.copy(
                    loading = false,
                    message = "Satın alma doğrulandı. Hakların güncellendi."
                )
                refreshServerState()
            }.onFailure { error ->
                state = state.copy(
                    loading = false,
                    message = cleanMessage(error)
                )
            }
        }
    }

    private fun refreshServerState() {
        val client = security ?: return
        if (userId.isBlank()) return

        lifecycleScope.launch {
            runCatching {
                client.proStatus(userId) to client.quotaStatus()
            }.onSuccess { (pro, quota) ->
                state = state.copy(
                    loading = false,
                    pro = pro,
                    quota = quota
                )
            }.onFailure { error ->
                state = state.copy(
                    loading = false,
                    message = cleanMessage(error)
                )
            }
        }
    }

    private fun cleanMessage(error: Throwable): String {
        val text = error.message.orEmpty().trim()
        return if (text.isBlank()) {
            "İşlem tamamlanamadı. İnternet bağlantını kontrol edip tekrar deneyebilirsin."
        } else {
            text.take(300)
        }
    }
}

@Composable
private fun ProPurchasesContent(
    state: PurchaseCenterState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onBuyMonthly: () -> Unit,
    onBuyAddon: (String) -> Unit,
    onRestore: () -> Unit,
    onManage: () -> Unit
) {
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(onClick = onBack) {
                    Text("Geri")
                }
                Text(
                    "Pro ve Satın Almalar",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(onClick = onRefresh) {
                    Text("Yenile")
                }
            }

            state.message?.takeIf { it.isNotBlank() }?.let { message ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        message,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            if (state.loading) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            val pro = state.pro
            val quota = state.quota
            val cfg = state.config

            Card {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        if (pro?.active == true) "Pro aktif" else "Standart plan",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (pro?.active == true) {
                            "Yetki AppForge sunucusu tarafından doğrulandı."
                        } else {
                            "Pro Aylık ile gelişmiş özellikleri ve daha yüksek kotaları kullanabilirsin."
                        }
                    )
                    pro?.expiresAt?.let { Text("Dönem sonu: $it") }

                    val price = state.prices.monthlyPrice
                    Button(
                        onClick = onBuyMonthly,
                        enabled = state.prices.monthlyAvailable && !state.loading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (price.isNullOrBlank()) "Pro Aylık" else "Pro Aylık • $price"
                        )
                    }

                    OutlinedButton(
                        onClick = onManage,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Aboneliği Google Play'de yönet")
                    }

                    OutlinedButton(
                        onClick = onRestore,
                        enabled = !state.loading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Satın almaları geri yükle")
                    }
                }
            }

            if (quota != null) {
                Card {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "Kota durumu",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            quotaLine(
                                label = "Projeler",
                                used = quota.projectUsed,
                                limit = quota.projectLimit,
                                addon = quota.projectAddonBonus
                            )
                        )
                        Text(
                            quotaLine(
                                label = "Başarılı build",
                                used = quota.buildUsed,
                                limit = quota.buildLimit,
                                addon = quota.buildAddonBonus
                            )
                        )
                        quota.periodEndsAt?.let {
                            Text("Kota dönemi sonu: $it")
                        }
                        Text(
                            "Başarısız veya iptal edilen build'ler aylık başarılı-build hakkı tüketmez.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            val monthlyPro =
                pro?.active == true &&
                    (
                        pro.source == "google_play_subscription" ||
                            pro.productId == cfg?.proMonthlyProductId
                    )

            if (cfg != null && monthlyPro) {
                Card {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Ek kota paketleri",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Ek paketler yalnız aktif Pro Aylık döneminde geçerlidir ve sonraki döneme devretmez."
                        )

                        AddonButton(
                            label = "+10 proje / +20 build",
                            productId = cfg.quota10ProductId,
                            price = state.prices.quotaAddonPrices[cfg.quota10ProductId],
                            available = state.prices.quotaAddonAvailability[cfg.quota10ProductId] == true,
                            onBuy = onBuyAddon
                        )
                        AddonButton(
                            label = "+25 proje / +50 build",
                            productId = cfg.quota25ProductId,
                            price = state.prices.quotaAddonPrices[cfg.quota25ProductId],
                            available = state.prices.quotaAddonAvailability[cfg.quota25ProductId] == true,
                            onBuy = onBuyAddon
                        )
                        AddonButton(
                            label = "+50 proje / +100 build",
                            productId = cfg.quota50ProductId,
                            price = state.prices.quotaAddonPrices[cfg.quota50ProductId],
                            available = state.prices.quotaAddonAvailability[cfg.quota50ProductId] == true,
                            onBuy = onBuyAddon
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun AddonButton(
    label: String,
    productId: String,
    price: String?,
    available: Boolean,
    onBuy: (String) -> Unit
) {
    Button(
        onClick = { onBuy(productId) },
        enabled = productId.isNotBlank() && available,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            if (price.isNullOrBlank()) label else "$label • $price"
        )
    }
}

private fun quotaLine(
    label: String,
    used: Int?,
    limit: Int?,
    addon: Int
): String {
    val usedText = used?.toString() ?: "-"
    val limitText = limit?.toString() ?: "Sınırsız"
    val addonText = if (addon > 0) " • ek +$addon" else ""
    return "$label: $usedText / $limitText$addonText"
}
