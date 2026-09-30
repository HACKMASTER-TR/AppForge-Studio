package com.appforge.studio.build

import android.content.Context
import com.appforge.studio.security.OwnerAccessPolicy
import java.io.File

/**
 * Windows publisher signing is intentionally owner/admin-only.
 *
 * V1 establishes the visibility and engine guard without storing a certificate,
 * private key or password in source. A later secure provider may enable signing
 * only after importing signing material into owner-private storage.
 */
internal object WindowsPublisherSigningPolicy {
    private const val PREFS =
        "appforge_windows_publisher_signing_admin"

    private const val ENABLED =
        "enabled"

    fun adminSectionVisible(
        context: Context
    ): Boolean =
        OwnerAccessPolicy
            .isActiveOwner(
                context
            )

    fun signingRequested(
        context: Context
    ): Boolean =
        adminSectionVisible(
            context
        ) &&
            context
                .getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE
                )
                .getBoolean(
                    ENABLED,
                    false
                )

    fun applyIfRequested(
        context: Context,
        target: File,
        onLog: (String) -> Unit = {}
    ) {
        if (
            !signingRequested(
                context
            )
        ) {
            return
        }

        OwnerAccessPolicy
            .requireActiveOwner(
                context
            )

        require(
            target.isFile &&
                target.length() > 0L
        ) {
            "Windows publisher signing target is invalid."
        }

        onLog(
            "🔐 Windows publisher signing • yönetici doğrulandı."
        )

        error(
            "Windows publisher signing etkin ancak güvenli sertifika sağlayıcısı henüz yapılandırılmadı. İmzasız dosya yayınlanmadı."
        )
    }
}
