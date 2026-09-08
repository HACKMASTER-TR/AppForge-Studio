package com.appforge.studio.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test


class BuildDiagnosisPolicyTest {

    @Test
    fun successfulBuildsNeverRequireFailureDiagnosis() {
        val statuses =
            listOf(
                "success",
                "succeeded",
                "completed",
                "done",
                "Başarılı",
                "Başarılı • %100",
                "Tamamlandı",
                "Build succeeded"
            )

        statuses.forEach {
            status ->

            assertTrue(
                status,
                BuildDiagnosisPolicy
                    .isSuccessful(
                        status =
                            status,
                        progress =
                            100
                    )
            )

            assertFalse(
                status,
                BuildDiagnosisPolicy
                    .shouldDiagnoseFailure(
                        status
                    )
            )
        }
    }


    @Test
    fun actualFailuresAreDiagnosed() {
        val statuses =
            listOf(
                "failed",
                "failure",
                "Hata: Gradle build",
                "Build başarısız",
                "Build failed",
                "Error: compilation",
                "Kotlin Exception"
            )

        statuses.forEach {
            status ->

            assertTrue(
                status,
                BuildDiagnosisPolicy
                    .shouldDiagnoseFailure(
                        status
                    )
            )
        }
    }


    @Test
    fun runningAndCancelledBuildsAreNotFailures() {
        val statuses =
            listOf(
                "Hazır",
                "Derleniyor",
                "Building",
                "Queued",
                "cancelled",
                "canceled",
                "İptal edildi"
            )

        statuses.forEach {
            status ->

            assertFalse(
                status,
                BuildDiagnosisPolicy
                    .shouldDiagnoseFailure(
                        status
                    )
            )
        }
    }


    @Test
    fun progressAloneDoesNotInventSuccess() {
        assertFalse(
            BuildDiagnosisPolicy
                .isSuccessful(
                    status =
                        "Derleniyor",
                    progress =
                        100
                )
        )
    }
}
