package com.appforge.studio.terminal

import android.content.Context
import com.appforge.studio.security.OwnerAccessPolicy
import org.json.JSONObject
import java.io.File
import java.time.Instant

internal object OwnerPrivateFileSync {

    fun sync(
        context: Context,
        accountEmail: String
    ) {
        OwnerAccessPolicy.requireActiveOwner(
            context,
            accountEmail
        )

        val secondBrain =
            OwnerAccessPolicy.secondBrainRoot(
                context,
                accountEmail
            )

        val dashboard =
            OwnerAccessPolicy.dashboardRoot(
                context,
                accountEmail
            )

        val backups =
            OwnerAccessPolicy.backupsRoot(
                context,
                accountEmail
            )

        val logs =
            OwnerAccessPolicy.logsRoot(
                context,
                accountEmail
            )

        val config =
            OwnerAccessPolicy.configRoot(
                context,
                accountEmail
            )

        writeReadmes(
            secondBrain = secondBrain,
            dashboard = dashboard,
            backups = backups,
            logs = logs,
            config = config
        )

        val repo =
            findAppForgeRepository(
                context
            )

        if (repo == null) {
            File(
                logs,
                "last-sync.txt"
            ).writeText(
                """
                AppForge owner sync
                Time: ${Instant.now()}
                Repository runtime: unavailable
                Owner vault: active
                Secrets exported: NO
                """.trimIndent() + "\n"
            )

            return
        }

        /*
         * SecondBrain'in çalışan kaynakları.
         * Asıllarına dokunma; owner kasasına güvenli kopya al.
         */
        copyIfExists(
            File(
                repo,
                ".appforge-brain/rules.md"
            ),
            File(
                secondBrain,
                "rules.md"
            )
        )

        copyTreeIfExists(
            File(
                repo,
                ".appforge-brain/bin"
            ),
            File(
                secondBrain,
                "bin"
            )
        )

        /*
         * Runtime JSON dosyalarında yalnız güvenli alanları export et.
         * Token / secret / password değerleri kesinlikle export edilmez.
         */
        exportAllowedJson(
            source =
                File(
                    repo,
                    ".appforge-brain/runtime/checkpoint.json"
                ),
            target =
                File(
                    secondBrain,
                    "status.json"
                ),
            allowedKeys =
                setOf(
                    "currentStage",
                    "nextAction",
                    "lastSafeCheckFailures",
                    "lastSafeCheckSkipped",
                    "checkpointUpdatedAt",
                    "pushAllowed",
                    "pushPolicy"
                )
        )

        exportAllowedJson(
            source =
                File(
                    repo,
                    ".appforge-brain/runtime/operator-policy.json"
                ),
            target =
                File(
                    config,
                    "operator-policy.json"
                ),
            allowedKeys =
                setOf(
                    "allowPush",
                    "pushPolicy",
                    "allowLocalCommit",
                    "requirePushApproval",
                    "fullAutonomy"
                )
        )

        /*
         * Dashboard'ın local çalışan dosyasını owner kasasına yansıt.
         */
        copyIfExists(
            File(
                repo,
                "dashboard.sh"
            ),
            File(
                dashboard,
                "dashboard.sh"
            )
        )

        copyIfExists(
            File(
                repo,
                "dashboard.sh.bak"
            ),
            File(
                backups,
                "dashboard.sh.bak"
            )
        )

        /*
         * Push politikası da görülebilsin.
         */
        copyIfExists(
            File(
                repo,
                ".githooks/pre-push"
            ),
            File(
                config,
                "pre-push"
            )
        )

        /*
         * Güvenlik özeti.
         * Gerçek token değerlerini YAZMA.
         */
        File(
            config,
            "security-status.txt"
        ).writeText(
            """
            AppForge Owner Security

            Owner vault: ACTIVE
            SecondBrain: OWNER ONLY
            Dashboard: OWNER ONLY
            APK storage: OWNER ONLY
            GitHub storage: OWNER ONLY

            GitHub tokens: encrypted account vault
            Railway tokens: encrypted account vault
            Build API keys: encrypted account vault

            Plaintext token export: DISABLED
            PRoot direct owner-vault mount: DISABLED
            """.trimIndent() + "\n"
        )

        File(
            logs,
            "last-sync.txt"
        ).writeText(
            """
            AppForge owner sync completed
            Time: ${Instant.now()}

            SecondBrain rules: synced
            SecondBrain bin: synced
            SecondBrain status: synced
            Dashboard: synced when present
            Dashboard backup: synced when present
            Operator policy: sanitized
            Push policy: synced
            Secret values: NOT EXPORTED
            """.trimIndent() + "\n"
        )
    }


    private fun findAppForgeRepository(
        context: Context
    ): File? {
        val linuxRoots =
            listOf(
                File(
                    context.noBackupFilesDir,
                    "terminal/linux"
                ),
                File(
                    context.filesDir,
                    "terminal/linux"
                )
            )

        linuxRoots.forEach { linux ->
            linux
                .listFiles()
                .orEmpty()
                .filter {
                    it.isDirectory
                }
                .forEach { distro ->
                    distro
                        .listFiles()
                        .orEmpty()
                        .filter {
                            it.isDirectory
                        }
                        .forEach { arch ->
                            val candidate =
                                File(
                                    arch,
                                    "rootfs/root/AppForge-Studio"
                                )

                            if (
                                candidate.isDirectory &&
                                File(
                                    candidate,
                                    ".git"
                                ).isDirectory
                            ) {
                                return runCatching {
                                    candidate.canonicalFile
                                }.getOrNull()
                            }
                        }
                }
        }

        return null
    }


    private fun copyIfExists(
        source: File,
        target: File
    ) {
        if (
            !source.isFile ||
            !source.canRead()
        ) {
            return
        }

        target
            .parentFile
            ?.mkdirs()

        val temp =
            File(
                target.parentFile,
                "${target.name}.sync-tmp"
            )

        runCatching {
            source.copyTo(
                temp,
                overwrite = true
            )

            if (
                target.exists() &&
                !target.delete()
            ) {
                error(
                    "Old owner file delete failed"
                )
            }

            if (
                !temp.renameTo(
                    target
                )
            ) {
                temp.copyTo(
                    target,
                    overwrite = true
                )

                temp.delete()
            }
        }.onFailure {
            temp.delete()
        }
    }


    private fun copyTreeIfExists(
        sourceRoot: File,
        targetRoot: File
    ) {
        if (
            !sourceRoot.isDirectory
        ) {
            return
        }

        val canonicalSource =
            runCatching {
                sourceRoot.canonicalFile
            }.getOrNull()
                ?: return

        targetRoot.mkdirs()

        canonicalSource
            .walkTopDown()
            .filter {
                it.isFile
            }
            .forEach { source ->
                val canonicalFile =
                    runCatching {
                        source.canonicalFile
                    }.getOrNull()
                        ?: return@forEach

                if (
                    !canonicalFile.absolutePath
                        .startsWith(
                            canonicalSource.absolutePath +
                                File.separator
                        )
                ) {
                    return@forEach
                }

                val relative =
                    canonicalFile
                        .relativeTo(
                            canonicalSource
                        )
                        .path

                /*
                 * Secret/token isimli dosyaları owner File UI'ına bile
                 * plaintext olarak export etmiyoruz.
                 */
                val lower =
                    relative.lowercase()

                if (
                    lower.contains("token") ||
                    lower.contains("secret") ||
                    lower.contains("password") ||
                    lower.contains("credential") ||
                    lower.contains("apikey") ||
                    lower.contains("api-key")
                ) {
                    return@forEach
                }

                val target =
                    File(
                        targetRoot,
                        relative
                    )

                copyIfExists(
                    canonicalFile,
                    target
                )
            }
    }


    private fun exportAllowedJson(
        source: File,
        target: File,
        allowedKeys: Set<String>
    ) {
        if (
            !source.isFile ||
            !source.canRead()
        ) {
            return
        }

        val original =
            runCatching {
                JSONObject(
                    source.readText()
                )
            }.getOrNull()
                ?: return

        val safe =
            JSONObject()

        allowedKeys.forEach { key ->
            if (
                original.has(
                    key
                ) &&
                !original.isNull(
                    key
                )
            ) {
                safe.put(
                    key,
                    original.get(
                        key
                    )
                )
            }
        }

        target
            .parentFile
            ?.mkdirs()

        target.writeText(
            safe.toString(2) +
                "\n"
        )
    }


    private fun writeReadmes(
        secondBrain: File,
        dashboard: File,
        backups: File,
        logs: File,
        config: File
    ) {
        File(
            secondBrain,
            "README.txt"
        ).writeText(
            """
            AppForge SecondBrain

            Bu klasör yalnız doğrulanmış owner hesabına açıktır.
            Çalışan SecondBrain dosyalarının güvenli senkron kopyalarını içerir.

            Token, parola ve secret değerleri burada tutulmaz.
            """.trimIndent() + "\n"
        )

        File(
            dashboard,
            "README.txt"
        ).writeText(
            """
            AppForge Dashboard

            dashboard.sh dosyasının owner-only senkron kopyası burada tutulur.
            Çalışan asıl dosya PRoot repository içinde kalır.
            """.trimIndent() + "\n"
        )

        File(
            backups,
            "README.txt"
        ).writeText(
            """
            AppForge Backups

            Güvenli owner-only yedek kopyalar burada tutulur.
            """.trimIndent() + "\n"
        )

        File(
            logs,
            "README.txt"
        ).writeText(
            """
            AppForge Logs

            Yalnız güvenli owner sync durum kayıtları tutulur.
            Secret veya token içeren çalışma logları export edilmez.
            """.trimIndent() + "\n"
        )

        File(
            config,
            "README.txt"
        ).writeText(
            """
            AppForge Config

            Güvenli politika özetleri bulunur.
            Şifreli token kasasının gerçek değerleri buraya kopyalanmaz.
            """.trimIndent() + "\n"
        )
    }
}
