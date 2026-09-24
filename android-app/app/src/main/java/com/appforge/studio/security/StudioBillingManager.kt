package com.appforge.studio.security

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/** One non-consumable INAPP product; never query SUBS or consume tokens. */
const val APPFORGE_LIFETIME_PRODUCT_ID = "appforge_pro_lifetime"

data class StudioPlanPrice(
    val lifetimePrice: String? = null,
    val lifetimeAvailable: Boolean = false
)

data class StudioPurchaseResult(
    val productId: String,
    val purchaseToken: String
)

class StudioBillingManager(
    context: Context,
    private val lifetimeProductId: String,
    private val onPurchase: (StudioPurchaseResult) -> Unit,
    private val onMessage: (String) -> Unit
) : PurchasesUpdatedListener {
    private var lifetimeDetails: ProductDetails? = null
    private val billingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enableAutoServiceReconnection()
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    fun start(onReady: () -> Unit) {
        if (billingClient.isReady) {
            onReady()
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) onReady()
                else onMessage("Google Play Billing başlatılamadı.")
            }

            override fun onBillingServiceDisconnected() {
                onMessage("Google Play bağlantısı kesildi. Yeniden deneyebilirsin.")
            }
        })
    }

    fun queryPlans(onResult: (StudioPlanPrice) -> Unit) {
        lifetimeDetails = null
        if (lifetimeProductId != APPFORGE_LIFETIME_PRODUCT_ID) {
            onMessage("Pro ürün kimliği sunucuda doğrulanmadı.")
            onResult(StudioPlanPrice())
            return
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(QueryProductDetailsParams.Product.newBuilder()
                .setProductId(lifetimeProductId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build())
        ).build()
        billingClient.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                lifetimeDetails = details.productDetailsList.firstOrNull {
                    it.productId == lifetimeProductId
                }
            } else {
                onMessage("Google Play ürün bilgileri alınamadı.")
            }
            val offer = lifetimeDetails
            onResult(StudioPlanPrice(
                lifetimePrice = offer?.oneTimePurchaseOfferDetailsList
                    ?.firstOrNull()?.formattedPrice
                    ?: offer?.oneTimePurchaseOfferDetails?.formattedPrice,
                lifetimeAvailable = offer != null
            ))
        }
    }

    fun launchLifetime(activity: Activity) {
        val details = lifetimeDetails
        if (lifetimeProductId != APPFORGE_LIFETIME_PRODUCT_ID || details == null) {
            onMessage("Pro Ömür Boyu şu anda satın alınamıyor.")
            return
        }
        val builder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
        details.oneTimePurchaseOfferDetailsList?.firstOrNull()?.offerToken
            ?.takeIf { it.isNotBlank() }?.let(builder::setOfferToken)
        val result = billingClient.launchBillingFlow(
            activity, BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(builder.build())).build()
        )
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            onMessage("Google Play satın alma ekranı açılamadı: ${result.responseCode}")
        }
    }

    /** Restore is NOT a grant; receipts are sent to server for verification. */
    fun restorePurchases(onComplete: (Int) -> Unit = {}) {
        if (!billingClient.isReady) {
            start { restorePurchases(onComplete) }
            return
        }
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP).build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                onMessage("Google Play satın almaları sorgulanamadı.")
                onComplete(0)
                return@queryPurchasesAsync
            }
            val matching = purchases.filter { purchase ->
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                    lifetimeProductId == APPFORGE_LIFETIME_PRODUCT_ID &&
                    lifetimeProductId in purchase.products &&
                    purchase.purchaseToken.isNotBlank()
            }.distinctBy { it.purchaseToken }
            matching.forEach { purchase ->
                onPurchase(StudioPurchaseResult(lifetimeProductId, purchase.purchaseToken))
            }
            if (matching.isEmpty()) onMessage("Google Play üzerinde Pro Ömür Boyu satın alımı bulunamadı.")
            onComplete(matching.size)
        }
    }

    override fun onPurchasesUpdated(
        result: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val pending = purchases.orEmpty().any {
                    it.purchaseState == Purchase.PurchaseState.PENDING &&
                        lifetimeProductId in it.products
                }
                if (pending) onMessage("Ödeme beklemede; Pro henüz açılmadı.")
                purchases.orEmpty().filter {
                    it.purchaseState == Purchase.PurchaseState.PURCHASED &&
                        lifetimeProductId == APPFORGE_LIFETIME_PRODUCT_ID &&
                        lifetimeProductId in it.products && it.purchaseToken.isNotBlank()
                }.distinctBy { it.purchaseToken }.forEach {
                    onPurchase(StudioPurchaseResult(lifetimeProductId, it.purchaseToken))
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED ->
                onMessage("Satın alma iptal edildi.")
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
                onMessage("Ürüne zaten sahipsin. Satın alımı geri yükle.")
            else -> onMessage("Google Play satın alma işlemi tamamlanamadı: ${result.responseCode}")
        }
    }

    fun close() = billingClient.endConnection()
}
