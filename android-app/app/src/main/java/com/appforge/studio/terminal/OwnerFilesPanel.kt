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

    val root =
        remember(accountEmail) {
            OwnerAccessPolicy
                .apkRoot(
                    context,
                    accountEmail
                )

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

            OwnerPrivateFileSync.sync(
                context = context,
                accountEmail = accountEmail
            )
        }
    }

    WorkspaceFilesPanel(
        workspace =
            root
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
            .forEach { source ->
                moveLegacyItem(
                    source,
                    apkRoot
                )
            }

        if (
            downloads
                .listFiles()
                .orEmpty()
                .isEmpty()
        ) {
            downloads.delete()
        }
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
     * Aynı isim varsa mevcut dosyanın üzerine yazma.
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
