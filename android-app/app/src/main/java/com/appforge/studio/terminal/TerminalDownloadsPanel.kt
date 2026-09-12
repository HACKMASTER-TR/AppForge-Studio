package com.appforge.studio.terminal

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale

private const val MAX_IMPORTED_DOWNLOAD_BYTES =
    512L * 1024L * 1024L

private data class DownloadMetadata(
    val name: String,
    val size: Long?
)

private data class DownloadImportSummary(
    val imported: List<File>,
    val failed: List<String>
)

@Composable
internal fun TerminalDownloadsPanel(
    accountEmail: String,
    importRequestToken: Int,
    onImportRequestConsumed: () -> Unit,
    onRunCommand: (String) -> Unit
) {
    val context =
        LocalContext.current

    val scope =
        rememberCoroutineScope()

    val inbox =
        remember(
            accountEmail
        ) {
            TerminalDownloadInbox.resolve(
                context = context,
                accountEmail = accountEmail
            )
        }

    var message by
        remember(
            accountEmail
        ) {
            mutableStateOf("")
        }

    var importing by
        remember {
            mutableStateOf(false)
        }

    var refreshToken by
        remember {
            mutableIntStateOf(0)
        }

    var selectedEntry by
        remember {
            mutableStateOf<File?>(null)
        }

    val picker =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .OpenMultipleDocuments()
        ) { uris ->
            if (uris.isEmpty()) {
                message =
                    "Dosya seçilmedi."
            } else {
                importing =
                    true

                scope.launch {
                    val summary =
                        withContext(
                            Dispatchers.IO
                        ) {
                            TerminalDownloadInbox
                                .importUris(
                                    context =
                                        context,
                                    accountEmail =
                                        accountEmail,
                                    uris =
                                        uris
                                )
                        }

                    message =
                        buildString {
                            if (
                                summary
                                    .imported
                                    .isNotEmpty()
                            ) {
                                append(
                                    summary
                                        .imported
                                        .size
                                )

                                append(
                                    " dosya hesaba özel İndirilenler alanına aktarıldı."
                                )
                            }

                            if (
                                summary
                                    .failed
                                    .isNotEmpty()
                            ) {
                                if (isNotEmpty()) {
                                    append("\n")
                                }

                                append(
                                    summary
                                        .failed
                                        .size
                                )

                                append(
                                    " dosya aktarılamadı."
                                )

                                summary
                                    .failed
                                    .take(3)
                                    .forEach {
                                        append(
                                            "\n• $it"
                                        )
                                    }
                            }
                        }

                    importing =
                        false

                    refreshToken +=
                        1
                }
            }
        }

    LaunchedEffect(
        importRequestToken
    ) {
        if (
            importRequestToken > 0
        ) {
            onImportRequestConsumed()

            picker.launch(
                arrayOf("*/*")
            )
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(12.dp),
        verticalArrangement =
            Arrangement.spacedBy(
                10.dp
            )
    ) {
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
                    Modifier.padding(
                        14.dp
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        6.dp
                    )
            ) {
                Text(
                    "Telefon İndirilenler",
                    color =
                        TerminalText,
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    "Android dosya seçiciden Download / İndirilenler klasöründeki dosyaları seç. " +
                        "Seçilen dosyalar bu AppForge hesabının özel alanına kopyalanır.",
                    color =
                        TerminalMuted,
                    fontSize =
                        11.sp
                )

                Text(
                    "Her hesap için ayrı dosya kutusu kullanılır. Dosya başına sınır: 512 MB.",
                    color =
                        TerminalMuted,
                    fontSize =
                        10.sp
                )
            }
        }

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            verticalAlignment =
                Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.spacedBy(
                    8.dp
                )
        ) {
            Button(
                onClick = {
                    picker.launch(
                        arrayOf("*/*")
                    )
                },
                enabled =
                    !importing
            ) {
                Text(
                    "Telefondan dosya al"
                )
            }

            OutlinedButton(
                onClick = {
                    message =
                        "Terminal komutu: appforge import"
                }
            ) {
                Text(
                    "Komut"
                )
            }

            OutlinedButton(
                onClick = {
                    refreshToken +=
                        1
                }
            ) {
                Text(
                    "Yenile"
                )
            }

            if (importing) {
                CircularProgressIndicator()
            }
        }

        if (
            message.isNotBlank()
        ) {
            Card(
                modifier =
                    Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            TerminalSurfaceRaised
                    )
            ) {
                Text(
                    message,
                    modifier =
                        Modifier.padding(
                            10.dp
                        ),
                    color =
                        TerminalText,
                    fontSize =
                        11.sp
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
        ) {
            TerminalDownloadFileList(
                inbox = inbox,
                refreshToken = refreshToken,
                onEntryClick = {
                    selectedEntry =
                        it
                }
            )
        }

        selectedEntry?.let { entry ->
            TerminalDownloadActionDialog(
                entry = entry,
                onDismiss = {
                    selectedEntry =
                        null
                },
                onRunCommand = { command ->
                    selectedEntry =
                        null

                    onRunCommand(
                        command
                    )
                }
            )
        }
    }
}


@Composable
private fun TerminalDownloadFileList(
    inbox: File,
    refreshToken: Int,
    onEntryClick: (File) -> Unit
) {
    val entries =
        remember(
            inbox.absolutePath,
            refreshToken
        ) {
            inbox
                .listFiles()
                ?.sortedWith(
                    compareBy<File> {
                        !it.isDirectory
                    }.thenBy {
                        it.name.lowercase(
                            Locale.ROOT
                        )
                    }
                )
                .orEmpty()
        }

    if (entries.isEmpty()) {
        Box(
            modifier =
                Modifier.fillMaxSize(),
            contentAlignment =
                Alignment.Center
        ) {
            Text(
                "Henüz içe aktarılmış dosya yok.",
                color =
                    TerminalMuted,
                fontSize =
                    12.sp
            )
        }

        return
    }

    LazyColumn(
        modifier =
            Modifier.fillMaxSize(),
        verticalArrangement =
            Arrangement.spacedBy(
                8.dp
            )
    ) {
        items(
            items =
                entries,
            key = {
                it.absolutePath
            }
        ) { entry ->
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            onEntryClick(
                                entry
                            )
                        },
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            TerminalSurface
                    )
            ) {
                Column(
                    modifier =
                        Modifier.padding(
                            14.dp
                        ),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            4.dp
                        )
                ) {
                    Text(
                        if (entry.isDirectory) {
                            "📁 ${entry.name}"
                        } else {
                            "▤ ${entry.name}"
                        },
                        color =
                            TerminalText,
                        fontWeight =
                            FontWeight.SemiBold
                    )

                    Text(
                        if (entry.isDirectory) {
                            "Klasör • Dokun: işlemler"
                        } else {
                            "${formatDownloadSize(entry.length())} • Dokun: işlemler"
                        },
                        color =
                            TerminalMuted,
                        fontSize =
                            10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalDownloadActionDialog(
    entry: File,
    onDismiss: () -> Unit,
    onRunCommand: (String) -> Unit
) {
    val archive =
        archiveCommand(
            entry
        )

    val runnable =
        runnableCommand(
            entry
        )

    val preview =
        previewCommand(
            entry
        )

    AlertDialog(
        onDismissRequest =
            onDismiss,
        title = {
            Text(
                entry.name
            )
        },
        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(
                        6.dp
                    )
            ) {
                Text(
                    if (entry.isDirectory) {
                        "Bu klasörü Terminalde kullanabilirsin."
                    } else {
                        "Dosya türüne uygun işlemi seç."
                    },
                    fontSize =
                        12.sp
                )

                archive?.let { command ->
                    Button(
                        modifier =
                            Modifier.fillMaxWidth(),
                        onClick = {
                            onRunCommand(
                                command
                            )
                        }
                    ) {
                        Text(
                            "Çıkart"
                        )
                    }
                }

                runnable?.let { command ->
                    Button(
                        modifier =
                            Modifier.fillMaxWidth(),
                        onClick = {
                            onRunCommand(
                                command
                            )
                        }
                    ) {
                        Text(
                            runnableLabel(
                                entry
                            )
                        )
                    }
                }

                preview?.let { command ->
                    OutlinedButton(
                        modifier =
                            Modifier.fillMaxWidth(),
                        onClick = {
                            onRunCommand(
                                command
                            )
                        }
                    ) {
                        Text(
                            "Görüntüle"
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onRunCommand(
                        terminalUseCommand(
                            entry
                        )
                    )
                }
            ) {
                Text(
                    "Terminalde kullan"
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick =
                    onDismiss
            ) {
                Text(
                    "Kapat"
                )
            }
        }
    )
}

private fun archiveCommand(
    file: File
): String? {
    if (
        !file.isFile
    ) {
        return null
    }

    val lower =
        file.name.lowercase(
            Locale.ROOT
        )

    val baseName =
        when {
            lower.endsWith(
                ".tar.gz"
            ) ->
                file.name
                    .dropLast(7)

            lower.endsWith(
                ".tgz"
            ) ->
                file.name
                    .dropLast(4)

            lower.endsWith(
                ".tar"
            ) ->
                file.name
                    .dropLast(4)

            lower.endsWith(
                ".zip"
            ) ->
                file.name
                    .dropLast(4)

            else ->
                return null
        }
            .ifBlank {
                "extracted"
            }

    val output =
        File(
            file.parentFile,
            "$baseName-extracted"
        )

    val sourceQ =
        shellQuote(
            file.absolutePath
        )

    val outputQ =
        shellQuote(
            output.absolutePath
        )

    return when {
        lower.endsWith(
            ".tar.gz"
        ) ||
            lower.endsWith(
                ".tgz"
            ) ->
            "mkdir -p $outputQ && " +
                "tar -xzf $sourceQ -C $outputQ && " +
                "cd $outputQ && ls -la"

        lower.endsWith(
            ".tar"
        ) ->
            "mkdir -p $outputQ && " +
                "tar -xf $sourceQ -C $outputQ && " +
                "cd $outputQ && ls -la"

        lower.endsWith(
            ".zip"
        ) ->
            "mkdir -p $outputQ && " +
                "unzip -q $sourceQ -d $outputQ && " +
                "cd $outputQ && ls -la"

        else ->
            null
    }
}

private fun runnableCommand(
    file: File
): String? {
    if (
        !file.isFile
    ) {
        return null
    }

    val lower =
        file.name.lowercase(
            Locale.ROOT
        )

    val path =
        shellQuote(
            file.absolutePath
        )

    return when {
        lower.endsWith(
            ".sh"
        ) ->
            "sh $path"

        lower.endsWith(
            ".py"
        ) ->
            "python3 $path"

        lower.endsWith(
            ".js"
        ) ||
            lower.endsWith(
                ".mjs"
            ) ||
            lower.endsWith(
                ".cjs"
            ) ->
            "node $path"

        lower.endsWith(
            ".jar"
        ) ->
            "java -jar $path"

        else ->
            null
    }
}

private fun runnableLabel(
    file: File
): String {
    val lower =
        file.name.lowercase(
            Locale.ROOT
        )

    return when {
        lower.endsWith(
            ".py"
        ) ->
            "Python ile çalıştır"

        lower.endsWith(
            ".js"
        ) ||
            lower.endsWith(
                ".mjs"
            ) ||
            lower.endsWith(
                ".cjs"
            ) ->
            "Node ile çalıştır"

        lower.endsWith(
            ".jar"
        ) ->
            "Java ile çalıştır"

        else ->
            "Çalıştır"
    }
}

private fun previewCommand(
    file: File
): String? {
    if (
        !file.isFile
    ) {
        return null
    }

    val lower =
        file.name.lowercase(
            Locale.ROOT
        )

    val supported =
        listOf(
            ".txt",
            ".md",
            ".json",
            ".yaml",
            ".yml",
            ".xml",
            ".kt",
            ".kts",
            ".java",
            ".py",
            ".js",
            ".mjs",
            ".cjs",
            ".ts",
            ".tsx",
            ".jsx",
            ".html",
            ".css",
            ".sh",
            ".gradle",
            ".properties",
            ".toml",
            ".ini",
            ".conf",
            ".log"
        ).any {
            lower.endsWith(
                it
            )
        }

    if (!supported) {
        return null
    }

    return "sed -n '1,220p' ${
        shellQuote(
            file.absolutePath
        )
    }"
}

private fun terminalUseCommand(
    entry: File
): String {
    if (entry.isDirectory) {
        return "cd ${
            shellQuote(
                entry.absolutePath
            )
        } && pwd && ls -la"
    }

    val parent =
        entry.parentFile
            ?.absolutePath
            ?: "."

    return "cd ${
        shellQuote(
            parent
        )
    } && pwd && ls -lh ${
        shellQuote(
            entry.name
        )
    }"
}

private fun shellQuote(
    value: String
): String =
    "'" +
        value.replace(
            "'",
            "'\"'\"'"
        ) +
        "'"

private fun formatDownloadSize(
    bytes: Long
): String =
    when {
        bytes >=
            1024L * 1024L * 1024L ->
            String.format(
                Locale.ROOT,
                "%.2f GB",
                bytes.toDouble() /
                    (
                        1024.0 *
                            1024.0 *
                            1024.0
                    )
            )

        bytes >=
            1024L * 1024L ->
            String.format(
                Locale.ROOT,
                "%.2f MB",
                bytes.toDouble() /
                    (
                        1024.0 *
                            1024.0
                    )
            )

        bytes >=
            1024L ->
            String.format(
                Locale.ROOT,
                "%.1f KB",
                bytes.toDouble() /
                    1024.0
            )

        else ->
            "$bytes B"
    }

private object TerminalDownloadInbox {
    fun resolve(
        context: Context,
        accountEmail: String
    ): File {
        val normalized =
            accountEmail
                .trim()
                .lowercase(
                    Locale.ROOT
                )
                .ifBlank {
                    "anonymous"
                }

        val digest =
            MessageDigest
                .getInstance(
                    "SHA-256"
                )
                .digest(
                    normalized
                        .toByteArray(
                            Charsets.UTF_8
                        )
                )

        val accountKey =
            digest
                .joinToString(
                    separator = ""
                ) {
                    "%02x".format(
                        it.toInt() and
                            0xff
                    )
                }
                .take(24)

        return File(
            context.filesDir,
            "terminal-downloads/$accountKey"
        ).apply {
            mkdirs()
        }
    }

    fun importUris(
        context: Context,
        accountEmail: String,
        uris: List<Uri>
    ): DownloadImportSummary {
        val root =
            resolve(
                context = context,
                accountEmail =
                    accountEmail
            )

        val imported =
            mutableListOf<File>()

        val failed =
            mutableListOf<String>()

        uris
            .distinct()
            .forEachIndexed {
                index,
                uri ->

                val metadata =
                    runCatching {
                        readMetadata(
                            context,
                            uri,
                            index
                        )
                    }.getOrElse {
                        DownloadMetadata(
                            name =
                                "download-$index",
                            size =
                                null
                        )
                    }

                runCatching {
                    if (
                        metadata.size != null &&
                        metadata.size >
                            MAX_IMPORTED_DOWNLOAD_BYTES
                    ) {
                        error(
                            "Dosya 512 MB sınırını aşıyor."
                        )
                    }

                    val safeName =
                        sanitizeFileName(
                            metadata.name,
                            index
                        )

                    val destination =
                        uniqueTarget(
                            root,
                            safeName
                        )

                    val input =
                        context
                            .contentResolver
                            .openInputStream(
                                uri
                            )
                            ?: error(
                                "Dosya açılamadı."
                            )

                    try {
                        input.use { source ->
                            destination
                                .outputStream()
                                .buffered()
                                .use { output ->
                                    val buffer =
                                        ByteArray(
                                            DEFAULT_BUFFER_SIZE
                                        )

                                    var total =
                                        0L

                                    while (true) {
                                        val count =
                                            source.read(
                                                buffer
                                            )

                                        if (
                                            count < 0
                                        ) {
                                            break
                                        }

                                        total +=
                                            count

                                        if (
                                            total >
                                            MAX_IMPORTED_DOWNLOAD_BYTES
                                        ) {
                                            error(
                                                "Dosya 512 MB sınırını aşıyor."
                                            )
                                        }

                                        output.write(
                                            buffer,
                                            0,
                                            count
                                        )
                                    }
                                }
                        }
                    } catch (
                        throwable: Throwable
                    ) {
                        destination.delete()

                        throw throwable
                    }

                    destination
                }.onSuccess {
                    imported +=
                        it
                }.onFailure {
                    failed +=
                        "${metadata.name}: ${
                            it.message
                                ?: "Aktarım hatası"
                        }"
                }
            }

        return DownloadImportSummary(
            imported = imported,
            failed = failed
        )
    }

    private fun readMetadata(
        context: Context,
        uri: Uri,
        index: Int
    ): DownloadMetadata {
        var name =
            uri
                .lastPathSegment
                ?.substringAfterLast(
                    '/'
                )
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: "download-$index"

        var size: Long? =
            null

        context
            .contentResolver
            .query(
                uri,
                arrayOf(
                    OpenableColumns
                        .DISPLAY_NAME,
                    OpenableColumns
                        .SIZE
                ),
                null,
                null,
                null
            )
            ?.use { cursor ->
                if (
                    cursor.moveToFirst()
                ) {
                    val nameIndex =
                        cursor.getColumnIndex(
                            OpenableColumns
                                .DISPLAY_NAME
                        )

                    if (
                        nameIndex >= 0
                    ) {
                        cursor
                            .getString(
                                nameIndex
                            )
                            ?.takeIf {
                                it.isNotBlank()
                            }
                            ?.let {
                                name = it
                            }
                    }

                    val sizeIndex =
                        cursor.getColumnIndex(
                            OpenableColumns
                                .SIZE
                        )

                    if (
                        sizeIndex >= 0 &&
                        !cursor.isNull(
                            sizeIndex
                        )
                    ) {
                        size =
                            cursor.getLong(
                                sizeIndex
                            )
                    }
                }
            }

        return DownloadMetadata(
            name = name,
            size = size
        )
    }

    private fun sanitizeFileName(
        original: String,
        index: Int
    ): String {
        val clean =
            original
                .map { char ->
                    when {
                        char.isLetterOrDigit() ->
                            char

                        char in
                            "._- ()[]" ->
                            char

                        else ->
                            '_'
                    }
                }
                .joinToString(
                    separator = ""
                )
                .trim()
                .take(180)

        return clean
            .ifBlank {
                "download-$index"
            }
    }

    private fun uniqueTarget(
        directory: File,
        fileName: String
    ): File {
        val initial =
            File(
                directory,
                fileName
            )

        if (
            !initial.exists()
        ) {
            return initial
        }

        val dot =
            fileName.lastIndexOf('.')

        val base =
            if (
                dot > 0
            ) {
                fileName.substring(
                    0,
                    dot
                )
            } else {
                fileName
            }

        val extension =
            if (
                dot > 0
            ) {
                fileName.substring(
                    dot
                )
            } else {
                ""
            }

        var counter =
            1

        while (
            counter <
            10_000
        ) {
            val candidate =
                File(
                    directory,
                    "$base ($counter)$extension"
                )

            if (
                !candidate.exists()
            ) {
                return candidate
            }

            counter +=
                1
        }

        error(
            "Aynı isimli çok fazla dosya var."
        )
    }
}
