package com.appforge.studio.build

import android.content.Context
import com.appforge.studio.security.OwnerAccessPolicy
import com.appforge.studio.terminal.LinuxShellEngine
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

internal interface WindowsPublisherSigningProvider {
    val id: String

    fun isConfigured(
        context: Context
    ): Boolean

    fun sign(
        context: Context,
        target: File,
        rootfs: File,
        shell: LinuxShellEngine,
        buildId: String,
        offline: Boolean,
        onLog: (String) -> Unit
    )
}

internal object WindowsPublisherSigningProviders {
    private val providers =
        listOf(
            LocalPkcs12WindowsPublisherSigningProvider
        )

    fun resolve(
        id: String?
    ): WindowsPublisherSigningProvider? =
        providers
            .firstOrNull {
                provider ->
                provider.id == id
            }
}

internal object LocalPkcs12WindowsPublisherSigningProvider :
    WindowsPublisherSigningProvider {

    override val id: String =
        WindowsPublisherSigningStore.PROVIDER_ID

    override fun isConfigured(
        context: Context
    ): Boolean =
        WindowsPublisherSigningStore
            .isConfigured(
                context
            )

    override fun sign(
        context: Context,
        target: File,
        rootfs: File,
        shell: LinuxShellEngine,
        buildId: String,
        offline: Boolean,
        onLog: (String) -> Unit
    ) {
        OwnerAccessPolicy
            .requireActiveOwner(
                context
            )

        check(
            isConfigured(
                context
            )
        ) {
            "Windows publisher signing PKCS#12 provider yapılandırılmadı."
        }

        check(
            !offline
        ) {
            "Windows publisher signing timestamp doğrulaması için internet bağlantısı gerekli."
        }

        require(
            target.isFile &&
                target.length() > 0L
        ) {
            "Windows publisher signing input EXE geçersiz."
        }

        val signingWorkspace =
            File(
                context.cacheDir,
                "windows-publisher-signing/" +
                    buildId +
                    "-" +
                    UUID
                        .randomUUID()
                        .toString()
                        .replace(
                            "-",
                            ""
                        )
                        .take(
                            8
                        )
            )

        signingWorkspace
            .deleteRecursively()

        signingWorkspace
            .mkdirs()

        check(
            signingWorkspace.isDirectory
        ) {
            "Windows signing izole çalışma alanı oluşturulamadı."
        }

        val unsigned =
            File(
                signingWorkspace,
                "unsigned.exe"
            )

        val signed =
            File(
                signingWorkspace,
                "signed.exe"
            )

        target.copyTo(
            unsigned,
            overwrite = true
        )

        WindowsPublisherSigningStore
            .materializeInto(
                context = context,
                destination = signingWorkspace
            )

        try {
            val command =
                """
                set -eu
                umask 077

                if ! command -v osslsigncode >/dev/null 2>&1; then
                  export DEBIAN_FRONTEND=noninteractive
                  apt-get update
                  apt-get install -y --no-install-recommends osslsigncode ca-certificates
                fi

                test -s /workspace/unsigned.exe
                test -s /workspace/signer.pfx
                test -s /workspace/pass.txt

                chmod 600 /workspace/signer.pfx /workspace/pass.txt
                rm -f /workspace/signed.exe

                osslsigncode sign \
                  -pkcs12 /workspace/signer.pfx \
                  -readpass /workspace/pass.txt \
                  -h sha256 \
                  -ts http://timestamp.digicert.com \
                  -n "AppForge Windows Application" \
                  -in /workspace/unsigned.exe \
                  -out /workspace/signed.exe

                test -s /workspace/signed.exe

                osslsigncode verify \
                  -in /workspace/signed.exe

                echo APPFORGE_WINDOWS_PUBLISHER_SIGNING=PASS
                """.trimIndent()

            val result =
                runBlocking {
                    shell.execute(
                        sessionId =
                            "signing-" + buildId,
                        rootfs =
                            rootfs,
                        workspace =
                            signingWorkspace,
                        command =
                            command,
                        confirmed =
                            true,
                        timeoutMs =
                            900_000L
                    )
                }

            check(
                !result.timedOut
            ) {
                "Windows publisher signing zaman aşımına uğradı."
            }

            result
                .output
                .lineSequence()
                .filter {
                    it.isNotBlank()
                }
                .toList()
                .takeLast(
                    40
                )
                .forEach {
                    line ->
                    onLog(
                        "SIGN ${line.take(700)}"
                    )
                }

            check(
                result.exitCode == 0
            ) {
                result.output
                    .takeLast(
                        3000
                    )
                    .ifBlank {
                        "Windows publisher signing başarısız."
                    }
            }

            check(
                signed.isFile &&
                    signed.length() > 0L
            ) {
                "Windows publisher signing çıktı EXE üretmedi."
            }

            runCatching {
                Files.move(
                    signed.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            }.getOrElse {
                signed.copyTo(
                    target,
                    overwrite = true
                )
            }

            check(
                target.isFile &&
                    target.length() > 0L
            ) {
                "İmzalı Windows EXE final artifact konumuna taşınamadı."
            }

            onLog(
                "✅ Windows Authenticode publisher signing PASS • SHA-256 + RFC3161 timestamp."
            )
        } finally {
            signingWorkspace
                .deleteRecursively()
        }
    }
}
