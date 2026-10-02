package com.appforge.studio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
                    .takeIf {
                        it.optInt(
                            "schemaVersion",
                            -1
                        ) == 2
                    }
            }.getOrNull()
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing
                )
                .imePadding()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(
                    start = 20.dp,
                    top = 20.dp,
                    end = 20.dp,
                    bottom = 140.dp
                ),
        verticalArrangement =
            Arrangement.spacedBy(
                12.dp
            )
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth(),
            horizontalArrangement =
                Arrangement
                    .SpaceBetween
        ) {
            Text(
                text =
                    "Second Brain",
                style =
                    MaterialTheme
                        .typography
                        .headlineSmall,
                fontWeight =
                    FontWeight.Bold
            )

            Button(
                onClick =
                    onBack
            ) {
                Text(
                    "Geri"
                )
            }
        }

        if (
            snapshot ==
                null
        ) {
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
            ) {
                Text(
                    text =
                        "Second Brain V2 snapshot eksik veya geçersiz. " +
                            "Repo içinde " +
                            "python3 scripts/generate-second-brain-snapshot.py " +
                            "çalıştırıp uygulamayı yeniden derle.",
                    modifier =
                        Modifier
                            .padding(
                                16.dp
                            )
                )
            }

            return@Column
        }

        val basis =
            snapshot
                .optString(
                    "sourceBasisSha256",
                    "?"
                )
                .take(
                    16
                )

        BrainCard(
            title =
                "Snapshot V2",
            lines =
                listOf(
                    "Şema: ${snapshot.optInt("schemaVersion", -1)}",
                    "Otorite: ${snapshot.optString("authority", "UNKNOWN")}",
                    "Kaynak özeti: $basis",
                    "Kanıt dosyası: ${snapshot.optInt("sourceBasisFileCount", 0)}"
                )
        )

        BrainCard(
            title =
                "Mimari Durum",
            lines =
                listOf(
                    "Build: ${snapshot.optString("buildArchitecture", "UNKNOWN")}",
                    "Publisher auth: ${snapshot.optString("publisherAuthorization", "UNKNOWN")}",
                    "D1 ledger: ${snapshot.optString("d1Ledger", "UNKNOWN")}",
                    "Production publisher: ${snapshot.optString("productionPublisherEndpoint", "UNKNOWN")}"
                )
        )

        BrainCard(
            title =
                "Doğrulanabilir Kapsam",
            lines =
                listOf(
                    "Wiki sayfası: ${snapshot.optInt("wikiPages", 0)}",
                    "Quality contract dosyası: ${snapshot.optInt("qualityContractFiles", 0)}",
                    "Android unit test dosyası: ${snapshot.optInt("androidUnitTestFiles", 0)}",
                    "D1 migration: ${snapshot.optInt("d1Migrations", 0)}"
                )
        )

        BrainCard(
            title =
                "Canlı Sistem Sınırı",
            lines =
                listOf(
                    "Durum: ${snapshot.optString("liveState", "UNKNOWN")}",
                    "Release gate: ${snapshot.optString("releaseGate", "UNKNOWN")}",
                    "Bu snapshot canlı GitHub veya Cloudflare sorgusu değildir."
                )
        )
    }
}

@Composable
private fun BrainCard(
    title: String,
    lines: List<String>
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
    ) {
        Column(
            modifier =
                Modifier
                    .padding(
                        16.dp
                    ),
            verticalArrangement =
                Arrangement
                    .spacedBy(
                        6.dp
                    )
        ) {
            Text(
                text =
                    title,
                fontWeight =
                    FontWeight.Bold
            )

            lines.forEach {
                line ->

                Text(
                    line
                )
            }
        }
    }
}
