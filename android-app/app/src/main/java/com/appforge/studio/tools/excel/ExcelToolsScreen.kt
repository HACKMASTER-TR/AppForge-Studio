@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.appforge.studio.tools.excel

import com.appforge.studio.tools.OtherAppsUsageGate

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ExcelBg = Color(0xFF060711)
private val ExcelCard = Color(0xFF101426)
private val ExcelText = Color(0xFFF4F7FF)
private val ExcelMuted = Color(0xFFA9B1C7)
private val ExcelAccent = Color(0xFF63D9FF)

@Composable
fun ExcelToolsScreen(
    onBack: () -> Unit,
    proUnlocked: Boolean,
    onOpenPro: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }
    var statusTitle by remember { mutableStateOf("Hazır") }
    var statusText by remember {
        mutableStateOf(
            "Dönüştürmek istediğin XLSX, XLSM veya CSV dosyasını seç.\n" +
                "İşlem tamamen cihazında gerçekleştirilir."
        )
    }

    var lastOutputUri by
        remember {
            mutableStateOf<Uri?>(
                null
            )
        }

    var lastOutputName by
        remember {
            mutableStateOf(
                ""
            )
        }

    var history by
        remember {
            mutableStateOf(
                loadExcelHistory(
                    context
                )
            )
        }

    val picker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult

            if (
                !OtherAppsUsageGate.consume(
                    context,
                    proUnlocked
                )
            ) {
                statusTitle =
                    "PRO gerekli"

                statusText =
                    "Excel Tools ve VideoForge için toplam 5 ücretsiz kullanım hakkın bitti."

                onOpenPro()

                return@rememberLauncherForActivityResult
            }

            scope.launch {
                busy = true
                statusTitle = "Dosya hazırlanıyor"
                statusText = "Dosya kontrol ediliyor ve düzenlenebilir kopya hazırlanıyor…"

                try {
                    val output =
                        withContext(
                            Dispatchers.IO
                        ) {
                            processExcelFile(
                                context,
                                uri
                            )
                        }

                    lastOutputUri =
                        output.uri

                    lastOutputName =
                        output.fileName

                    history =
                        withContext(
                            Dispatchers.IO
                        ) {
                            loadExcelHistory(
                                context
                            )
                        }

                    statusTitle =
                        "Dosya hazır"

                    statusText =
                        output.message
                } catch (t: Throwable) {
                    statusTitle = "Dosya işlenemedi"
                    statusText =
                        t.message
                            ?: t.toString()
                } finally {
                    busy = false
                }
            }
        }

    Scaffold(
        containerColor = ExcelBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "AppForge Excel Tools",
                            color = ExcelText,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "Excel dosyalarını düzenleme ve dönüştürme araçları",
                            color = ExcelMuted,
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    TextButton(
                        onClick = onBack
                    ) {
                        Text("← Geri")
                    }
                },
                colors =
                    TopAppBarDefaults
                        .topAppBarColors(
                            containerColor = ExcelBg
                        )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            contentPadding =
                PaddingValues(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .widthIn(max = 850.dp),
                    shape =
                        RoundedCornerShape(26.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = ExcelCard
                        )
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(22.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "▦  Excel dosyanı düzenlenebilir bir kopyaya dönüştür",
                            color = ExcelText,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp
                        )

                        Text(
                            "XLSX ve XLSM dosyalarındaki çalışma kitabı ve sayfa korumalarını kaldırarak düzenlenebilir yeni bir kopya oluşturur. CSV dosyalarında ise orijinal dosyaya dokunmadan yeni bir kopya oluşturulur.",
                            color = ExcelMuted,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )

                        Text(
                            "✓ Formüller ve biçimlendirme korunur\n" +
                                "✓ XLSM dosyalarında makrolar korunur\n" +
                                "✓ XLSX, XLSM ve CSV desteği\n" +
                                "✓ Maksimum dosya boyutu: 80 MB\n" +
                                if (proUnlocked) {
                                    "✓ PRO: Sınırsız kullanım"
                                } else {
                                    "✓ Ücretsiz ortak hak: ${OtherAppsUsageGate.remaining(context)}/5"
                                },
                            color = ExcelText,
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )

                        Button(
                            onClick = {
                                if (
                                    proUnlocked ||
                                    OtherAppsUsageGate.canUse(
                                        context,
                                        false
                                    )
                                ) {
                                    picker.launch(
                                        arrayOf("*/*")
                                    )
                                } else {
                                    statusTitle =
                                        "PRO gerekli"

                                    statusText =
                                        "5 ücretsiz ortak kullanım hakkın bitti. Devam etmek için PRO gerekli."

                                    onOpenPro()
                                }
                            },
                            enabled = !busy,
                            modifier =
                                Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (busy) {
                                    "İŞLENİYOR…"
                                } else {
                                    "DOSYA SEÇ"
                                }
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .widthIn(max = 850.dp),
                    shape =
                        RoundedCornerShape(22.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = ExcelCard
                        )
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {
                        if (busy) {
                            CircularProgressIndicator()
                        }

                        Text(
                            statusTitle,
                            color = ExcelAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )

                        Text(
                            statusText,
                            color = ExcelMuted,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )

                        if (
                            lastOutputUri != null &&
                            lastOutputName.isNotBlank()
                        ) {
                            Button(
                                onClick = {
                                    val outputUri =
                                        lastOutputUri

                                    if (
                                        outputUri != null &&
                                        !openExcelOutput(
                                            context,
                                            outputUri,
                                            lastOutputName
                                        )
                                    ) {
                                        statusTitle =
                                            "Dosya açılamadı"

                                        statusText =
                                            "Bu dosya türünü açabilecek bir uygulama bulunamadı."
                                    }
                                },
                                modifier =
                                    Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "📂 DOSYAYI AÇ"
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "İşlem geçmişi",
                    color = ExcelText,
                    fontWeight = FontWeight.Black,
                    fontSize = 19.sp
                )
            }

            if (
                history.isEmpty()
            ) {
                item {
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .widthIn(
                                    max = 850.dp
                                ),
                        shape =
                            RoundedCornerShape(
                                22.dp
                            ),
                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    ExcelCard
                            )
                    ) {
                        Text(
                            "Henüz işlem geçmişi yok.",
                            modifier =
                                Modifier.padding(
                                    18.dp
                                ),
                            color =
                                ExcelMuted,
                            fontSize =
                                13.sp
                        )
                    }
                }
            } else {
                items(
                    items = history,
                    key = {
                        it.uri.toString()
                    }
                ) { output ->
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .widthIn(
                                    max = 850.dp
                                ),
                        shape =
                            RoundedCornerShape(
                                22.dp
                            ),
                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    ExcelCard
                            )
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        18.dp
                                    ),
                            verticalArrangement =
                                Arrangement.spacedBy(
                                    9.dp
                                )
                        ) {
                            Text(
                                output.name,
                                color =
                                    ExcelText,
                                fontWeight =
                                    FontWeight.Bold,
                                fontSize =
                                    14.sp
                            )

                            Text(
                                formatExcelHistoryDate(
                                    output.createdAtSeconds
                                ),
                                color =
                                    ExcelMuted,
                                fontSize =
                                    11.sp
                            )

                            Row(
                                modifier =
                                    Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement.spacedBy(
                                        8.dp
                                    )
                            ) {
                                Button(
                                    onClick = {
                                        if (
                                            !openExcelOutput(
                                                context,
                                                output.uri,
                                                output.name
                                            )
                                        ) {
                                            statusTitle =
                                                "Dosya açılamadı"

                                            statusText =
                                                "Bu dosya türünü açabilecek bir uygulama bulunamadı."
                                        }
                                    },
                                    modifier =
                                        Modifier.weight(
                                            1f
                                        )
                                ) {
                                    Text(
                                        "📂 AÇ"
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            val deleted =
                                                withContext(
                                                    Dispatchers.IO
                                                ) {
                                                    deleteExcelOutput(
                                                        context,
                                                        output.uri
                                                    )
                                                }

                                            history =
                                                withContext(
                                                    Dispatchers.IO
                                                ) {
                                                    loadExcelHistory(
                                                        context
                                                    )
                                                }

                                            if (
                                                deleted
                                            ) {
                                                if (
                                                    lastOutputUri ==
                                                    output.uri
                                                ) {
                                                    lastOutputUri =
                                                        null

                                                    lastOutputName =
                                                        ""
                                                }

                                                statusTitle =
                                                    "Çöp kutusu"

                                                statusText =
                                                    "${output.name} silindi."
                                            } else {
                                                statusTitle =
                                                    "Silinemedi"

                                                statusText =
                                                    "Dosya silinemedi veya daha önce kaldırılmış."
                                            }
                                        }
                                    },
                                    modifier =
                                        Modifier.weight(
                                            1f
                                        )
                                ) {
                                    Text(
                                        "🗑 SİL"
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val currentHistory =
                                    history

                                val deletedCount =
                                    withContext(
                                        Dispatchers.IO
                                    ) {
                                        clearExcelHistory(
                                            context,
                                            currentHistory
                                        )
                                    }

                                history =
                                    withContext(
                                        Dispatchers.IO
                                    ) {
                                        loadExcelHistory(
                                            context
                                        )
                                    }

                                lastOutputUri =
                                    null

                                lastOutputName =
                                    ""

                                statusTitle =
                                    "Çöp kutusu temizlendi"

                                statusText =
                                    "$deletedCount Excel çıktısı silindi."
                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .widthIn(
                                    max = 850.dp
                                )
                    ) {
                        Text(
                            "🗑 TÜM GEÇMİŞİ TEMİZLE"
                        )
                    }
                }
            }

            item {
                Text(
                    "Parola ile şifrelenmiş dosyalar, eski XLS biçimi, bozuk dosyalar ve kurumsal IRM korumalı dosyalar desteklenmez.",
                    color = ExcelMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

private data class ExcelProcessOutput(
    val uri: Uri,
    val fileName: String,
    val message: String
)

private data class ExcelHistoryItem(
    val uri: Uri,
    val name: String,
    val createdAtSeconds: Long
)

private fun processExcelFile(
    context: Context,
    uri: Uri
): ExcelProcessOutput {
    val resolver =
        context.contentResolver

    val fileName =
        resolveExcelDisplayName(
            context,
            uri
        )

    val result =
        resolver
            .openInputStream(uri)
            ?.use {
                ExcelProcessor.process(
                    it,
                    fileName
                )
            }
            ?: throw IOException(
                "Dosya açılamadı."
            )

    val outputName =
        createExcelOutputName(
            fileName
        )

    val outputUri =
        saveExcelToDownloads(
            context,
            outputName,
            mimeForExcelName(
                outputName
            ),
            result.data
        )

    val message =
        buildString {
            append(
                outputName
            )

            append(
                "\nİndirilenler/AppForge Excel Tools klasörüne kaydedildi."
            )

            if (
                result.csvCopy
            ) {
                append(
                    "\nCSV kopyası oluşturuldu."
                )
            } else {
                append(
                    "\nKoruma bilgileri kaldırıldı. Düzenlenebilir kopya hazır."
                )
            }
        }

    return ExcelProcessOutput(
        uri = outputUri,
        fileName = outputName,
        message = message
    )
}

private fun resolveExcelDisplayName(
    context: Context,
    uri: Uri
): String {
    var name =
        uri.lastPathSegment
            ?: "dosya.xlsx"

    context
        .contentResolver
        .query(
            uri,
            arrayOf(
                OpenableColumns.DISPLAY_NAME
            ),
            null,
            null,
            null
        )
        ?.use { cursor ->
            if (
                cursor.moveToFirst()
            ) {
                val index =
                    cursor.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME
                    )

                if (index >= 0) {
                    name =
                        cursor.getString(index)
                            ?: name
                }
            }
        }

    return name
}

private fun createExcelOutputName(
    inputName: String
): String {
    val dot =
        inputName.lastIndexOf('.')

    val base =
        if (dot > 0) {
            inputName.substring(0, dot)
        } else {
            inputName
        }

    val extension =
        if (dot > 0) {
            inputName.substring(dot)
        } else {
            ".xlsx"
        }

    val stamp =
        SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            Locale.US
        ).format(
            Date()
        )

    return "${base}_AppForge_$stamp$extension"
}

private fun mimeForExcelName(
    name: String
): String =
    when {
        name.endsWith(
            ".xlsx",
            ignoreCase = true
        ) ->
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

        name.endsWith(
            ".xlsm",
            ignoreCase = true
        ) ->
            "application/vnd.ms-excel.sheet.macroEnabled.12"

        else ->
            "text/csv"
    }

private fun saveExcelToDownloads(
    context: Context,
    fileName: String,
    mimeType: String,
    data: ByteArray
): Uri {
    val resolver =
        context.contentResolver

    val values =
        ContentValues().apply {
            put(
                MediaStore.Downloads.DISPLAY_NAME,
                fileName
            )

            put(
                MediaStore.Downloads.MIME_TYPE,
                mimeType
            )

            put(
                MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS +
                    "/AppForge Excel Tools"
            )

            put(
                MediaStore.Downloads.IS_PENDING,
                1
            )
        }

    val outputUri =
        resolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            values
        )
            ?: throw IOException(
                "Çıktı dosyası oluşturulamadı."
            )

    try {
        resolver
            .openOutputStream(
                outputUri,
                "w"
            )
            ?.use {
                it.write(data)
                it.flush()
            }
            ?: throw IOException(
                "Çıktı dosyası açılamadı."
            )

        val ready =
            ContentValues().apply {
                put(
                    MediaStore.Downloads.IS_PENDING,
                    0
                )
            }

        resolver.update(
            outputUri,
            ready,
            null,
            null
        )
    } catch (t: Throwable) {
        resolver.delete(
            outputUri,
            null,
            null
        )

        throw t
    }

    return outputUri
}

private fun openExcelOutput(
    context: Context,
    uri: Uri,
    fileName: String
): Boolean {

    return runCatching {
        val intent =
            Intent(
                Intent.ACTION_VIEW
            ).apply {
                setDataAndType(
                    uri,
                    mimeForExcelName(
                        fileName
                    )
                )

                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                addFlags(
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }

        context.startActivity(
            Intent.createChooser(
                intent,
                "Dosyayı aç"
            )
        )

        true
    }.getOrElse {
        false
    }
}

private fun loadExcelHistory(
    context: Context
): List<ExcelHistoryItem> {

    val resolver =
        context.contentResolver

    val result =
        mutableListOf<ExcelHistoryItem>()

    val projection =
        arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED
        )

    val selection =
        "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"

    val selectionArgs =
        arrayOf(
            "%AppForge Excel Tools%"
        )

    resolver.query(
        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        selectionArgs,
        "${MediaStore.MediaColumns.DATE_ADDED} DESC"
    )?.use { cursor ->

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
                MediaStore.MediaColumns.DATE_ADDED
            )

        while (
            cursor.moveToNext()
        ) {
            val id =
                cursor.getLong(
                    idIndex
                )

            val name =
                cursor.getString(
                    nameIndex
                ) ?: "Excel çıktısı"

            val createdAt =
                cursor.getLong(
                    dateIndex
                )

            result +=
                ExcelHistoryItem(
                    uri =
                        Uri.withAppendedPath(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            id.toString()
                        ),
                    name =
                        name,
                    createdAtSeconds =
                        createdAt
                )
        }
    }

    return result.take(
        50
    )
}

private fun deleteExcelOutput(
    context: Context,
    uri: Uri
): Boolean {

    return runCatching {
        context
            .contentResolver
            .delete(
                uri,
                null,
                null
            ) > 0
    }.getOrDefault(
        false
    )
}

private fun clearExcelHistory(
    context: Context,
    history: List<ExcelHistoryItem>
): Int {

    var deleted =
        0

    history.forEach {
        item ->

        if (
            deleteExcelOutput(
                context,
                item.uri
            )
        ) {
            deleted +=
                1
        }
    }

    return deleted
}

private fun formatExcelHistoryDate(
    createdAtSeconds: Long
): String {

    if (
        createdAtSeconds <= 0L
    ) {
        return "Tarih bilinmiyor"
    }

    return SimpleDateFormat(
        "dd.MM.yyyy HH:mm",
        Locale.getDefault()
    ).format(
        Date(
            createdAtSeconds *
                1000L
        )
    )
}
