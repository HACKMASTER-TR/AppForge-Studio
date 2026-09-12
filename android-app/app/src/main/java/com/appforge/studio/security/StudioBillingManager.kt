package com.appforge.studio.security

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
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

data class StudioPlanPrice(
    val lifetimePrice: String? = null,
    val monthlyPrice: String? = null,
    val lifetimeAvailable: Boolean = false,
    val monthlyAvailable: Boolean = false,
    val quotaAddonPrices: Map<String, String> = emptyMap(),
    val quotaAddonAvailability: Map<String, Boolean> = emptyMap()
)

data class StudioPurchaseResult(
    val productId: String,
    val purchaseToken: String
)

class StudioBillingManager(
    context: Context,
    private val lifetimeProductId: String,
    private val monthlyProductId: String,
    private val onPurchase: (StudioPurchaseResult) -> Unit,
    private val onMessage: (String) -> Unit,
    private val quotaAddonProductIds: List<String> = emptyList()
) : PurchasesUpdatedListener {
    private val appContext = context.applicationContext

    private var lifetimeDetails: ProductDetails? = null
    private var monthlyDetails: ProductDetails? = null
    private val quotaAddonDetails = linkedMapOf<String, ProductDetails>()

    private val billingClient = BillingClient
        .newBuilder(appContext)
        .setListener(this)
        .enableAutoServiceReconnection()
        .enablePendingPurchases(
            PendingPurchasesParams
                .newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    fun start(onReady: () -> Unit) {
        if (billingClient.isReady) {
            onReady()
            return
        }

        billingClient.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        onReady()
                    } else {
                        onMessage(userMessage(billingResult, "Google Play Billing başlatılamadı."))
                    }
                }

                override fun onBillingServiceDisconnected() {
                    onMessage("Google Play bağlantısı kesildi. Bağlantı otomatik olarak yeniden denenecek.")
                }
            }
        )
    }

    fun queryPlans(onResult: (StudioPlanPrice) -> Unit) {
        lifetimeDetails = null
        monthlyDetails = null
        quotaAddonDetails.clear()

        fun finish() {
            val lifetimePrice = lifetimeDetails
                ?.oneTimePurchaseOfferDetailsList
                ?.firstOrNull()
                ?.formattedPrice
                ?: lifetimeDetails
                    ?.oneTimePurchaseOfferDetails
                    ?.formattedPrice

            val monthlyPrice = monthlyDetails
                ?.subscriptionOfferDetails
                ?.firstOrNull()
                ?.pricingPhases
                ?.pricingPhaseList
                ?.firstOrNull()
                ?.formattedPrice

            val addonPrices = quotaAddonDetails
                .mapValues { (_, details) ->
                    details.oneTimePurchaseOfferDetailsList
                        ?.firstOrNull()
                        ?.formattedPrice
                        ?: details.oneTimePurchaseOfferDetails?.formattedPrice
                        ?: ""
                }
                .filterValues { it.isNotBlank() }

            onResult(
                StudioPlanPrice(
                    lifetimePrice = lifetimePrice,
                    monthlyPrice = monthlyPrice,
                    lifetimeAvailable = lifetimeDetails != null,
                    monthlyAvailable = monthlyDetails != null,
                    quotaAddonPrices = addonPrices,
                    quotaAddonAvailability = quotaAddonProductIds
                        .associateWith { quotaAddonDetails.containsKey(it) }
                )
            )
        }

        fun queryMonthly() {
            if (monthlyProductId.isBlank()) {
                finish()
                return
            }

            val params = QueryProductDetailsParams
                .newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product
                            .newBuilder()
                            .setProductId(monthlyProductId)
                            .setProductType(BillingClient.ProductType.SUBS)
                            .build()
                    )
                )
                .build()

            billingClient.queryProductDetailsAsync(params) { result, detailsResult ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    monthlyDetails = detailsResult.productDetailsList
                        .firstOrNull { it.productId == monthlyProductId }
                } else {
                    onMessage(userMessage(result, "Pro Aylık fiyatı alınamadı."))
                }
                finish()
            }
        }

        val inAppIds = buildList {
            if (lifetimeProductId.isNotBlank()) add(lifetimeProductId)
            addAll(quotaAddonProductIds.filter { it.isNotBlank() }.distinct())
        }

        if (inAppIds.isEmpty()) {
            queryMonthly()
            return
        }

        val params = QueryProductDetailsParams
            .newBuilder()
            .setProductList(
                inAppIds.map { productId ->
                    QueryProductDetailsParams.Product
                        .newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                }
            )
            .build()

        billingClient.queryProductDetailsAsync(params) { result, detailsResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                detailsResult.productDetailsList.forEach { details ->
                    if (details.productId == lifetimeProductId) {
                        lifetimeDetails = details
                    }
                    if (details.productId in quotaAddonProductIds) {
                        quotaAddonDetails[details.productId] = details
                    }
                }
            } else {
                onMessage(userMessage(result, "Google Play ürünleri alınamadı."))
            }
            queryMonthly()
        }
    }

    fun launchLifetime(activity: Activity) {
        val details = lifetimeDetails
        if (details == null) {
            onMessage("Tek seferlik Pro ürünü Google Play'de kullanılamıyor.")
            return
        }

        val builder = BillingFlowParams.ProductDetailsParams
            .newBuilder()
            .setProductDetails(details)

        details.oneTimePurchaseOfferDetailsList
            ?.firstOrNull()
            ?.offerToken
            ?.takeIf { it.isNotBlank() }
            ?.let(builder::setOfferToken)

        launch(activity, builder.build())
    }

    fun launchQuotaAddon(
        activity: Activity,
        productId: String
    ) {
        if (productId !in quotaAddonProductIds) {
            onMessage("Geçersiz AppForge ek kota ürünü.")
            return
        }

        val details = quotaAddonDetails[productId]
        if (details == null) {
            onMessage("Ek kota ürünü Google Play'de şu anda kullanılamıyor.")
            return
        }

        val builder = BillingFlowParams.ProductDetailsParams
            .newBuilder()
            .setProductDetails(details)

        details.oneTimePurchaseOfferDetailsList
            ?.firstOrNull()
            ?.offerToken
            ?.takeIf { it.isNotBlank() }
            ?.let(builder::setOfferToken)

        launch(activity, builder.build())
    }

    fun launchMonthly(activity: Activity) {
        val details = monthlyDetails
        if (details == null) {
            onMessage("Pro Aylık ürünü Google Play'de şu anda kullanılamıyor.")
            return
        }

        val offerToken = details.subscriptionOfferDetails
            ?.firstOrNull()
            ?.offerToken

        if (offerToken.isNullOrBlank()) {
            onMessage("Pro Aylık için kullanılabilir bir Google Play planı bulunamadı.")
            return
        }

        launch(
            activity,
            BillingFlowParams.ProductDetailsParams
                .newBuilder()
                .setProductDetails(details)
                .setOfferToken(offerToken)
                .build()
        )
    }

    fun restorePurchases(
        onComplete: (Int) -> Unit = {}
    ) {
        if (!billingClient.isReady) {
            start { restorePurchases(onComplete) }
            return
        }

        val restoredTokens = linkedSetOf<String>()
        var remainingQueries = 2
        var hadError = false

        fun finishOne() {
            remainingQueries -= 1
            if (remainingQueries != 0) return

            if (hadError && restoredTokens.isEmpty()) {
                onMessage("Satın almalar şu anda geri yüklenemedi. Daha sonra tekrar deneyebilirsin.")
            } else if (restoredTokens.isEmpty()) {
                onMessage("Geri yüklenecek aktif satın alma bulunamadı.")
            } else {
                onMessage("${restoredTokens.size} satın alma bulundu ve doğrulama için gönderildi.")
            }

            onComplete(restoredTokens.size)
        }

        fun query(type: String) {
            val params = QueryPurchasesParams
                .newBuilder()
                .setProductType(type)
                .build()

            billingClient.queryPurchasesAsync(params) { result, purchases ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    hadError = true
                    finishOne()
                    return@queryPurchasesAsync
                }

                purchases
                    .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    .forEach { purchase ->
                        if (!restoredTokens.add(purchase.purchaseToken)) {
                            return@forEach
                        }

                        purchase.products
                            .firstOrNull()
                            ?.let { productId ->
                                onPurchase(
                                    StudioPurchaseResult(
                                        productId = productId,
                                        purchaseToken = purchase.purchaseToken
                                    )
                                )
                            }
                    }

                finishOne()
            }
        }

        query(BillingClient.ProductType.SUBS)
        query(BillingClient.ProductType.INAPP)
    }

    fun openManageSubscription(activity: Activity) {
        if (monthlyProductId.isBlank()) {
            onMessage("Yönetilecek Pro Aylık ürünü bulunamadı.")
            return
        }

        val uri = Uri.parse(
            "https://play.google.com/store/account/subscriptions" +
                "?sku=${Uri.encode(monthlyProductId)}" +
                "&package=${Uri.encode(appContext.packageName)}"
        )

        runCatching {
            activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }.onFailure {
            onMessage("Google Play abonelik yönetimi açılamadı.")
        }
    }

    private fun launch(
        activity: Activity,
        product: BillingFlowParams.ProductDetailsParams
    ) {
        val result = billingClient.launchBillingFlow(
            activity,
            BillingFlowParams
                .newBuilder()
                .setProductDetailsParamsList(listOf(product))
                .build()
        )

        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            onMessage(userMessage(result, "Google Play satın alma ekranı açılamadı."))
        }
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val completed = purchases
                    .orEmpty()
                    .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }

                if (completed.isEmpty() && purchases.orEmpty().any {
                        it.purchaseState == Purchase.PurchaseState.PENDING
                    }
                ) {
                    onMessage("Satın alma beklemede. Google Play işlemi tamamlandığında haklar doğrulanacak.")
                    return
                }

                completed.forEach { purchase ->
                    purchase.products.firstOrNull()?.let { productId ->
                        onPurchase(
                            StudioPurchaseResult(
                                productId = productId,
                                purchaseToken = purchase.purchaseToken
                            )
                        )
                    }
                }
            }

            BillingClient.BillingResponseCode.USER_CANCELED ->
                onMessage("Satın alma iptal edildi.")

            else ->
                onMessage(userMessage(billingResult, "Google Play satın alma işlemi tamamlanamadı."))
        }
    }

    fun close() {
        billingClient.endConnection()
    }

    private fun userMessage(
        result: BillingResult,
        fallback: String
    ): String = when (result.responseCode) {
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED ->
            "Google Play bağlantısı kesildi. Tekrar deneyebilirsin."

        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingClient.BillingResponseCode.NETWORK_ERROR ->
            "Google Play'e ulaşılamıyor. İnternet bağlantını kontrol edip tekrar dene."

        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ->
            "Google Play satın alma bu cihaz veya hesapta kullanılamıyor."

        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE ->
            "Bu ürün Google Play'de şu anda kullanılamıyor."

        BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
            "Bu ürün zaten hesabında. Satın almaları geri yükleyebilirsin."

        BillingClient.BillingResponseCode.DEVELOPER_ERROR ->
            "Google Play ürün yapılandırması tamamlanamadı."

        BillingClient.BillingResponseCode.ERROR ->
            fallback

        else -> fallback
    }
}
