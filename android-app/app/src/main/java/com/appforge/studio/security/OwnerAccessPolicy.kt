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

    fun isActiveOwner(
        context: Context,
        accountEmail: String? = null
    ): Boolean {
        val session =
            SecureAccountStore
                .loadSession(context)
                ?: return false

        if (!isOwnerEmail(session.email)) {
            return false
        }

        if (
            !accountEmail.isNullOrBlank() &&
            !session.email
                .trim()
                .equals(
                    accountEmail.trim(),
                    ignoreCase = true
                )
        ) {
            return false
        }

        return true
    }

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
