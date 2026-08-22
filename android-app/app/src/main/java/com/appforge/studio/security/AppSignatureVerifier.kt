package com.appforge.studio.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.appforge.studio.BuildConfig
import java.security.MessageDigest

data class SignatureCheck(
    val configured: Boolean,
    val valid: Boolean,
    val detectedSha256: List<String>
)

object AppSignatureVerifier {
    fun check(context: Context): SignatureCheck {
        val expected =
            BuildConfig.RELEASE_CERT_SHA256
                .trim()
                .replace(":", "")
                .uppercase()

        val signatures =
            if (Build.VERSION.SDK_INT >= 28) {
                val info =
                    context.packageManager.getPackageInfo(
                        context.packageName,
                        PackageManager.GET_SIGNING_CERTIFICATES
                    )

                val signingInfo =
                    info.signingInfo

                if (signingInfo == null) {
                    emptyArray()
                } else if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                ).signatures
            }

        val digests =
            signatures
                .orEmpty()
                .map { signature ->
                    MessageDigest
                        .getInstance("SHA-256")
                        .digest(signature.toByteArray())
                        .joinToString("") {
                            "%02X".format(it)
                        }
                }
                .distinct()

        if (expected.isBlank()) {
            return SignatureCheck(
                configured = false,
                valid = true,
                detectedSha256 = digests
            )
        }

        return SignatureCheck(
            configured = true,
            valid = digests.any {
                it == expected
            },
            detectedSha256 = digests
        )
    }
}
