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
    val monthlyAvailable: Boolean = false,
    val quotaAddonPrices: Map<String, String> =
        emptyMap(),
    val quotaAddonAvailability: Map<String, Boolean> =
        emptyMap()
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
    private val quotaAddonProductIds: List<String> =
        emptyList()
) : PurchasesUpdatedListener {
    private val appContext =
        context.applicationContext

    private var lifetimeDetails:
        ProductDetails? =
        null

    private var monthlyDetails:
        ProductDetails? =
        null


    private val quotaAddonDetails =
        linkedMapOf<
            String,
            ProductDetails
        >()

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
        lifetimeDetails =
            null

        monthlyDetails =
            null

        quotaAddonDetails
            .clear()

        fun finish() {
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

            val addonPrices =
                quotaAddonDetails
                    .mapValues {
                        (_, details) ->
                        details
                            .oneTimePurchaseOfferDetailsList
                            ?.firstOrNull()
                            ?.formattedPrice
                            ?: details
                                .oneTimePurchaseOfferDetails
                                ?.formattedPrice
                            ?: ""
                    }
                    .filterValues {
                        it.isNotBlank()
                    }

            val addonAvailability =
                quotaAddonProductIds
                    .associateWith {
                        productId ->
                        quotaAddonDetails
                            .containsKey(
                                productId
                            )
                    }

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
                        null,
                    quotaAddonPrices =
                        addonPrices,
                    quotaAddonAvailability =
                        addonAvailability
                )
            )
        }

        fun queryMonthly() {
            if (
                monthlyProductId
                    .isBlank()
            ) {
                finish()
                return
            }

            val product =
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

            val params =
                QueryProductDetailsParams
                    .newBuilder()
                    .setProductList(
                        listOf(
                            product
                        )
                    )
                    .build()

            billingClient
                .queryProductDetailsAsync(
                    params
                ) {
                    result,
                    detailsResult ->

                    if (
                        result.responseCode ==
                        BillingClient
                            .BillingResponseCode
                            .OK
                    ) {
                        monthlyDetails =
                            detailsResult
                                .productDetailsList
                                .firstOrNull {
                                    it.productId ==
                                        monthlyProductId
                                }
                    } else {
                        onMessage(
                            result
                                .debugMessage
                                .ifBlank {
                                    "Pro Aylık fiyatı alınamadı."
                                }
                        )
                    }

                    finish()
                }
        }

        val inAppProductIds =
            buildList {
                if (
                    lifetimeProductId
                        .isNotBlank()
                ) {
                    add(
                        lifetimeProductId
                    )
                }

                addAll(
                    quotaAddonProductIds
                        .filter {
                            it.isNotBlank()
                        }
                        .distinct()
                )
            }

        if (
            inAppProductIds
                .isEmpty()
        ) {
            queryMonthly()
            return
        }

        val inAppProducts =
            inAppProductIds
                .map {
                    productId ->
                    QueryProductDetailsParams
                        .Product
                        .newBuilder()
                        .setProductId(
                            productId
                        )
                        .setProductType(
                            BillingClient
                                .ProductType
                                .INAPP
                        )
                        .build()
                }

        val inAppParams =
            QueryProductDetailsParams
                .newBuilder()
                .setProductList(
                    inAppProducts
                )
                .build()

        billingClient
            .queryProductDetailsAsync(
                inAppParams
            ) {
                result,
                detailsResult ->

                if (
                    result.responseCode ==
                    BillingClient
                        .BillingResponseCode
                        .OK
                ) {
                    detailsResult
                        .productDetailsList
                        .forEach {
                            details ->

                            if (
                                details.productId ==
                                    lifetimeProductId
                            ) {
                                lifetimeDetails =
                                    details
                            }

                            if (
                                details.productId in
                                    quotaAddonProductIds
                            ) {
                                quotaAddonDetails[
                                    details.productId
                                ] =
                                    details
                            }
                        }
                } else {
                    onMessage(
                        result
                            .debugMessage
                            .ifBlank {
                                "Google Play tek seferlik ürünleri alınamadı."
                            }
                    )
                }

                queryMonthly()
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

    fun launchQuotaAddon(
        activity: Activity,
        productId: String
    ) {
        if (
            productId !in
                quotaAddonProductIds
        ) {
            onMessage(
                "Geçersiz AppForge ek kota ürünü."
            )
            return
        }

        val details =
            quotaAddonDetails[
                productId
            ]

        if (
            details == null
        ) {
            onMessage(
                "Ek kota ürünü Google Play'den henüz yüklenmedi."
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
