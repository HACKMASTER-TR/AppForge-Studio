package com.appforge.studio

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import com.appforge.studio.build.BuildStatusResult

private val BUILD_TERMINAL_STATES =
    setOf(
        "success",
        "failed",
        "cancelled",
        "canceled"
    )


/*
 * High-frequency build runtime state is intentionally grouped here.
 *
 * Phase 10 keeps the existing MainActivity variable names through
 * delegated aliases, so build behaviour remains unchanged.
 *
 * Later phases can pass this stable holder only to Builder-related
 * composables instead of keeping individual build states at the
 * AppForgeApp root.
 */
@Stable
internal class BuildRuntimeState {

    val status =
        mutableStateOf(
            "Hazır"
        )

    val progress =
        mutableIntStateOf(
            0
        )

    val buildStartedAtMs =
        mutableStateOf<Long?>(
            null
        )

    val buildElapsedMs =
        mutableLongStateOf(
            0L
        )

    val buildTimerRunning =
        mutableStateOf(
            false
        )

    val buildBusy =
        mutableStateOf(
            false
        )

    val logs =
        mutableStateOf<
            List<String>
            >(
            emptyList()
        )

    val preflight =
        mutableStateOf<
            List<String>
            >(
            emptyList()
        )

    val buildProjectKey =
        mutableStateOf<String?>(
            null
        )

    val buildId =
        mutableStateOf<String?>(
            null
        )

    val buildNo =
        mutableStateOf<Long?>(
            null
        )

    val apkUrl =
        mutableStateOf<String?>(
            null
        )

    val aabUrl =
        mutableStateOf<String?>(
            null
        )

    val exeUrl =
        mutableStateOf<String?>(
            null
        )

    val queuePosition =
        mutableStateOf<Int?>(
            null
        )

    val queueAhead =
        mutableStateOf<Int?>(
            null
        )

    val queueWorkerSlots =
        mutableIntStateOf(
            0
        )

    val queueEtaSeconds =
        mutableStateOf<Int?>(
            null
        )

    val queueEstimate =
        mutableStateOf<String?>(
            null
        )

    fun restoreFromEngine(
        snapshot: BuildStatusResult,
        projectKey: String?,
        startedAtMs: Long?
    ) {
        val normalized =
            snapshot.status
                .trim()
                .lowercase()

        val active =
            normalized !in
                BUILD_TERMINAL_STATES

        status.value =
            snapshot.status

        progress.intValue =
            if (
                normalized ==
                "success"
            ) {
                100
            } else {
                snapshot.progress
                    .coerceIn(
                        0,
                        100
                    )
            }

        val restoredStartedAt =
            startedAtMs
                ?.takeIf {
                    it > 0L
                }

        buildStartedAtMs.value =
            restoredStartedAt

        buildElapsedMs.longValue =
            restoredStartedAt
                ?.let {
                    (
                        System.currentTimeMillis() -
                            it
                    ).coerceAtLeast(
                        0L
                    )
                }
                ?: 0L

        buildTimerRunning.value =
            active

        buildBusy.value =
            active

        logs.value =
            snapshot.logs

        preflight.value =
            snapshot.preflight

        buildProjectKey.value =
            projectKey
                ?.takeIf {
                    it.isNotBlank()
                }

        buildId.value =
            snapshot.buildId

        buildNo.value =
            snapshot.buildNo

        apkUrl.value =
            if (
                snapshot.apkAvailable
            ) {
                "available"
            } else {
                null
            }

        aabUrl.value =
            if (
                snapshot.aabAvailable
            ) {
                "available"
            } else {
                null
            }

        exeUrl.value =
            if (
                snapshot.exeAvailable
            ) {
                "available"
            } else {
                null
            }

        queuePosition.value =
            snapshot.queuePosition

        queueAhead.value =
            snapshot.queueAhead

        queueWorkerSlots.intValue =
            snapshot.queueCompatibleWorkerSlots

        queueEtaSeconds.value =
            snapshot.queueEstimatedWaitSeconds

        queueEstimate.value =
            snapshot.queueEstimate
    }

    /*
     * A completed build belongs to one project/source identity.
     *
     * When another project becomes active, never leak the previous
     * project's progress, logs or downloadable artifacts into the
     * new Builder screen.
     */
    fun resetForProjectChange() {

        status.value =
            "Hazır"

        progress.intValue =
            0

        buildStartedAtMs.value =
            null

        buildElapsedMs.longValue =
            0L

        buildTimerRunning.value =
            false

        buildBusy.value =
            false

        logs.value =
            emptyList()

        preflight.value =
            emptyList()

        buildProjectKey.value =
            null

        buildId.value =
            null

        buildNo.value =
            null

        apkUrl.value =
            null

        aabUrl.value =
            null

        exeUrl.value =
            null

        queuePosition.value =
            null

        queueAhead.value =
            null

        queueWorkerSlots.intValue =
            0

        queueEtaSeconds.value =
            null

        queueEstimate.value =
            null
    }

}
