package com.appforge.studio

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf


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
}
