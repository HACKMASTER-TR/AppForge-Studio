package com.appforge.studio.play

import android.app.Activity
import android.content.Context
import android.os.Build
import com.appforge.studio.BuildConfig

internal const val PLAY_REVIEW_MIN_SUCCESSFUL_BUILDS =
    3

internal const val PLAY_REVIEW_COOLDOWN_MS =
    120L * 24L * 60L * 60L * 1000L

internal fun shouldRequestPlayReview(
    successfulBuilds: Int,
    lastAttemptAtMs: Long,
    nowMs: Long
): Boolean {

    if (
        successfulBuilds <
            PLAY_REVIEW_MIN_SUCCESSFUL_BUILDS
    ) {
        return false
    }

    if (
        lastAttemptAtMs <= 0L
    ) {
        return true
    }

    return (
        nowMs -
            lastAttemptAtMs
        ) >=
        PLAY_REVIEW_COOLDOWN_MS
}

/**
 * PLAY_REVIEW_SUCCESS_GATE_V1
 *
 * Review flow is requested only after a meaningful successful action:
 * successful local AppForge builds.
 *
 * Google Play remains authoritative over whether the review UI
 * is actually displayed.
 */
object PlayReviewGate {

    private const val PREFS =
        "appforge_play_review_v1"

    private const val KEY_SUCCESS_COUNT =
        "successful_build_count"

    private const val KEY_LAST_ATTEMPT =
        "last_attempt_at_ms"

    private const val KEY_LAST_BUILD_ID =
        "last_successful_build_id"

    fun recordSuccessfulBuildAndMaybeRequest(
        activity: Activity,
        buildId: String?
    ) {
        if (
            BuildConfig.DEBUG ||
            !installedFromGooglePlay(
                activity
            )
        ) {
            return
        }

        val prefs =
            activity.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )

        val normalizedBuildId =
            buildId
                ?.trim()
                .orEmpty()

        if (
            normalizedBuildId.isNotBlank() &&
            prefs.getString(
                KEY_LAST_BUILD_ID,
                null
            ) ==
            normalizedBuildId
        ) {
            return
        }

        val successes =
            prefs.getInt(
                KEY_SUCCESS_COUNT,
                0
            ) + 1

        val now =
            System.currentTimeMillis()

        prefs.edit()
            .putInt(
                KEY_SUCCESS_COUNT,
                successes
            )
            .apply {
                if (
                    normalizedBuildId
                        .isNotBlank()
                ) {
                    putString(
                        KEY_LAST_BUILD_ID,
                        normalizedBuildId
                    )
                }
            }
            .apply()

        val lastAttempt =
            prefs.getLong(
                KEY_LAST_ATTEMPT,
                0L
            )

        if (
            !shouldRequestPlayReview(
                successfulBuilds =
                    successes,
                lastAttemptAtMs =
                    lastAttempt,
                nowMs =
                    now
            )
        ) {
            return
        }

        /*
         * Record the attempt before handing control to Play.
         * The API intentionally does not reveal whether the dialog
         * was displayed or whether a rating was submitted.
         */
        prefs.edit()
            .putLong(
                KEY_LAST_ATTEMPT,
                now
            )
            .apply()

        PlayPlatformServices
            .requestInAppReview(
                activity
            )
    }

    private fun installedFromGooglePlay(
        context: Context
    ): Boolean =
        runCatching {

            val installer =
                if (
                    Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.R
                ) {
                    context.packageManager
                        .getInstallSourceInfo(
                            context.packageName
                        )
                        .installingPackageName
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager
                        .getInstallerPackageName(
                            context.packageName
                        )
                }

            installer ==
                "com.android.vending"

        }.getOrDefault(
            false
        )
}
