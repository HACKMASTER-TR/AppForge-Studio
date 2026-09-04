package com.appforge.studio.terminal

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal

internal object TerminalBiometricGuard {
    fun isAvailable(
        context: Context
    ): Boolean {
        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.P
        ) {
            return false
        }

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.Q
        ) {
            val manager =
                context.getSystemService(
                    BiometricManager::class.java
                )

            return manager
                ?.canAuthenticate() ==
                BiometricManager.BIOMETRIC_SUCCESS
        }

        // Android 9 does not expose platform canAuthenticate().
        // The prompt itself remains the source of truth on API 28.
        return true
    }

    fun authenticate(
        context: Context,
        title: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ): CancellationSignal? {
        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.P
        ) {
            onError(
                "Bu Android sürümünde platform biyometrik doğrulaması kullanılamıyor."
            )
            return null
        }

        val executor =
            context.mainExecutor

        val cancellation =
            CancellationSignal()

        val prompt =
            BiometricPrompt
                .Builder(context)
                .setTitle(
                    title.take(80)
                )
                .setSubtitle(
                    "AppForge Terminal güvenlik doğrulaması"
                )
                .setNegativeButton(
                    "Vazgeç",
                    executor
                ) { _, _ ->
                    onError(
                        "Biyometrik doğrulama iptal edildi."
                    )
                }
                .build()

        prompt.authenticate(
            cancellation,
            executor,
            object :
                BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result:
                        BiometricPrompt.AuthenticationResult
                ) {
                    onSuccess()
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence
                ) {
                    onError(
                        errString
                            .toString()
                            .take(240)
                            .ifBlank {
                                "Biyometrik doğrulama başarısız."
                            }
                    )
                }

                override fun onAuthenticationFailed() {
                    // Keep the prompt open. A failed scan must not unlock
                    // or start a sensitive operation.
                }
            }
        )

        return cancellation
    }
}
