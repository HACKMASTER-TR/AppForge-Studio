package com.appforge.studio.terminal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.appforge.studio.security.OwnerAccessPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun OwnerFilesPanel(
    accountEmail: String,
    legacyWorkspace: File
) {
    val context =
        LocalContext.current

    val authorized =
        remember(accountEmail) {
            OwnerAccessPolicy
                .isActiveOwner(
                    context,
                    accountEmail
                )
        }

    if (!authorized) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp)
        ) {
            Text(
                "Erişim reddedildi."
            )
        }

        return
    }

    val apkRoot =
        remember(accountEmail) {
            OwnerAccessPolicy
                .apkRoot(
                    context,
                    accountEmail
                )
        }

    val root =
        remember(accountEmail) {
            OwnerAccessPolicy
                .githubRoot(
                    context,
                    accountEmail
                )

            OwnerAccessPolicy
                .secondBrainRoot(
                    context,
                    accountEmail
                )

            OwnerAccessPolicy
                .dashboardRoot(
                    context,
                    accountEmail
                )

            OwnerAccessPolicy
                .backupsRoot(
                    context,
                    accountEmail
                )

            OwnerAccessPolicy
                .logsRoot(
                    context,
                    accountEmail
                )

            OwnerAccessPolicy
                .configRoot(
                    context,
                    accountEmail
                )

            OwnerAccessPolicy
                .filesRoot(
                    context,
                    accountEmail
                )
        }

    /*
     * Eski sürümlerde AppForgeDownloads ve AppForge-Git-*
     * seçili workspace içine düşebiliyordu.
     *
     * Yalnız owner hesabında bunları özel kasaya taşı.
     */
    LaunchedEffect(
        accountEmail,
        legacyWorkspace.absolutePath
    ) {
        withContext(
            Dispatchers.IO
        ) {
            migrateLegacyOwnerFiles(
                context = context,
                accountEmail = accountEmail,
                legacyWorkspace =
                    legacyWorkspace
            )

            OwnerArtifactReferenceStore
                .adoptDuplicateExeCopies(
                    context
                )

            OwnerPrivateFileSync.sync(
                context = context,
                accountEmail = accountEmail
            )
        }
    }

    WorkspaceFilesPanel(
        workspace =
            root,
        additionalEntries = { directory ->
            if (
                directory.canonicalFile ==
                    apkRoot.canonicalFile
            ) {
                OwnerArtifactReferenceStore
                    .list(
                        context
                    )
                    .map { reference ->
                        WorkspaceEntry(
                            file =
                                File(
                                    apkRoot,
                                    reference.displayName
                                ),
                            relativePath =
                                "APK/${reference.displayName}",
                            isDirectory =
                                false,
                            sizeBytes =
                                reference.sizeBytes,
                            modifiedAt =
                                reference.modifiedAt,
                            referenceId =
                                reference.id
                        )
                    }
            } else {
                emptyList()
            }
        },
        onDeleteReference = { entry ->
            entry.referenceId
                ?.let { referenceId ->
                    OwnerArtifactReferenceStore
                        .remove(
                            context,
                            referenceId
                        )
                }
                ?: false
        }
    )
}


private fun migrateLegacyOwnerFiles(
    context: android.content.Context,
    accountEmail: String,
    legacyWorkspace: File
) {
    OwnerAccessPolicy
        .requireActiveOwner(
            context,
            accountEmail
        )

    val legacy =
        runCatching {
            legacyWorkspace
                .canonicalFile
        }.getOrNull()
            ?: return

    if (
        !legacy.isDirectory ||
        !legacy.canRead()
    ) {
        return
    }

    val apkRoot =
        OwnerAccessPolicy
            .apkRoot(
                context,
                accountEmail
            )
            .canonicalFile

    val githubRoot =
        OwnerAccessPolicy
            .githubRoot(
                context,
                accountEmail
            )
            .canonicalFile

    /*
     * AppForgeDownloads:
     * klasörün kendisini değil içeriğini APK alanına taşı.
     */
    val downloads =
        File(
            legacy,
            "AppForgeDownloads"
        )

    if (
        downloads.isDirectory
    ) {
        downloads
            .listFiles()
            .orEmpty()
            .filterNot { source ->
                source.name.startsWith(".") ||
                    source.name.endsWith(
                        ".part",
                        ignoreCase = true
                    )
            }
            .forEach { source ->
                moveLegacyItem(
                    source,
                    apkRoot
                )
            }

        /*
         * AppForgeDownloads is a permanent bridge between the Linux
         * terminal and the Android owner APK vault. Never delete it.
         */
    }

    /*
     * AppForge Git clone klasörleri.
     * Gerçek proje .git klasörüne ASLA dokunma.
     */
    legacy
        .listFiles()
        .orEmpty()
        .filter { file ->
            file.isDirectory &&
                file.name.startsWith(
                    "AppForge-Git-",
                    ignoreCase = true
                )
        }
        .forEach { source ->
            moveLegacyItem(
                source,
                githubRoot
            )
        }
}


private fun moveLegacyItem(
    source: File,
    destinationRoot: File
) {
    val root =
        destinationRoot
            .canonicalFile

    val safeName =
        source.name

    if (
        safeName.isBlank() ||
        safeName == "." ||
        safeName == ".."
    ) {
        return
    }

    var target =
        File(
            root,
            safeName
        ).canonicalFile

    if (
        target.parentFile != root
    ) {
        return
    }

    /*
     * AppForge's own self-update APK is a singleton latest artifact.
     *
     * Copy the incoming file to a staging name first. Only after the
     * complete file has been verified do we replace the previous latest
     * copy and remove legacy _2/_3 duplicates.
     *
     * Project APK/EXE artifacts retain the collision-safe behavior below.
     */
    val isAppForgeLatestApk =
        !source.isDirectory &&
            safeName.equals(
                "AppForgeStudio-latest.apk",
                ignoreCase = true
            )

    if (
        isAppForgeLatestApk
    ) {
        val staged =
            File(
                root,
                ".AppForgeStudio-latest.apk.new"
            ).canonicalFile

        if (
            staged.parentFile != root
        ) {
            return
        }

        if (
            staged.exists()
        ) {
            staged.delete()
        }

        source.copyTo(
            staged,
            overwrite = true
        )

        if (
            !staged.isFile ||
            staged.length() != source.length()
        ) {
            staged.delete()
            return
        }

        root
            .listFiles()
            .orEmpty()
            .filter { existing ->
                existing.isFile &&
                    Regex(
                        """(?i)^AppForgeStudio-(?:latest|[0-9a-f]{7,40})(?:_\d+)?\.apk$"""
                    ).matches(
                        existing.name
                    )
            }
            .forEach { existing ->
                existing.delete()
            }

        if (
            !staged.renameTo(
                target
            )
        ) {
            staged.copyTo(
                target,
                overwrite = true
            )

            staged.delete()
        }

        if (
            target.isFile &&
            target.length() ==
                source.length()
        ) {
            source.delete()
        }

        return
    }

    /*
     * Other artifacts keep collision protection.
     */
    if (
        target.exists()
    ) {
        val stem =
            target.nameWithoutExtension

        val ext =
            target.extension
                .takeIf {
                    it.isNotBlank()
                }
                ?.let {
                    ".$it"
                }
                .orEmpty()

        var index = 2

        while (
            target.exists()
        ) {
            target =
                File(
                    root,
                    "${stem}_$index$ext"
                ).canonicalFile

            index += 1

            if (
                target.parentFile != root
            ) {
                return
            }
        }
    }

    /*
     * Önce atomik rename dene.
     * Farklı filesystem ise güvenli copy + delete.
     */
    if (
        source.renameTo(
            target
        )
    ) {
        return
    }

    if (
        source.isDirectory
    ) {
        source.copyRecursively(
            target,
            overwrite = false
        )

        if (
            target.exists()
        ) {
            source.deleteRecursively()
        }
    } else {
        source.copyTo(
            target,
            overwrite = false
        )

        if (
            target.isFile &&
            target.length() ==
                source.length()
        ) {
            source.delete()
        }
    }
}
