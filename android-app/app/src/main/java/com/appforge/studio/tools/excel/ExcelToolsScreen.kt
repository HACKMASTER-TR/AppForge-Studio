@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.appforge.studio.tools.excel

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }
    var statusTitle by remember { mutableStateOf("Hazır") }
    var statusText by remember {
        mutableStateOf(
            "XLSX, XLSM veya CSV dosyanı seç.\n" +
                "Dosya işlemleri cihaz üzerinde gerçekleştirilir."
        )
    }

    val picker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult

            scope.launch {
                busy = true
                statusTitle = "İşleniyor"
                statusText = "Seçilen dosya analiz ediliyor…"

                try {
                    val message =
                        withContext(Dispatchers.IO) {
                            processExcelFile(
                                context,
                                uri
                            )
                        }

                    statusTitle = "İşlem tamamlandı"
                    statusText = message
                } catch (t: Throwable) {
                    statusTitle = "İşlem başarısız"
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
                            "Excel dosya araçları",
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
                            "▦  Excel dosyanı düzenlenebilir kopyaya dönüştür",
                            color = ExcelText,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp
                        )

                        Text(
                            "XLSX ve XLSM paketlerindeki çalışma kitabı ve sayfa koruma etiketlerini kaldırılmış yeni bir kopya oluşturur. CSV dosyalarında orijinale dokunmadan yeni kopya oluşturur.",
                            color = ExcelMuted,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )

                        Text(
                            "✓ Formüller ve biçimlendirme korunur\n" +
                                "✓ XLSM makro projesi paket içinde kalır\n" +
                                "✓ XLSX, XLSM ve CSV desteği\n" +
                                "✓ En fazla 80 MB",
                            color = ExcelText,
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )

                        Button(
                            onClick = {
                                picker.launch(
                                    arrayOf(
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                        "application/vnd.ms-excel.sheet.macroEnabled.12",
                                        "text/csv",
                                        "application/csv",
                                        "application/octet-stream"
                                    )
                                )
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
                    }
                }
            }

            item {
                Text(
                    "Açılış parolasıyla şifrelenmiş dosyalar, eski XLS biçimi, bozuk dosyalar ve kurumsal IRM koruması desteklenmez.",
                    color = ExcelMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

private fun processExcelFile(
    context: Context,
    uri: Uri
): String {
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

    saveExcelToDownloads(
        context,
        outputName,
        mimeForExcelName(outputName),
        result.data
    )

    return buildString {
        append(outputName)
        append("\nDownload/AppForge Excel Tools klasörüne kaydedildi.")

        if (result.csvCopy) {
            append("\nCSV kopyası oluşturuldu.")
        } else {
            append(
                "\nDeğiştirilen paket bölümü: ${result.changedParts}"
            )
        }
    }
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
) {
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
}
