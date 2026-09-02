@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.appforge.studio.ui

import android.content.Intent
import android.os.Build

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hackmaster.videoforge.VideoForgeActivity

private val OtherAppsBg = Color(0xFF060711)
private val OtherAppsCard = Color(0xFF101426)
private val OtherAppsText = Color(0xFFF4F7FF)
private val OtherAppsMuted = Color(0xFFA9B1C7)
private val OtherAppsAccent = Color(0xFF63D9FF)

@Composable
fun OtherAppsScreen(
    onBack: () -> Unit,
    onOpenExcelTools: () -> Unit
) {
    val context = LocalContext.current
    val videoForgeAndroidSupported =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    val videoForgeAbiSupported =
        Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }
    val videoForgeSupported =
        videoForgeAndroidSupported &&
            videoForgeAbiSupported

    Scaffold(
        containerColor = OtherAppsBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Diğer Uygulamalar",
                            color = OtherAppsText,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "AppForge araçları ve mini uygulamalar",
                            color = OtherAppsMuted,
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Geri")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = OtherAppsBg
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OtherAppCard(
                    icon = "▦",
                    title = "AppForge Excel Tools",
                    description =
                        "XLSX, XLSM ve CSV dosyaları için cihaz üzerinde çalışan Excel araçları.",
                    status = "Kullanıma hazır",
                    onClick = onOpenExcelTools
                )
            }

            item {
                OtherAppCard(
                    icon = "▶",
                    title = "VideoForge",
                    description =
                        "Cihaz üzerinde çok dilli video dublajı, konuşmacı ayırma, altyazı, önizleme, kuyruk ve doğrudan video URL işleme.",
                    status =
                        when {
                            videoForgeSupported ->
                                "Kullanıma hazır • V4.1.2"
                            !videoForgeAndroidSupported ->
                                "Android 10 veya üzeri gerekli"
                            else ->
                                "64-bit ARM cihaz gerekli"
                        },
                    onClick =
                        if (videoForgeSupported) {
                            {
                                context.startActivity(
                                    Intent(
                                        context,
                                        VideoForgeActivity::class.java
                                    )
                                )
                            }
                        } else {
                            null
                        }
                )
            }
        }
    }
}

@Composable
private fun OtherAppCard(
    icon: String,
    title: String,
    description: String,
    status: String,
    onClick: (() -> Unit)?
) {
    val card: @Composable () -> Unit = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(
                    "$icon  $title",
                    color = OtherAppsText,
                    fontWeight = FontWeight.Black,
                    fontSize = 19.sp
                )

                Text(
                    description,
                    color = OtherAppsMuted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Text(
                    status,
                    color = OtherAppsAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 900.dp),
            shape = RoundedCornerShape(24.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = OtherAppsCard
                )
        ) {
            card()
        }
    } else {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 900.dp),
            shape = RoundedCornerShape(24.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        OtherAppsCard.copy(alpha = 0.65f)
                )
        ) {
            card()
        }
    }
}
