package com.appforge.studio.terminal

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
internal fun WorkspaceFilesPanel(
    workspace: File
) {
    val scope =
        rememberCoroutineScope()

    val context =
        LocalContext.current

    var currentDirectory by
        remember(workspace.absolutePath) {
            mutableStateOf(workspace)
        }

    var entries by
        remember(workspace.absolutePath) {
            mutableStateOf<List<WorkspaceEntry>>(
                emptyList()
            )
        }

    var refreshKey by
        remember(workspace.absolutePath) {
            mutableIntStateOf(0)
        }

    var loading by
        remember {
            mutableStateOf(true)
        }

    var message by
        remember {
            mutableStateOf("")
        }

    var createDirectory by
        remember {
            mutableStateOf<Boolean?>(
                null
            )
        }

    var deleteTarget by
        remember {
            mutableStateOf<WorkspaceEntry?>(
                null
            )
        }

    var editorTarget by
        remember {
            mutableStateOf<File?>(
                null
            )
        }

    var editorContent by
        remember {
            mutableStateOf("")
        }

    var editorLoading by
        remember {
            mutableStateOf(false)
        }

    LaunchedEffect(
        workspace.absolutePath,
        currentDirectory.absolutePath,
        refreshKey
    ) {
        loading =
            true

        message =
            ""

        runCatching {
            WorkspaceFileService.list(
                workspace,
                currentDirectory
            )
        }.onSuccess {
            entries = it
        }.onFailure {
            entries =
                emptyList()

            message =
                it.message
                    ?: "Dosyalar okunamadı."
        }

        loading =
            false
    }
    createDirectory
        ?.let { directory ->
            CreateWorkspaceItemDialog(
                directory = directory,
                onDismiss = {
                    createDirectory =
                        null
                },
                onCreate = { name ->
                    createDirectory =
                        null

                    scope.launch {
                        runCatching {
                            WorkspaceFileService.create(
                                root = workspace,
                                parent =
                                    currentDirectory,
                                name = name,
                                directory = directory
                            )
                        }.onSuccess {
                            message =
                                if (directory) {
                                    "Klasör oluşturuldu: ${it.name}"
                                } else {
                                    "Dosya oluşturuldu: ${it.name}"
                                }

                            refreshKey +=
                                1
                        }.onFailure {
                            message =
                                it.message
                                    ?: "Öğe oluşturulamadı."
                        }
                    }
                }
            )
        }

    deleteTarget
        ?.let { target ->
            AlertDialog(
                onDismissRequest = {
                    deleteTarget =
                        null
                },
                title = {
                    Text("Geri dönüşüme taşı")
                },
                text = {
                    Text(
                        "${target.file.name} proje içindeki gizli geri dönüşüm alanına taşınacak."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            deleteTarget =
                                null

                            scope.launch {
                                runCatching {
                                    WorkspaceFileService.moveToTrash(
                                        workspace,
                                        target.file
                                    )
                                }.onSuccess {
                                    message =
                                        "Öğe geri dönüşüme taşındı."

                                    refreshKey +=
                                        1
                                }.onFailure {
                                    message =
                                        it.message
                                            ?: "Öğe taşınamadı."
                                }
                            }
                        }
                    ) {
                        Text("Taşı")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            deleteTarget =
                                null
                        }
                    ) {
                        Text("Vazgeç")
                    }
                }
            )
        }

    editorTarget
        ?.let { file ->
            FileEditorDialog(
                file = file,
                content = editorContent,
                loading = editorLoading,
                onContentChange = {
                    editorContent = it
                },
                onDismiss = {
                    editorTarget =
                        null
                },
                onSave = {
                    editorLoading =
                        true

                    scope.launch {
                        runCatching {
                            WorkspaceFileService.writeText(
                                workspace,
                                file,
                                editorContent
                            )
                        }.onSuccess {
                            message =
                                "Kaydedildi: ${file.name}"

                            editorTarget =
                                null

                            refreshKey +=
                                1
                        }.onFailure {
                            message =
                                it.message
                                    ?: "Dosya kaydedilemedi."
                        }

                        editorLoading =
                            false
                    }
                }
            )
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(12.dp),
        verticalArrangement =
            Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth(),
            verticalAlignment =
                Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val parent =
                        currentDirectory
                            .parentFile

                    if (
                        parent != null &&
                        runCatching {
                            parent.canonicalFile.path
                                .startsWith(
                                    workspace.canonicalPath
                                )
                        }.getOrDefault(false)
                    ) {
                        currentDirectory =
                            parent
                    }
                },
                enabled =
                    runCatching {
                        currentDirectory.canonicalFile !=
                            workspace.canonicalFile
                    }.getOrDefault(false)
            ) {
                Text("‹ Üst")
            }

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {
                Text(
                    terminalRelativePath(
                        workspace,
                        currentDirectory
                    ),
                    color =
                        TerminalPrimary,
                    fontFamily =
                        FontFamily.Monospace,
                    fontWeight =
                        FontWeight.Bold,
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis
                )

                Text(
                    "Yalnız seçili proje alanı erişilebilir",
                    color =
                        TerminalMuted,
                    fontSize =
                        10.sp
                )
            }

            OutlinedButton(
                onClick = {
                    createDirectory =
                        false
                }
            ) {
                Text("+ Dosya")
            }

            OutlinedButton(
                onClick = {
                    createDirectory =
                        true
                }
            ) {
                Text("+ Klasör")
            }
        }

        if (message.isNotBlank()) {
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            TerminalSurfaceRaised
                    )
            ) {
                Text(
                    message,
                    color =
                        if (
                            message.contains(
                                "oluşturulamadı",
                                ignoreCase = true
                            ) ||
                            message.contains(
                                "kaydedilemedi",
                                ignoreCase = true
                            ) ||
                            message.contains(
                                "okunamadı",
                                ignoreCase = true
                            )
                        ) {
                            TerminalError
                        } else {
                            TerminalText
                        },
                    modifier =
                        Modifier.padding(10.dp),
                    fontSize =
                        11.sp
                )
            }
        }

        when {
            loading ->
                Box(
                    modifier =
                        Modifier.fillMaxSize(),
                    contentAlignment =
                        Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color =
                            TerminalPrimary
                    )
                }

            entries.isEmpty() ->
                Card(
                    modifier =
                        Modifier.fillMaxWidth(),
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                TerminalSurface
                        )
                ) {
                    Column(
                        modifier =
                            Modifier.padding(20.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "Bu klasör boş",
                            color =
                                TerminalText,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            "Yeni dosya veya klasör oluşturabilir, terminalden bir proje indirebilirsin.",
                            color =
                                TerminalMuted,
                            fontSize =
                                12.sp
                        )
                    }
                }

            else ->
                LazyColumn(
                    modifier =
                        Modifier.fillMaxSize(),
                    verticalArrangement =
                        Arrangement.spacedBy(6.dp)
                ) {
                    items(
                        entries,
                        key = {
                            it.file.absolutePath
                        }
                    ) { entry ->
                        WorkspaceEntryCard(
                            entry = entry,
                            onOpen = {
                                if (entry.isDirectory) {
                                    currentDirectory =
                                        entry.file
                                } else if (
                                    entry.file.extension
                                        .equals(
                                            "apk",
                                            ignoreCase = true
                                        )
                                ) {
                                    scope.launch {
                                        runCatching {
                                            openWorkspaceApkInstaller(
                                                context =
                                                    context,
                                                source =
                                                    entry.file
                                            )
                                        }.onSuccess {
                                            message =
                                                it
                                        }.onFailure {
                                            message =
                                                "APK yükleyici açılamadı: " +
                                                    (
                                                        it.message
                                                            ?: "Bilinmeyen hata"
                                                    )
                                        }
                                    }
                                } else if (
                                    WorkspaceFileService
                                        .isTextFile(
                                            entry.file
                                        )
                                ) {
                                    editorLoading =
                                        true

                                    scope.launch {
                                        runCatching {
                                            WorkspaceFileService.readText(
                                                workspace,
                                                entry.file
                                            )
                                        }.onSuccess {
                                            editorContent =
                                                it

                                            editorTarget =
                                                entry.file
                                        }.onFailure {
                                            message =
                                                it.message
                                                    ?: "Dosya açılamadı."
                                        }

                                        editorLoading =
                                            false
                                    }
                                } else {
                                    message =
                                        "İkili dosya düzenleyicide açılamaz."
                                }
                            },
                            onDelete = {
                                deleteTarget =
                                    entry
                            }
                        )
                    }
                }
        }
    }
}


private suspend fun openWorkspaceApkInstaller(
    context: Context,
    source: File
): String {
    require(
        source.isFile &&
            source.extension.equals(
                "apk",
                ignoreCase = true
            )
    ) {
        "Geçerli APK dosyası bulunamadı."
    }

    if (
        Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O &&
        !context.packageManager
            .canRequestPackageInstalls()
    ) {
        val permissionIntent =
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse(
                    "package:${context.packageName}"
                )
            ).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )

        context.startActivity(
            permissionIntent
        )

        return "APK yükleme iznini ver, ardından dosyaya tekrar dokun."
    }

    val cachedApk =
        withContext(
            Dispatchers.IO
        ) {
            val installerDirectory =
                File(
                    context.cacheDir,
                    "apk-installer"
                ).apply {
                    check(
                        exists() ||
                            mkdirs()
                    ) {
                        "APK yükleme klasörü oluşturulamadı."
                    }
                }

            installerDirectory
                .listFiles()
                .orEmpty()
                .filter {
                    it.isFile &&
                        it.extension.equals(
                            "apk",
                            ignoreCase = true
                        )
                }
                .forEach {
                    runCatching {
                        it.delete()
                    }
                }

            File(
                installerDirectory,
                "AppForgeStudio-install.apk"
            ).also { destination ->
                source.copyTo(
                    target =
                        destination,
                    overwrite =
                        true
                )

                check(
                    destination.isFile &&
                        destination.length() ==
                            source.length()
                ) {
                    "APK güvenli yükleme alanına kopyalanamadı."
                }
            }
        }

    val uri =
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            cachedApk
        )

    val installIntent =
        Intent(
            Intent.ACTION_VIEW
        ).apply {
            setDataAndType(
                uri,
                "application/vnd.android.package-archive"
            )

            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )
        }

    context.startActivity(
        installIntent
    )

    return "Android APK yükleyici açıldı."
}


@Composable
private fun WorkspaceEntryCard(
    entry: WorkspaceEntry,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val date =
        remember(entry.modifiedAt) {
            DateFormat
                .getDateTimeInstance(
                    DateFormat.SHORT,
                    DateFormat.SHORT
                )
                .format(
                    Date(entry.modifiedAt)
                )
        }

    Card(
        onClick = onOpen,
        modifier =
            Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    TerminalSurface
            )
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 13.dp,
                        end = 6.dp,
                        top = 10.dp,
                        bottom = 10.dp
                    ),
            verticalAlignment =
                Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {
            Text(
                if (entry.isDirectory) {
                    "▰"
                } else {
                    "▤"
                },
                color =
                    if (entry.isDirectory) {
                        TerminalWarning
                    } else {
                        TerminalSecondary
                    },
                fontSize =
                    22.sp
            )

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {
                Text(
                    entry.file.name,
                    color =
                        TerminalText,
                    fontWeight =
                        FontWeight.Bold,
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis
                )

                Text(
                    if (entry.isDirectory) {
                        "Klasör • $date"
                    } else {
                        "${formatBytes(entry.sizeBytes)} • $date"
                    },
                    color =
                        TerminalMuted,
                    fontSize =
                        10.sp
                )
            }

            TextButton(
                onClick = onDelete
            ) {
                Text(
                    "Çöpe taşı",
                    color =
                        TerminalError,
                    fontSize =
                        10.sp
                )
            }
        }
    }
}

@Composable
private fun CreateWorkspaceItemDialog(
    directory: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by
        remember(directory) {
            mutableStateOf("")
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (directory) {
                    "Yeni klasör"
                } else {
                    "Yeni dosya"
                }
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                },
                modifier =
                    Modifier.fillMaxWidth(),
                label = {
                    Text("Ad")
                },
                placeholder = {
                    Text(
                        if (directory) {
                            "src"
                        } else {
                            "README.md"
                        }
                    )
                },
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(name)
                },
                enabled =
                    name.isNotBlank()
            ) {
                Text("Oluştur")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Vazgeç")
            }
        }
    )
}

@Composable
private fun FileEditorDialog(
    file: File,
    content: String,
    loading: Boolean,
    onContentChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    file.name,
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis
                )

                Text(
                    "UTF-8 metin düzenleyici",
                    color =
                        TerminalMuted,
                    fontSize =
                        10.sp
                )
            }
        },
        text = {
            Column {
                OutlinedTextField(
                    value = content,
                    onValueChange =
                        onContentChange,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(
                                min = 260.dp,
                                max = 520.dp
                            ),
                    enabled =
                        !loading,
                    textStyle =
                        androidx.compose.ui.text.TextStyle(
                            fontFamily =
                                FontFamily.Monospace,
                            fontSize =
                                12.sp
                        )
                )

                HorizontalDivider(
                    modifier =
                        Modifier.padding(
                            top = 8.dp
                        )
                )

                Text(
                    "${content.toByteArray(Charsets.UTF_8).size} bayt",
                    color =
                        TerminalMuted,
                    fontSize =
                        9.sp,
                    modifier =
                        Modifier.padding(
                            top = 5.dp
                        )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled =
                    !loading
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Kaydet")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled =
                    !loading
            ) {
                Text("Kapat")
            }
        }
    )
}

private fun formatBytes(bytes: Long): String =
    when {
        bytes < 1_024L ->
            "$bytes B"

        bytes < 1_024L * 1_024L ->
            "%.1f KB".format(
                bytes / 1_024.0
            )

        else ->
            "%.1f MB".format(
                bytes / (
                    1_024.0 *
                        1_024.0
                    )
            )
    }
