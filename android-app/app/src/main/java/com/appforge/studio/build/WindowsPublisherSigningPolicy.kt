package com.appforge.studio.build

import android.content.Context
import com.appforge.studio.security.OwnerAccessPolicy
import com.appforge.studio.terminal.LinuxShellEngine
import java.io.File

/**
 * Windows publisher signing is owner/admin-only and fail-closed.
 *
 * Signing material never lives in source, project payloads or GitHub.
 */
internal object WindowsPublisherSigningPolicy {
    private const val PREFS =
        "appforge_windows_publisher_signing_admin"

    private const val ENABLED =
        "enabled"

    private const val PROVIDER =
        "provider"

    fun adminSectionVisible(
        context: Context
    ): Boolean =
        OwnerAccessPolicy
            .isActiveOwner(
                context
            )

    /*
     * Deliberately not owner-filtered:
     * if signing was enabled by the owner and the owner session later
     * disappears, applyIfRequested must fail closed rather than silently
     * releasing an unsigned EXE.
     */
    fun signingRequested(
        context: Context
    ): Boolean =
        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .getBoolean(
                ENABLED,
                false
            )

    fun configuredProvider(
        context: Context
    ): String? =
        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .getString(
                PROVIDER,
                null
            )

    fun providerConfigured(
        context: Context
    ): Boolean {
        val provider =
            WindowsPublisherSigningProviders
                .resolve(
                    configuredProvider(
                        context
                    )
                )

        return provider != null &&
            provider.isConfigured(
                context
            )
    }


    fun configureLocalPkcs12(
        context: Context,
        pkcs12Bytes: ByteArray,
        password: CharArray
    ) {
        OwnerAccessPolicy
            .requireActiveOwner(
                context
            )

        WindowsPublisherSigningStore
            .importPkcs12(
                context = context,
                pkcs12Bytes = pkcs12Bytes,
                password = password
            )

        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                PROVIDER,
                WindowsPublisherSigningStore.PROVIDER_ID
            )
            .putBoolean(
                ENABLED,
                true
            )
            .apply()
    }

    fun setSigningEnabled(
        context: Context,
        enabled: Boolean
    ) {
        OwnerAccessPolicy
            .requireActiveOwner(
                context
            )

        if (enabled) {
            val provider =
                WindowsPublisherSigningProviders
                    .resolve(
                        configuredProvider(
                            context
                        )
                    )

            check(
                provider != null &&
                    provider.isConfigured(
                        context
                    )
            ) {
                "Windows publisher signing etkin ancak güvenli sertifika sağlayıcısı henüz yapılandırılmadı."
            }
        }

        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .putBoolean(
                ENABLED,
                enabled
            )
            .apply()
    }

    fun clearConfiguration(
        context: Context
    ) {
        OwnerAccessPolicy
            .requireActiveOwner(
                context
            )

        WindowsPublisherSigningStore
            .clear(
                context
            )

        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .clear()
            .apply()
    }

    fun applyIfRequested(
        context: Context,
        target: File,
        rootfs: File,
        shell: LinuxShellEngine,
        buildId: String,
        offline: Boolean,
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

        val provider =
            WindowsPublisherSigningProviders
                .resolve(
                    configuredProvider(
                        context
                    )
                )

        check(
            provider != null &&
                provider.isConfigured(
                    context
                )
        ) {
            "Windows publisher signing etkin ancak güvenli sertifika sağlayıcısı henüz yapılandırılmadı."
        }

        onLog(
            "🔐 Windows publisher signing • yönetici doğrulandı • provider=${provider.id}"
        )

        provider.sign(
            context = context,
            target = target,
            rootfs = rootfs,
            shell = shell,
            buildId = buildId,
            offline = offline,
            onLog = onLog
        )
    }
}
