package com.appforge.studio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable
fun SecondBrainScreen(
    onBack: () -> Unit
) {
    val context =
        LocalContext.current

    val snapshot =
        remember {
            runCatching {
                context.assets
                    .open(
                        "second_brain_snapshot.json"
                    )
                    .bufferedReader()
                    .use { reader ->
                        JSONObject(
                            reader.readText()
                        )
                    }
            }.getOrNull()
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(20.dp),
        verticalArrangement =
            Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceBetween
        ) {
            Text(
                text = "Second Brain",
                style =
                    MaterialTheme
                        .typography
                        .headlineSmall,
                fontWeight =
                    FontWeight.Bold
            )

            Button(
                onClick = onBack
            ) {
                Text("Geri")
            }
        }

        if (snapshot == null) {
            Card(
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Text(
                    text =
                        "Second Brain snapshot bulunamadı. " +
                        "Repo içinde ./scripts/brain all komutunu çalıştırıp " +
                        "uygulamayı yeniden derle.",
                    modifier =
                        Modifier.padding(16.dp)
                )
            }
        } else {
            BrainCard(
                title = "Durum",
                lines =
                    listOf(
                        "Sürüm: ${snapshot.optString("version", "?")}",
                        "Branch: ${snapshot.optString("branch", "?")}",
                        "HEAD: ${snapshot.optString("head", "?")}"
                    )
            )

            BrainCard(
                title = "Güvenlik ve Risk",
                lines =
                    listOf(
                        "Karar: ${snapshot.optString("risk", "UNKNOWN")}",
                        "Risk: ${snapshot.optInt("riskScore", -1)}/100",
                        "Security: ${snapshot.optString("security", "UNKNOWN")}",
                        "Release gate: ${snapshot.optString("release", "UNKNOWN")}"
                    )
            )

            BrainCard(
                title = "Proje Haritası",
                lines =
                    listOf(
                        "API route: ${snapshot.optInt("apiRoutes", 0)}",
                        "DB tablo: ${snapshot.optInt("databaseTables", 0)}",
                        "Migration: ${snapshot.optInt("migrations", 0)}",
                        "Test: ${snapshot.optInt("tests", 0)}"
                    )
            )

            BrainCard(
                title = "Canlı Sistem",
                lines =
                    listOf(
                        "GitHub: ${snapshot.optString("liveGithub", "NOT_CHECKED")}",
                        "Railway: ${snapshot.optString("liveRailway", "NOT_CHECKED")}"
                    )
            )
        }
    }
}

@Composable
private fun BrainCard(
    title: String,
    lines: List<String>
) {
    Card(
        modifier =
            Modifier.fillMaxWidth()
    ) {
        Column(
            modifier =
                Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                fontWeight =
                    FontWeight.Bold
            )

            lines.forEach { line ->
                Text(line)
            }
        }
    }
}
