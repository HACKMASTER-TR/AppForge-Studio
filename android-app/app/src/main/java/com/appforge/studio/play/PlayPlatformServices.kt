package com.appforge.studio.play

import android.app.Activity
import android.content.Context
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.google.android.play.core.assetpacks.AssetPackManager
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory

data class PlayInstallReferrerSnapshot(
    val referrer: String,
    val clickTimestampSeconds: Long,
    val installTimestampSeconds: Long,
    val instantExperience: Boolean
)

/**
 * GOOGLE_PLAY_PLATFORM_RUNTIME_V1
 *
 * Runtime entry points for Play services that are safe to have available
 * before their product/UI triggers are enabled.
 *
 * No raw referrer is persisted here.
 * No review prompt is fired automatically.
 * No Play release/track mutation happens from the Android client.
 */
object PlayPlatformServices {

    fun requestInAppReview(
        activity: Activity,
        onFinished: (Boolean) -> Unit = {}
    ) {
        val manager =
            ReviewManagerFactory.create(
                activity
            )

        manager.requestReviewFlow()
            .addOnSuccessListener { info ->

                manager.launchReviewFlow(
                    activity,
                    info
                )
                    .addOnCompleteListener {
                        /*
                         * Play intentionally does not reveal whether
                         * the review dialog was actually shown or whether
                         * the user submitted a rating.
                         */
                        onFinished(true)
                    }
            }
            .addOnFailureListener {
                onFinished(false)
            }
    }

    fun readInstallReferrerOnce(
        context: Context,
        onResult: (
            PlayInstallReferrerSnapshot?
        ) -> Unit
    ) {
        val client =
            InstallReferrerClient
                .newBuilder(
                    context.applicationContext
                )
                .build()

        client.startConnection(
            object :
                InstallReferrerStateListener {

                override fun onInstallReferrerSetupFinished(
                    responseCode: Int
                ) {
                    try {
                        if (
                            responseCode !=
                                InstallReferrerClient
                                    .InstallReferrerResponse
                                    .OK
                        ) {
                            onResult(null)
                            return
                        }

                        val response =
                            client.installReferrer

                        onResult(
                            PlayInstallReferrerSnapshot(
                                referrer =
                                    response.installReferrer,
                                clickTimestampSeconds =
                                    response.referrerClickTimestampSeconds,
                                installTimestampSeconds =
                                    response.installBeginTimestampSeconds,
                                instantExperience =
                                    response.googlePlayInstantParam
                            )
                        )
                    } catch (
                        _: Throwable
                    ) {
                        onResult(null)

                    } finally {
                        runCatching {
                            client.endConnection()
                        }
                    }
                }

                override fun onInstallReferrerServiceDisconnected() {
                    /*
                     * Callers may choose to retry later.
                     * Never loop automatically.
                     */
                }
            }
        )
    }

    fun featureDeliveryManager(
        context: Context
    ): SplitInstallManager =
        SplitInstallManagerFactory
            .create(
                context.applicationContext
            )

    fun assetDeliveryManager(
        context: Context
    ): AssetPackManager =
        AssetPackManagerFactory
            .getInstance(
                context.applicationContext
            )
}
