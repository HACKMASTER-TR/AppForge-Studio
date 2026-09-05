package com.appforge.studio.terminal

import android.content.Context
import android.system.Os
import com.appforge.studio.security.SecureAccountStore
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

internal const val APPFORGE_GITHUB_CREDENTIAL_GUEST_PATH =
    "/run/appforge/github-credential"

internal const val APPFORGE_GIT_ASKPASS_GUEST_PATH =
    "/usr/local/bin/appforge-git-askpass"

internal class TerminalGitCredentialLease(
    val credentialFile: File,
    private val directory: File
) : AutoCloseable {
    private val closed =
        AtomicBoolean(false)

    override fun close() {
        if (
            closed.compareAndSet(
                false,
                true
            )
        ) {
            runCatching {
                directory.deleteRecursively()
            }
        }
    }
}

internal object TerminalGitCredentialBridge {
    private const val PROVIDER =
        "github"

    private const val CREDENTIAL_ROOT =
        "terminal/git-credentials-v1"

    private const val MAX_TOKEN_CHARS =
        32 * 1_024

    private const val MAX_USERNAME_CHARS =
        512

    private const val MODE_0700 =
        448

    private const val MODE_0600 =
        384

    fun clearStale(
        context: Context
    ) {
        runCatching {
            credentialBase(
                context.applicationContext
            ).deleteRecursively()
        }
    }

    fun prepare(
        context: Context
    ): TerminalGitCredentialLease? {
        val appContext =
            context.applicationContext

        val connection =
            SecureAccountStore
                .loadExternalConnection(
                    appContext,
                    PROVIDER
                )
                ?: return null

        val token =
            connection.accessToken
                .trim()

        if (token.isBlank()) {
            return null
        }

        require(
            token.length <=
                MAX_TOKEN_CHARS &&
                token.none {
                    it == '\n' ||
                        it == '\r' ||
                        it == '\u0000'
                }
        ) {
            "GitHub erişim anahtarı terminal için geçersiz."
        }

        val username =
            connection.accountLabel
                .substringBefore(
                    " • "
                )
                .trim()
                .ifBlank {
                    "x-access-token"
                }

        require(
            username.length <=
                MAX_USERNAME_CHARS &&
                username.none {
                    it == '\n' ||
                        it == '\r' ||
                        it == '\u0000'
                }
        ) {
            "GitHub kullanıcı bilgisi geçersiz."
        }

        val base =
            credentialBase(
                appContext
            )

        check(
            base.exists() ||
                base.mkdirs()
        ) {
            "Geçici Git kimlik klasörü oluşturulamadı."
        }

        check(base.isDirectory) {
            "Geçici Git kimlik yolu geçersiz."
        }

        Os.chmod(
            base.absolutePath,
            MODE_0700
        )

        val directory =
            File(
                base,
                UUID.randomUUID()
                    .toString()
            ).canonicalFile

        check(
            directory.parentFile ==
                base
        ) {
            "Geçici Git kimlik yolu güvenli değil."
        }

        check(
            directory.mkdir()
        ) {
            "Geçici Git oturum klasörü oluşturulamadı."
        }

        Os.chmod(
            directory.absolutePath,
            MODE_0700
        )

        val credential =
            File(
                directory,
                "github-credential"
            ).canonicalFile

        check(
            credential.parentFile ==
                directory
        ) {
            "Geçici Git kimlik dosyası güvenli değil."
        }

        val payload =
            buildString {
                append(username)
                append('\n')
                append(token)
                append('\n')
            }

        FileOutputStream(
            credential,
            false
        ).use { output ->
            output.write(
                payload.toByteArray(
                    Charsets.UTF_8
                )
            )
            output.fd.sync()
        }

        Os.chmod(
            credential.absolutePath,
            MODE_0600
        )

        return TerminalGitCredentialLease(
            credentialFile =
                credential,
            directory =
                directory
        )
    }

    private fun credentialBase(
        context: Context
    ): File {
        val cacheRoot =
            context.cacheDir
                .canonicalFile

        val candidate =
            File(
                cacheRoot,
                CREDENTIAL_ROOT
            ).canonicalFile

        check(
            candidate.absolutePath
                .startsWith(
                    cacheRoot.absolutePath +
                        File.separator
                )
        ) {
            "Git kimlik önbellek yolu güvenli değil."
        }

        return candidate
    }
}

internal object TerminalStandaloneDeveloperBootstrap {
    private const val MODE_0755 =
        493

    private const val MODE_0600 =
        384

    private const val MODE_0644 =
        420

    private const val REVISION =
        "appforge-standalone-workstation-v2"

    private val executableAssets =
        listOf(
            "appforge-git-askpass",
            "appforge-doctor",
            "appforge-ready",
            "appforge-test",
            "appforge-ci",
            "appforge-apk",
            "appforge-repair-tools",
            "appforge-project"
        )

    fun install(
        context: Context,
        rootfs: File
    ) {
        val safeRootfs =
            rootfs.canonicalFile

        require(
            safeRootfs.isDirectory &&
                File(
                    safeRootfs,
                    "bin/sh"
                ).exists()
        ) {
            "Standalone geliştirici ortamı için Linux rootfs hazır değil."
        }

        val binDirectory =
            safeDirectory(
                safeRootfs,
                "usr/local/bin"
            )

        executableAssets
            .forEach { name ->
                installExecutableAsset(
                    context =
                        context.applicationContext,
                    directory =
                        binDirectory,
                    assetName =
                        name
                )
            }

        val runtimeDirectory =
            safeDirectory(
                safeRootfs,
                "run/appforge"
            )

        val credentialMountPoint =
            File(
                runtimeDirectory,
                "github-credential"
            ).canonicalFile

        check(
            credentialMountPoint.parentFile ==
                runtimeDirectory
        ) {
            "Git kimlik mount noktası güvenli değil."
        }

        if (
            !credentialMountPoint.exists()
        ) {
            check(
                credentialMountPoint
                    .createNewFile()
            ) {
                "Git kimlik mount noktası oluşturulamadı."
            }
        }

        check(
            credentialMountPoint.isFile
        ) {
            "Git kimlik mount noktası geçersiz."
        }

        Os.chmod(
            credentialMountPoint.absolutePath,
            MODE_0600
        )

        val stateDirectory =
            safeDirectory(
                safeRootfs,
                "var/lib/appforge"
            )

        val marker =
            File(
                stateDirectory,
                "standalone-workstation-v2"
            ).canonicalFile

        check(
            marker.parentFile ==
                stateDirectory
        ) {
            "Standalone marker yolu güvenli değil."
        }

        if (
            !marker.isFile ||
            runCatching {
                marker.readText(
                    Charsets.UTF_8
                ).trim()
            }.getOrNull() !=
                REVISION
        ) {
            marker.writeText(
                REVISION + "\n",
                Charsets.UTF_8
            )
        }

        Os.chmod(
            marker.absolutePath,
            MODE_0644
        )
    }

    private fun safeDirectory(
        rootfs: File,
        relativePath: String
    ): File {
        val directory =
            File(
                rootfs,
                relativePath
            ).canonicalFile

        check(
            directory.absolutePath
                .startsWith(
                    rootfs.absolutePath +
                        File.separator
                )
        ) {
            "Standalone rootfs yolu güvenli değil: $relativePath"
        }

        check(
            directory.exists() ||
                directory.mkdirs()
        ) {
            "Standalone rootfs klasörü oluşturulamadı: $relativePath"
        }

        check(directory.isDirectory) {
            "Standalone rootfs yolu klasör değil: $relativePath"
        }

        return directory
    }

    private fun installExecutableAsset(
        context: Context,
        directory: File,
        assetName: String
    ) {
        require(
            assetName.matches(
                Regex(
                    "^[a-z0-9-]{1,64}$"
                )
            )
        ) {
            "Standalone asset adı geçersiz."
        }

        val target =
            File(
                directory,
                assetName
            ).canonicalFile

        check(
            target.parentFile ==
                directory
        ) {
            "Standalone executable yolu güvenli değil."
        }

        val bytes =
            context.assets
                .open(
                    "terminal/$assetName"
                )
                .use {
                    it.readBytes()
                }

        check(
            bytes.isNotEmpty() &&
                bytes.size <=
                128 * 1_024
        ) {
            "Standalone executable asset geçersiz: $assetName"
        }

        val currentMatches =
            target.isFile &&
                runCatching {
                    target
                        .readBytes()
                        .contentEquals(
                            bytes
                        )
                }.getOrDefault(false)

        if (!currentMatches) {
            FileOutputStream(
                target,
                false
            ).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
        }

        Os.chmod(
            target.absolutePath,
            MODE_0755
        )
    }
}
