package com.appforge.studio.security

import android.content.Context
import java.io.File
import java.security.MessageDigest

object OwnerAccessPolicy {

    private const val OWNER_EMAIL_SHA256 =
        "1249d3064d7f482d584f75caf93ea01649f13e4a26d183c4729d2fae5d205589"

    private const val OWNER_ROOT =
        "appforge-owner-vault-v1"

    private fun digest(
        value: String?
    ): String {
        val normalized =
            value
                ?.trim()
                ?.lowercase()
                .orEmpty()

        if (normalized.isBlank()) {
            return ""
        }

        return MessageDigest
            .getInstance("SHA-256")
            .digest(
                normalized.toByteArray(
                    Charsets.UTF_8
                )
            )
            .joinToString("") { byte ->
                "%02x".format(
                    byte.toInt() and 0xff
                )
            }
    }

    fun isOwnerEmail(
        email: String?
    ): Boolean =
        digest(email) ==
            OWNER_EMAIL_SHA256

    // The owner gate is memory-only. An encrypted stored ID token is
    // merely a candidate until the HTTPS server verifies it again.
    // Email, local sessions and stored admin flags never grant access.
    @Volatile private var googleIdToken: String? = null
    @Volatile private var validUntilMs: Long = 0L

    fun clearVerifiedGoogleAdmin(context: Context? = null) {
        googleIdToken = null
        validUntilMs = 0L
        context?.let {
            SecureAccountStore.clearVerifiedGoogleAdmin(it)
        }
    }

    internal fun rememberVerifiedGoogleAdmin(
        context: Context,
        idToken: String,
        expiresAt: Long,
        serverUrl: String
    ) {
        val now = System.currentTimeMillis()
        val deadline = expiresAt.coerceAtMost(Long.MAX_VALUE / 1000) * 1000
        require(idToken.length in 50..12000 && deadline > now &&
            deadline - now <= 3_700_000L) { "Invalid verified Google admin session." }
        SecureAccountStore.saveVerifiedGoogleAdmin(
            context,
            idToken,
            expiresAt,
            serverUrl
        )
        googleIdToken = idToken
        validUntilMs = deadline
    }

    fun currentGoogleIdToken(): String? {
        val token = googleIdToken
        if (token == null || System.currentTimeMillis() >= validUntilMs) {
            clearVerifiedGoogleAdmin()
            return null
        }
        return token
    }

    @Suppress("UNUSED_PARAMETER")
    fun isActiveOwner(context: Context, accountEmail: String? = null): Boolean =
        currentGoogleIdToken() != null

    fun requireActiveOwner(
        context: Context,
        accountEmail: String? = null
    ) {
        check(
            isActiveOwner(
                context,
                accountEmail
            )
        ) {
            "Owner access denied."
        }
    }

    fun filesRoot(
        context: Context,
        accountEmail: String? = null
    ): File {
        requireActiveOwner(
            context,
            accountEmail
        )

        val base =
            context
                .noBackupFilesDir
                .canonicalFile

        val root =
            File(
                base,
                OWNER_ROOT
            ).canonicalFile

        check(
            root.absolutePath.startsWith(
                base.absolutePath +
                    File.separator
            )
        ) {
            "Invalid owner root."
        }

        check(
            root.exists() ||
                root.mkdirs()
        ) {
            "Owner root create failed."
        }

        return root
    }

    fun apkRoot(
        context: Context,
        accountEmail: String? = null
    ): File =
        childRoot(
            context,
            accountEmail,
            "APK"
        )

    fun githubRoot(
        context: Context,
        accountEmail: String? = null
    ): File =
        childRoot(
            context,
            accountEmail,
            "GitHub"
        )

    fun secondBrainRoot(
        context: Context,
        accountEmail: String? = null
    ): File =
        childRoot(
            context,
            accountEmail,
            "SecondBrain"
        )

    fun dashboardRoot(
        context: Context,
        accountEmail: String? = null
    ): File =
        childRoot(
            context,
            accountEmail,
            "Dashboard"
        )

    fun backupsRoot(
        context: Context,
        accountEmail: String? = null
    ): File =
        childRoot(
            context,
            accountEmail,
            "Backups"
        )

    fun logsRoot(
        context: Context,
        accountEmail: String? = null
    ): File =
        childRoot(
            context,
            accountEmail,
            "Logs"
        )

    fun configRoot(
        context: Context,
        accountEmail: String? = null
    ): File =
        childRoot(
            context,
            accountEmail,
            "Config"
        )

    private fun childRoot(
        context: Context,
        accountEmail: String?,
        name: String
    ): File {
        val root =
            filesRoot(
                context,
                accountEmail
            )

        val child =
            File(
                root,
                name
            ).canonicalFile

        check(
            child.absolutePath.startsWith(
                root.absolutePath +
                    File.separator
            )
        ) {
            "Invalid owner child root."
        }

        check(
            child.exists() ||
                child.mkdirs()
        ) {
            "Owner child root create failed."
        }

        return child
    }
}
