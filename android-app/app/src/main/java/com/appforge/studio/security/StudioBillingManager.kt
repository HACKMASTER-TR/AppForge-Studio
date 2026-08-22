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

data class StudioPlanPrice(
    val lifetimePrice: String? = null,
    val monthlyPrice: String? = null,
    val lifetimeAvailable: Boolean = false,
    val monthlyAvailable: Boolean = false
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
    private val onMessage: (String) -> Unit
) : PurchasesUpdatedListener {
    private val appContext =
        context.applicationContext

    private var lifetimeDetails:
        ProductDetails? =
        null

    private var monthlyDetails:
        ProductDetails? =
        null

    private val billingClient =
        BillingClient
            .newBuilder(
                appContext
            )
            .setListener(this)
            .enableAutoServiceReconnection()
            .enablePendingPurchases(
                PendingPurchasesParams
                    .newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .build()

    fun start(
        onReady: () -> Unit
    ) {
        if (
            billingClient
                .isReady
        ) {
            onReady()
            return
        }

        billingClient
            .startConnection(
                object :
                    BillingClientStateListener {
                    override fun onBillingSetupFinished(
                        billingResult: BillingResult
                    ) {
                        if (
                            billingResult
                                .responseCode ==
                            BillingClient
                                .BillingResponseCode
                                .OK
                        ) {
                            onReady()
                        } else {
                            onMessage(
                                billingResult
                                    .debugMessage
                                    .ifBlank {
                                        "Google Play Billing başlatılamadı."
                                    }
                            )
                        }
                    }

                    override fun onBillingServiceDisconnected() {
                        onMessage(
                            "Google Play Billing bağlantısı kesildi; otomatik yeniden bağlanma açık."
                        )
                    }
                }
            )
    }

    fun queryPlans(
        onResult: (StudioPlanPrice) -> Unit
    ) {
        val products =
            buildList {
                if (
                    lifetimeProductId
                        .isNotBlank()
                ) {
                    add(
                        QueryProductDetailsParams
                            .Product
                            .newBuilder()
                            .setProductId(
                                lifetimeProductId
                            )
                            .setProductType(
                                BillingClient
                                    .ProductType
                                    .INAPP
                            )
                            .build()
                    )
                }

                if (
                    monthlyProductId
                        .isNotBlank()
                ) {
                    add(
                        QueryProductDetailsParams
                            .Product
                            .newBuilder()
                            .setProductId(
                                monthlyProductId
                            )
                            .setProductType(
                                BillingClient
                                    .ProductType
                                    .SUBS
                            )
                            .build()
                    )
                }
            }

        if (
            products.isEmpty()
        ) {
            onResult(
                StudioPlanPrice()
            )
            return
        }

        val params =
            QueryProductDetailsParams
                .newBuilder()
                .setProductList(
                    products
                )
                .build()

        billingClient
            .queryProductDetailsAsync(
                params
            ) {
                result,
                detailsResult ->

                if (
                    result.responseCode !=
                    BillingClient
                        .BillingResponseCode
                        .OK
                ) {
                    onMessage(
                        result
                            .debugMessage
                            .ifBlank {
                                "Pro plan fiyatları alınamadı."
                            }
                    )

                    onResult(
                        StudioPlanPrice()
                    )

                    return@queryProductDetailsAsync
                }

                for (
                    details in
                    detailsResult
                        .productDetailsList
                ) {
                    when (
                        details.productId
                    ) {
                        lifetimeProductId ->
                            lifetimeDetails =
                                details

                        monthlyProductId ->
                            monthlyDetails =
                                details
                    }
                }

                val lifetimePrice =
                    lifetimeDetails
                        ?.oneTimePurchaseOfferDetailsList
                        ?.firstOrNull()
                        ?.formattedPrice
                        ?: lifetimeDetails
                            ?.oneTimePurchaseOfferDetails
                            ?.formattedPrice

                val monthlyPrice =
                    monthlyDetails
                        ?.subscriptionOfferDetails
                        ?.firstOrNull()
                        ?.pricingPhases
                        ?.pricingPhaseList
                        ?.firstOrNull()
                        ?.formattedPrice

                onResult(
                    StudioPlanPrice(
                        lifetimePrice =
                            lifetimePrice,
                        monthlyPrice =
                            monthlyPrice,
                        lifetimeAvailable =
                            lifetimeDetails !=
                            null,
                        monthlyAvailable =
                            monthlyDetails !=
                            null
                    )
                )
            }
    }

    fun launchLifetime(
        activity: Activity
    ) {
        val details =
            lifetimeDetails

        if (details == null) {
            onMessage(
                "Tek seferlik Pro ürünü Google Play'den henüz yüklenmedi."
            )
            return
        }

        val builder =
            BillingFlowParams
                .ProductDetailsParams
                .newBuilder()
                .setProductDetails(
                    details
                )

        val offerToken =
            details
                .oneTimePurchaseOfferDetailsList
                ?.firstOrNull()
                ?.offerToken

        if (
            !offerToken
                .isNullOrBlank()
        ) {
            builder.setOfferToken(
                offerToken
            )
        }

        launch(
            activity,
            builder.build()
        )
    }

    fun launchMonthly(
        activity: Activity
    ) {
        val details =
            monthlyDetails

        if (details == null) {
            onMessage(
                "Pro Aylık ürünü Google Play'den henüz yüklenmedi."
            )
            return
        }

        val offerToken =
            details
                .subscriptionOfferDetails
                ?.firstOrNull()
                ?.offerToken

        if (
            offerToken
                .isNullOrBlank()
        ) {
            onMessage(
                "Pro Aylık için uygun base plan / offer bulunamadı."
            )
            return
        }

        val params =
            BillingFlowParams
                .ProductDetailsParams
                .newBuilder()
                .setProductDetails(
                    details
                )
                .setOfferToken(
                    offerToken
                )
                .build()

        launch(
            activity,
            params
        )
    }

    private fun launch(
        activity: Activity,
        product:
            BillingFlowParams
                .ProductDetailsParams
    ) {
        val result =
            billingClient
                .launchBillingFlow(
                    activity,
                    BillingFlowParams
                        .newBuilder()
                        .setProductDetailsParamsList(
                            listOf(
                                product
                            )
                        )
                        .build()
                )

        if (
            result.responseCode !=
            BillingClient
                .BillingResponseCode
                .OK
        ) {
            onMessage(
                result
                    .debugMessage
                    .ifBlank {
                        "Google Play satın alma ekranı açılamadı."
                    }
            )
        }
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        when (
            billingResult.responseCode
        ) {
            BillingClient
                .BillingResponseCode
                .OK -> {
                purchases
                    .orEmpty()
                    .filter {
                        it.purchaseState ==
                        Purchase
                            .PurchaseState
                            .PURCHASED
                    }
                    .forEach {
                        purchase ->
                        val productId =
                            purchase
                                .products
                                .firstOrNull()
                                ?: return@forEach

                        onPurchase(
                            StudioPurchaseResult(
                                productId =
                                    productId,
                                purchaseToken =
                                    purchase
                                        .purchaseToken
                            )
                        )
                    }
            }

            BillingClient
                .BillingResponseCode
                .USER_CANCELED ->
                onMessage(
                    "Satın alma iptal edildi."
                )

            else ->
                onMessage(
                    billingResult
                        .debugMessage
                        .ifBlank {
                            "Google Play satın alma işlemi tamamlanamadı."
                        }
                )
        }
    }

    fun close() {
        billingClient
            .endConnection()
    }
}
