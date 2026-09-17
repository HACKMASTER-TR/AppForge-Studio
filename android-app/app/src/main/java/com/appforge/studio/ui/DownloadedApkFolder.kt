@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.appforge.studio.ui

import android.content.ClipData
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appforge.studio.build.BuildApiClient
import com.appforge.studio.io.ProjectLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale


private const val PUBLIC_FOLDER =
    "AppForgeStudio"

private const val LEGACY_PUBLIC_FOLDER =
    "AppForge Studio"


internal data class LocalBuildArtifact(
    val name: String,
    val uri: Uri,
    val modifiedAtMillis: Long,
    val sizeBytes: Long,
    val type: BuildArtifactType
)


private fun typeFromLocalFile(
    name: String,
    mimeType: String?
): BuildArtifactType? {

    val lower =
        name.lowercase(
            Locale.ROOT
        )

    val mime =
        mimeType
            .orEmpty()
            .lowercase(
                Locale.ROOT
            )

    return when {

        mime ==
            "application/vnd.android.package-archive" ||
            lower.endsWith(
                ".apk"
            ) ->
            BuildArtifactType.ANDROID_APK

        mime ==
            "application/vnd.microsoft.portable-executable" ||
            mime ==
            "application/x-msdownload" ||
            lower.endsWith(
                ".exe"
            ) ->
            BuildArtifactType.WINDOWS_PORTABLE_EXE

        lower.endsWith(
            ".aab"
        ) ->
            BuildArtifactType.ANDROID_AAB

        else ->
            null
    }
}


internal fun loadLocalBuildArtifacts(
    context: Context
): List<LocalBuildArtifact> {

    val output =
        mutableListOf<LocalBuildArtifact>()

    val folders =
        listOf(
            PUBLIC_FOLDER,
            LEGACY_PUBLIC_FOLDER
        )

    if (
        Build.VERSION.SDK_INT >=
        Build.VERSION_CODES.Q
    ) {

        val collection =
            MediaStore.Downloads
                .EXTERNAL_CONTENT_URI

        val projection =
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.MIME_TYPE
            )

        val prefixes =
            folders.map {
                "${Environment.DIRECTORY_DOWNLOADS}/$it/"
            }

        val pathSelection =
            prefixes.joinToString(
                separator = " OR "
            ) {
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
            }

        val selection =
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.R
            ) {
                "($pathSelection) AND " +
                    "${MediaStore.MediaColumns.IS_TRASHED}=0"
            } else {
                "($pathSelection)"
            }

        context.contentResolver
            .query(
                collection,
                projection,
                selection,
                prefixes
                    .map {
                        "$it%"
                    }
                    .toTypedArray(),
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )
            ?.use {
                cursor ->

                val idIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.MediaColumns._ID
                    )

                val nameIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.MediaColumns.DISPLAY_NAME
                    )

                val dateIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.MediaColumns.DATE_MODIFIED
                    )

                val sizeIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.MediaColumns.SIZE
                    )

                val mimeIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.MediaColumns.MIME_TYPE
                    )

                while (
                    cursor.moveToNext()
                ) {

                    val name =
                        cursor
                            .getString(
                                nameIndex
                            )
                            .orEmpty()

                    val mime =
                        cursor.getString(
                            mimeIndex
                        )

                    val type =
                        typeFromLocalFile(
                            name,
                            mime
                        )
                            ?: continue

                    output +=
                        LocalBuildArtifact(
                            name =
                                name,
                            uri =
                                ContentUris
                                    .withAppendedId(
                                        collection,
                                        cursor.getLong(
                                            idIndex
                                        )
                                    ),
                            modifiedAtMillis =
                                cursor.getLong(
                                    dateIndex
                                ) * 1000L,
                            sizeBytes =
                                cursor.getLong(
                                    sizeIndex
                                ),
                            type =
                                type
                        )
                }
            }

        return output
            .distinctBy {
                it.uri.toString()
            }
    }

    @Suppress(
        "DEPRECATION"
    )
    val downloads =
        Environment
            .getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )

    folders.forEach {
        folder ->

        File(
            downloads,
            folder
        )
            .listFiles()
            .orEmpty()
            .filter {
                it.isFile
            }
            .forEach {
                file ->

                val type =
                    typeFromLocalFile(
                        file.name,
                        null
                    )
                        ?: return@forEach

                output +=
                    LocalBuildArtifact(
                        name =
                            file.name,
                        uri =
                            Uri.fromFile(
                                file
                            ),
                        modifiedAtMillis =
                            file.lastModified(),
                        sizeBytes =
                            file.length(),
                        type =
                            type
                    )
            }
    }

    return output
}


private fun sizeText(
    value: Long
): String =
    when {

        value <= 0L ->
            "—"

        value <
            1024L * 1024L ->
            "${
                (
                    (
                        value +
                            1023L
                    ) /
                        1024L
                )
                    .coerceAtLeast(
                        1L
                    )
            } KB"

        else ->
            String.format(
                Locale.ROOT,
                "%.1f MB",
                value /
                    1048576.0
            )
    }


private fun shareArtifact(
    context: Context,
    artifact: LocalBuildArtifact
) {

    runCatching {

        val intent =
            Intent(
                Intent.ACTION_SEND
            ).apply {

                type =
                    artifact.type
                        .mimeType

                putExtra(
                    Intent.EXTRA_STREAM,
                    artifact.uri
                )

                clipData =
                    ClipData.newRawUri(
                        artifact.name,
                        artifact.uri
                    )

                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

        val chooser =
            Intent.createChooser(
                intent,
                "Derlemeyi paylaş"
            ).apply {

                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                if (
                    context !is
                    android.app.Activity
                ) {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                    )
                }
            }

        context.startActivity(
            chooser
        )

    }.onFailure {

        Toast.makeText(
            context,
            "Derleme paylaşılamadı: ${
                it.message
                    ?: it.javaClass.simpleName
            }",
            Toast.LENGTH_LONG
        ).show()
    }
}


private fun moveArtifactToTrash(
    context: Context,
    artifact: LocalBuildArtifact
): Boolean =
    runCatching {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.R ||
            artifact.uri.scheme !=
            "content"
        ) {
            return@runCatching false
        }

        val values =
            ContentValues().apply {
                put(
                    MediaStore.MediaColumns.IS_TRASHED,
                    1
                )
            }

        context.contentResolver
            .update(
                artifact.uri,
                values,
                null,
                null
            ) > 0

    }.getOrDefault(
        false
    )


@androidx.annotation.RequiresApi(
    Build.VERSION_CODES.Q
)
private fun downloadArtifact(
    context: Context,
    buildServiceUrl: String,
    buildApiKey: String,
    record: BuildArtifactRecord
): LocalBuildArtifact {

    require(
        Build.VERSION.SDK_INT >=
        Build.VERSION_CODES.Q
    ) {
        "AppForgeStudio klasörüne otomatik kayıt Android 10+ gerektirir."
    }

    val expectedName =
        buildArtifactFileName(
            record
        )

    loadLocalBuildArtifacts(
        context
    )
        .firstOrNull {
            it.name.equals(
                expectedName,
                ignoreCase = true
            )
        }
        ?.let {
            return it
        }

    val ticket =
        BuildApiClient(
            context,
            buildServiceUrl,
            buildApiKey
        )
            .createDownloadTicket(
                record.build.id,
                record.type.ticketKind
            )

    require(
        ticket.url.startsWith(
            "https://",
            ignoreCase = true
        )
    ) {
        "İndirme adresi HTTPS değil."
    }

    val resolver =
        context.contentResolver

    val values =
        ContentValues().apply {

            put(
                MediaStore.MediaColumns.DISPLAY_NAME,
                expectedName
            )

            put(
                MediaStore.MediaColumns.MIME_TYPE,
                record.type.mimeType
            )

            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/$PUBLIC_FOLDER"
            )

            put(
                MediaStore.MediaColumns.IS_PENDING,
                1
            )
        }

    val uri =
        resolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            values
        )
            ?: error(
                "AppForgeStudio dosyası oluşturulamadı."
            )

    val connection =
        java.net.URL(
            ticket.url
        )
            .openConnection()
            as java.net.HttpURLConnection

    try {

        connection.instanceFollowRedirects =
            true

        connection.connectTimeout =
            20_000

        connection.readTimeout =
            900_000

        connection.requestMethod =
            "GET"

        connection.setRequestProperty(
            "Accept",
            "application/octet-stream,*/*"
        )

        connection.setRequestProperty(
            "User-Agent",
            "AppForge-Studio-Android"
        )

        connection.connect()

        val code =
            connection.responseCode

        require(
            code in
                200..299
        ) {
            "Artifact HTTP $code"
        }

        require(
            connection.url
                .protocol
                .equals(
                    "https",
                    ignoreCase = true
                )
        ) {
            "Artifact yönlendirmesi HTTPS değil."
        }

        resolver
            .openOutputStream(
                uri,
                "w"
            )
            ?.buffered(
                1024 * 1024
            )
            ?.use {
                output ->

                connection
                    .inputStream
                    .buffered(
                        1024 * 1024
                    )
                    .use {
                        input ->

                        input.copyTo(
                            output,
                            1024 * 1024
                        )

                        output.flush()
                    }
            }
            ?: error(
                "Hedef dosya açılamadı."
            )

        resolver.update(
            uri,
            ContentValues().apply {
                put(
                    MediaStore.MediaColumns.IS_PENDING,
                    0
                )
            },
            null,
            null
        )

        val saved =
            loadLocalBuildArtifacts(
                context
            )
                .firstOrNull {
                    it.uri ==
                    uri
                }
                ?: LocalBuildArtifact(
                    name =
                        expectedName,
                    uri =
                        uri,
                    modifiedAtMillis =
                        System.currentTimeMillis(),
                    sizeBytes =
                        0L,
                    type =
                        record.type
                )

        return saved

    } catch (
        t: Throwable
    ) {

        runCatching {
            resolver.delete(
                uri,
                null,
                null
            )
        }

        throw t

    } finally {

        connection.disconnect()
    }
}


@Composable
internal fun DownloadedApkFolderScreen(
    onBack: () -> Unit,
    onInstall: (Uri, String) -> Unit,
    buildServiceUrl: String,
    buildApiKey: String
) {

    val context =
        LocalContext.current

    val scope =
        rememberCoroutineScope()

    val configuration =
        LocalConfiguration.current

    val compact =
        configuration.screenWidthDp <
            390

    var builds by
        remember {
            mutableStateOf(
                emptyList<BuildArtifactRecord>()
            )
        }

    var localFiles by
        remember {
            mutableStateOf(
                emptyList<LocalBuildArtifact>()
            )
        }

    var loading by
        remember {
            mutableStateOf(
                true
            )
        }

    var error by
        remember {
            mutableStateOf(
                ""
            )
        }

    var token by
        remember {
            mutableIntStateOf(
                0
            )
        }

    var search by
        rememberSaveable {
            mutableStateOf(
                ""
            )
        }

    var newest by
        rememberSaveable {
            mutableStateOf(
                true
            )
        }

    var saving by
        remember {
            mutableStateOf(
                emptySet<String>()
            )
        }

    LaunchedEffect(
        token
    ) {

        loading =
            true

        error =
            ""

        runCatching {

            withContext(
                Dispatchers.IO
            ) {

                val history =
                    ProjectLibrary
                        .loadBuilds(
                            context
                        )

                val artifacts =
                    history
                        .flatMap {
                            it.availableArtifacts()
                        }

                artifacts to
                    loadLocalBuildArtifacts(
                        context
                    )
            }

        }.onSuccess {

            builds =
                it.first

            localFiles =
                it.second

        }.onFailure {

            error =
                it.message
                    ?: "Derlemeler okunamadı."
        }

        loading =
            false
    }

    val localByName =
        remember(
            localFiles
        ) {
            localFiles
                .associateBy {
                    it.name.lowercase(
                        Locale.ROOT
                    )
                }
        }

    val query =
        search
            .trim()
            .lowercase(
                Locale.ROOT
            )

    val shown =
        builds
            .filter {
                record ->

                query.isBlank() ||
                    record.build.projectName
                        .lowercase(
                            Locale.ROOT
                        )
                        .contains(
                            query
                        ) ||
                    record.type.label
                        .lowercase(
                            Locale.ROOT
                        )
                        .contains(
                            query
                        )
            }
            .let {

                if (newest) {
                    it.sortedByDescending {
                        record ->
                        record.build.createdAt
                    }
                } else {
                    it.sortedBy {
                        record ->
                        record.build.createdAt
                    }
                }
            }

    val expectedNames =
        builds
            .map {
                buildArtifactFileName(
                    it
                )
                    .lowercase(
                        Locale.ROOT
                    )
            }
            .toSet()

    val legacyOrUnlinked =
        localFiles
            .filter {
                it.name
                    .lowercase(
                        Locale.ROOT
                    ) !in
                    expectedNames
            }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {

                        Text(
                            "Başarılı Derlemeler",
                            fontWeight =
                                FontWeight.Black
                        )

                        Text(
                            "${builds.size} başarılı artifact",
                            fontSize =
                                11.sp
                        )
                    }
                },
                navigationIcon = {
                    TextButton(
                        onClick =
                            onBack
                    ) {
                        Text(
                            "← Geri"
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            token++
                        }
                    ) {
                        Text(
                            "Yenile"
                        )
                    }
                }
            )
        }
    ) {
        padding ->

        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        padding
                    ),
            contentPadding =
                PaddingValues(
                    if (compact) {
                        12.dp
                    } else {
                        18.dp
                    }
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    10.dp
                )
        ) {

            item {
                OutlinedTextField(
                    value =
                        search,
                    onValueChange = {
                        search = it
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                    singleLine =
                        true,
                    label = {
                        Text(
                            "Derleme ara"
                        )
                    }
                )
            }

            item {
                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {

                    if (newest) {
                        Button(
                            onClick = {
                                newest =
                                    true
                            },
                            modifier =
                                Modifier.weight(
                                    1f
                                )
                        ) {
                            Text(
                                "Yeni → Eski"
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                newest =
                                    true
                            },
                            modifier =
                                Modifier.weight(
                                    1f
                                )
                        ) {
                            Text(
                                "Yeni → Eski"
                            )
                        }
                    }

                    if (!newest) {
                        Button(
                            onClick = {
                                newest =
                                    false
                            },
                            modifier =
                                Modifier.weight(
                                    1f
                                )
                        ) {
                            Text(
                                "Eski → Yeni"
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                newest =
                                    false
                            },
                            modifier =
                                Modifier.weight(
                                    1f
                                )
                        ) {
                            Text(
                                "Eski → Yeni"
                            )
                        }
                    }
                }
            }

            when {

                loading ->
                    item {
                        Text(
                            "Başarılı derlemeler okunuyor..."
                        )
                    }

                error.isNotBlank() ->
                    item {
                        Text(
                            error,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .error
                        )
                    }

                shown.isEmpty() ->
                    item {
                        Text(
                            "Henüz başarılı derleme yok."
                        )
                    }

                else ->
                    items(
                        shown,
                        key = {
                            it.id
                        }
                    ) {
                        record ->

                        val expectedName =
                            buildArtifactFileName(
                                record
                            )

                        val local =
                            localByName[
                                expectedName.lowercase(
                                    Locale.ROOT
                                )
                            ]

                        val date =
                            DateFormat
                                .getDateTimeInstance(
                                    DateFormat.SHORT,
                                    DateFormat.SHORT
                                )
                                .format(
                                    Date(
                                        record.build.createdAt
                                    )
                                )

                        val isSaving =
                            record.id in
                                saving

                        Card(
                            modifier =
                                Modifier.fillMaxWidth(),
                            shape =
                                RoundedCornerShape(
                                    18.dp
                                )
                        ) {

                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            14.dp
                                        ),
                                verticalArrangement =
                                    Arrangement.spacedBy(
                                        7.dp
                                    )
                            ) {

                                Text(
                                    record.type.label,
                                    fontWeight =
                                        FontWeight.Black,
                                    color =
                                        MaterialTheme
                                            .colorScheme
                                            .primary,
                                    fontSize =
                                        12.sp
                                )

                                Text(
                                    record.build.projectName,
                                    fontWeight =
                                        FontWeight.Bold,
                                    maxLines =
                                        1,
                                    overflow =
                                        TextOverflow.Ellipsis
                                )

                                Text(
                                    expectedName,
                                    fontSize =
                                        11.sp,
                                    maxLines =
                                        1,
                                    overflow =
                                        TextOverflow.Ellipsis
                                )

                                Text(
                                    "$date • ${
                                        if (local != null) {
                                            "AppForgeStudio • ${
                                                sizeText(
                                                    local.sizeBytes
                                                )
                                            }"
                                        } else {
                                            "Başarılı • Cihaza kaydedilmedi"
                                        }
                                    }",
                                    fontSize =
                                        11.sp
                                )

                                Row(
                                    modifier =
                                        Modifier.fillMaxWidth(),
                                    horizontalArrangement =
                                        Arrangement.spacedBy(
                                            4.dp
                                        )
                                ) {

                                    if (local == null) {

                                        TextButton(
                                            enabled =
                                                !isSaving,
                                            onClick = {

                                                if (
                                                    Build.VERSION.SDK_INT <
                                                    Build.VERSION_CODES.Q
                                                ) {

                                                    Toast.makeText(
                                                        context,
                                                        "AppForgeStudio otomatik kaydı Android 10+ gerektirir.",
                                                        Toast.LENGTH_LONG
                                                    ).show()

                                                    return@TextButton
                                                }

                                                scope.launch {

                                                    saving =
                                                        saving +
                                                            record.id

                                                    runCatching {

                                                        withContext(
                                                            Dispatchers.IO
                                                        ) {

                                                            downloadArtifact(
                                                                context =
                                                                    context,
                                                                buildServiceUrl =
                                                                    buildServiceUrl,
                                                                buildApiKey =
                                                                    buildApiKey,
                                                                record =
                                                                    record
                                                            )
                                                        }

                                                    }.onSuccess {

                                                        Toast.makeText(
                                                            context,
                                                            "✅ ${record.type.label} AppForgeStudio klasörüne kaydedildi.",
                                                            Toast.LENGTH_SHORT
                                                        ).show()

                                                        token++

                                                    }.onFailure {

                                                        Toast.makeText(
                                                            context,
                                                            "Kaydetme hatası: ${
                                                                it.message
                                                                    ?: it.javaClass.simpleName
                                                            }",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                    }

                                                    saving =
                                                        saving -
                                                            record.id
                                                }
                                            }
                                        ) {
                                            Text(
                                                if (isSaving) {
                                                    "Kaydediliyor..."
                                                } else {
                                                    "Kaydet"
                                                }
                                            )
                                        }

                                    } else {

                                        if (
                                            record.type ==
                                            BuildArtifactType.ANDROID_APK
                                        ) {
                                            TextButton(
                                                onClick = {
                                                    onInstall(
                                                        local.uri,
                                                        local.name
                                                    )
                                                }
                                            ) {
                                                Text(
                                                    "Kur"
                                                )
                                            }
                                        }

                                        TextButton(
                                            onClick = {
                                                shareArtifact(
                                                    context,
                                                    local
                                                )
                                            }
                                        ) {
                                            Text(
                                                "Paylaş"
                                            )
                                        }

                                        TextButton(
                                            onClick = {

                                                scope.launch {

                                                    val moved =
                                                        withContext(
                                                            Dispatchers.IO
                                                        ) {
                                                            moveArtifactToTrash(
                                                                context,
                                                                local
                                                            )
                                                        }

                                                    if (moved) {

                                                        Toast.makeText(
                                                            context,
                                                            "${record.type.label} çöpe taşındı.",
                                                            Toast.LENGTH_SHORT
                                                        ).show()

                                                        token++

                                                    } else {

                                                        Toast.makeText(
                                                            context,
                                                            "Sistem çöp kutusuna taşınamadı.",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                    }
                                                }
                                            }
                                        ) {
                                            Text(
                                                "Çöpe taşı"
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
            }

            if (
                legacyOrUnlinked
                    .isNotEmpty()
            ) {

                item {

                    Spacer(
                        Modifier.height(
                            8.dp
                        )
                    )

                    Text(
                        "Cihazdaki önceki derlemeler",
                        fontWeight =
                            FontWeight.Black
                    )
                }

                items(
                    legacyOrUnlinked,
                    key = {
                        it.uri.toString()
                    }
                ) {
                    artifact ->

                    Card(
                        modifier =
                            Modifier.fillMaxWidth(),
                        shape =
                            RoundedCornerShape(
                                18.dp
                            )
                    ) {

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        14.dp
                                    ),
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            Column(
                                modifier =
                                    Modifier.weight(
                                        1f
                                    )
                            ) {

                                Text(
                                    artifact.type.label,
                                    fontWeight =
                                        FontWeight.Black
                                )

                                Text(
                                    artifact.name,
                                    fontSize =
                                        12.sp,
                                    maxLines =
                                        1,
                                    overflow =
                                        TextOverflow.Ellipsis
                                )

                                Text(
                                    sizeText(
                                        artifact.sizeBytes
                                    ),
                                    fontSize =
                                        11.sp
                                )
                            }

                            if (
                                artifact.type ==
                                BuildArtifactType.ANDROID_APK
                            ) {

                                TextButton(
                                    onClick = {
                                        onInstall(
                                            artifact.uri,
                                            artifact.name
                                        )
                                    }
                                ) {
                                    Text(
                                        "Kur"
                                    )
                                }
                            }

                            TextButton(
                                onClick = {
                                    shareArtifact(
                                        context,
                                        artifact
                                    )
                                }
                            ) {
                                Text(
                                    "Paylaş"
                                )
                            }

                            TextButton(
                                onClick = {

                                    scope.launch {

                                        val moved =
                                            withContext(
                                                Dispatchers.IO
                                            ) {
                                                moveArtifactToTrash(
                                                    context,
                                                    artifact
                                                )
                                            }

                                        if (moved) {
                                            token++
                                        }
                                    }
                                }
                            ) {
                                Text(
                                    "Çöpe taşı"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
