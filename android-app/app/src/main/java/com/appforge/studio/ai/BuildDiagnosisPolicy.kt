package com.appforge.studio.ai


/*
 * Central policy for deciding whether current build state represents
 * success or an actual failure.
 *
 * Log presence alone must never turn a successful build into an error.
 */
internal object BuildDiagnosisPolicy {

    fun isSuccessful(
        status: String,
        progress: Int
    ): Boolean {
        val normalized =
            normalize(
                status
            )

        if (
            normalized.isBlank()
        ) {
            return false
        }

        val explicitSuccess =
            normalized ==
                "success" ||
            normalized ==
                "succeeded" ||
            normalized ==
                "completed" ||
            normalized ==
                "done" ||
            normalized
                .startsWith(
                    "başarılı"
                ) ||
            normalized
                .startsWith(
                    "tamamlandı"
                ) ||
            normalized
                .contains(
                    "build succeeded"
                ) ||
            normalized
                .contains(
                    "build successful"
                )

        if (
            explicitSuccess
        ) {
            return true
        }

        /*
         * 100% alone is not enough to call a build successful because
         * some failure paths can also finish their progress lifecycle.
         */
        return false
    }


    fun shouldDiagnoseFailure(
        status: String
    ): Boolean {
        val normalized =
            normalize(
                status
            )

        if (
            normalized.isBlank()
        ) {
            return false
        }

        if (
            isSuccessful(
                status =
                    status,
                progress =
                    100
            )
        ) {
            return false
        }

        /*
         * Cancellation is a terminal state, but it is not a build
         * failure that should be diagnosed as Gradle/project error.
         */
        if (
            normalized ==
                "cancelled" ||
            normalized ==
                "canceled" ||
            normalized
                .contains(
                    "iptal"
                )
        ) {
            return false
        }

        return (
            normalized ==
                "failed" ||
            normalized ==
                "failure" ||
            normalized
                .startsWith(
                    "hata"
                ) ||
            normalized
                .contains(
                    "başarısız"
                ) ||
            normalized
                .contains(
                    "build failed"
                ) ||
            normalized
                .contains(
                    " error"
                ) ||
            normalized
                .startsWith(
                    "error"
                ) ||
            normalized
                .contains(
                    "exception"
                )
        )
    }


    private fun normalize(
        status: String
    ): String =
        status
            .trim()
            .lowercase()
}
