package com.appforge.studio.terminal

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.io.File

private data class PendingAutomationCommand(
    val title: String,
    val detail: String,
    val command: String
)

@Composable
internal fun UltimateProjectAutomationPanel(
    workspace: File,
    detection: ProjectDetection,
    onOpenDeployment: () -> Unit,
    onAiHandoff: (String) -> Unit
) {
    val context =
        LocalContext.current

    val scope =
        rememberCoroutineScope()

    val executor =
        remember {
            UltimateProjectAutomationExecutor(
                context.applicationContext
            )
        }

    val plan =
        remember(
            workspace.absolutePath,
            workspace.lastModified(),
            detection.kind
        ) {
            UltimateProjectAutomationPlanner
                .plan(
                    workspace,
                    detection.kind
                )
        }

    var distribution by
        remember(
            workspace.absolutePath
        ) {
            mutableStateOf(
                LinuxDistribution.UBUNTU
            )
        }

    var selectedToolchains by
        remember(
            workspace.absolutePath,
            plan.recommendedToolchains
        ) {
            mutableStateOf(
                plan.recommendedToolchains
            )
        }

    var pendingCommand by
        remember {
            mutableStateOf<
                PendingAutomationCommand?
                >(null)
        }

    var busy by
        remember {
            mutableStateOf(false)
        }

    var output by
        remember(
            workspace.absolutePath
        ) {
            mutableStateOf(
                "Hazır. Önce Linux araç zincirlerini kontrol edebilir veya proje yaşam döngüsü adımlarından birini seçebilirsin."
            )
        }

    fun executeConfirmed(
        request: PendingAutomationCommand
    ) {
        if (busy) {
            return
        }

        busy =
            true

        scope.launch {
            runCatching {
                executor.execute(
                    workspace =
                        workspace,
                    distribution =
                        distribution,
                    command =
                        request.command,
                    confirmed =
                        true
                )
            }
                .onSuccess { result ->
                    output =
                        buildString {
                            append(
                                request.title
                            )
                            append(
                                "\nExit: "
                            )
                            append(
                                result.exitCode
                            )

                            if (
                                result.timedOut
                            ) {
                                append(
                                    " • zaman aşımı"
                                )
                            }

                            if (
                                result.output
                                    .isNotBlank()
                            ) {
                                append(
                                    "\n\n"
                                )
                                append(
                                    result.output
                                )
                            }
                        }
                }
                .onFailure { error ->
                    output =
                        "${request.title} çalıştırılamadı: ${error.message ?: "Bilinmeyen hata"}"
                }

            busy =
                false
        }
    }

    pendingCommand
        ?.let { request ->
            val explanation =
                TerminalCommandAdvisor
                    .explain(
                        request.command
                    )

            AlertDialog(
                onDismissRequest = {
                    if (!busy) {
                        pendingCommand =
                            null
                    }
                },
                title = {
                    Text(
                        request.title
                    )
                },
                text = {
                    Column(
                        verticalArrangement =
                            Arrangement.spacedBy(
                                9.dp
                            )
                    ) {
                        Text(
                            request.detail
                        )

                        Card(
                            colors =
                                CardDefaults.cardColors(
                                    containerColor =
                                        TerminalBackground
                                )
                        ) {
                            Text(
                                TerminalSecretMasker
                                    .redact(
                                        request.command
                                    ),
                                modifier =
                                    Modifier.padding(
                                        11.dp
                                    ),
                                color =
                                    TerminalPrimary,
                                fontFamily =
                                    FontFamily.Monospace,
                                fontSize =
                                    10.sp
                            )
                        }

                        Text(
                            "Linux: ${distribution.title} • Risk: ${explanation.risk}",
                            color =
                                if (
                                    explanation.allowed
                                ) {
                                    TerminalSecondary
                                } else {
                                    TerminalError
                                },
                            fontSize =
                                10.sp
                        )

                        Text(
                            "Komut yalnız doğrulanmış AppForge rootfs içinde /workspace üzerinde çalışır. Bu onaydan önce hiçbir paket kurulmaz veya dosya değiştirilmez.",
                            color =
                                TerminalMuted,
                            fontSize =
                                10.sp,
                            lineHeight =
                                15.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            pendingCommand =
                                null
                            executeConfirmed(
                                request
                            )
                        },
                        enabled =
                            !busy &&
                                explanation.allowed
                    ) {
                        Text(
                            "Onayla ve Çalıştır"
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            pendingCommand =
                                null
                        },
                        enabled =
                            !busy
                    ) {
                        Text(
                            "Vazgeç"
                        )
                    }
                }
            )
        }

    Card(
        modifier =
            Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    TerminalSurfaceRaised
            )
    ) {
        Column(
            modifier =
                Modifier.padding(
                    14.dp
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    10.dp
                )
        ) {
            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {
                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {
                    Text(
                        "Otomatik Proje Planı",
                        color =
                            TerminalText,
                        fontWeight =
                            FontWeight.Black,
                        fontSize =
                            17.sp
                    )

                    Text(
                        "${plan.projectKind.title} • ${plan.steps.size} yaşam döngüsü adımı",
                        color =
                            TerminalPrimary,
                        fontSize =
                            10.sp
                    )
                }

                if (busy) {
                    CircularProgressIndicator(
                        strokeWidth =
                            2.dp
                    )
                }
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(
                            rememberScrollState()
                        ),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        7.dp
                    )
            ) {
                LinuxDistribution.entries
                    .forEach { item ->
                        if (
                            item == distribution
                        ) {
                            Button(
                                onClick = {
                                    distribution =
                                        item
                                }
                            ) {
                                Text(
                                    item.title
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    distribution =
                                        item
                                }
                            ) {
                                Text(
                                    item.title
                                )
                            }
                        }
                    }
            }

            TerminalPanelTitle(
                "Görsel Paket Mağazası",
                "Yalnız AppForge'un sabit apt kataloğundaki paket grupları kullanılabilir. Paket adı elle yazdırılmaz."
            )

            LinuxToolchainCatalog.specs
                .forEach { spec ->
                    val selected =
                        spec.id in
                            selectedToolchains

                    Card(
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
                                        10.dp
                                    ),
                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    9.dp
                                )
                        ) {
                            Column(
                                modifier =
                                    Modifier.weight(
                                        1f
                                    )
                            ) {
                                Text(
                                    spec.title,
                                    color =
                                        TerminalText,
                                    fontWeight =
                                        FontWeight.Bold
                                )

                                Text(
                                    spec.description,
                                    color =
                                        TerminalMuted,
                                    fontSize =
                                        9.sp
                                )

                                Text(
                                    spec.packages
                                        .joinToString(
                                            " • "
                                        ),
                                    color =
                                        TerminalSecondary,
                                    fontFamily =
                                        FontFamily.Monospace,
                                    fontSize =
                                        8.sp
                                )
                            }

                            if (selected) {
                                Button(
                                    onClick = {
                                        selectedToolchains =
                                            selectedToolchains -
                                                spec.id
                                    }
                                ) {
                                    Text(
                                        "Seçili"
                                    )
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        selectedToolchains =
                                            selectedToolchains +
                                                spec.id
                                    }
                                ) {
                                    Text(
                                        "Ekle"
                                    )
                                }
                            }
                        }
                    }
                }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(
                            rememberScrollState()
                        ),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        7.dp
                    )
            ) {
                Button(
                    onClick = {
                        val command =
                            executor
                                .packageInstallCommand(
                                    selectedToolchains
                                )

                        pendingCommand =
                            PendingAutomationCommand(
                                title =
                                    "Araç zincirlerini kur",
                                detail =
                                    "Seçilen güvenilir apt paket grupları Ubuntu/Debian rootfs içine kurulacak.",
                                command =
                                    command
                            )
                    },
                    enabled =
                        !busy &&
                            selectedToolchains
                                .isNotEmpty()
                ) {
                    Text(
                        "Seçilenleri Kur…"
                    )
                }

                OutlinedButton(
                    onClick = {
                        pendingCommand =
                            PendingAutomationCommand(
                                title =
                                    "Araç zincirlerini tara",
                                detail =
                                    "Sadece command -v ile mevcut araçları kontrol eder; paket kurmaz.",
                                command =
                                    UltimateProjectAutomationPlanner
                                        .runtimeProbeCommand()
                            )
                    },
                    enabled =
                        !busy
                ) {
                    Text(
                        "Kurulu Araçları Tara…"
                    )
                }
            }

            TerminalPanelTitle(
                "Install • Test • Build",
                "Komutlar proje türü ve proje kökündeki kilit/yapı dosyalarından otomatik hazırlanır."
            )

            if (
                plan.steps.isEmpty()
            ) {
                Text(
                    "Bu proje için güvenilir otomatik yaşam döngüsü komutu üretilemedi. Terminalden veya Builder'dan manuel devam edebilirsin.",
                    color =
                        TerminalMuted,
                    fontSize =
                        10.sp
                )
            } else {
                plan.steps
                    .forEach { step ->
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
                                        10.dp
                                    ),
                                verticalArrangement =
                                    Arrangement.spacedBy(
                                        5.dp
                                    )
                            ) {
                                Row(
                                    modifier =
                                        Modifier.fillMaxWidth(),
                                    horizontalArrangement =
                                        Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        modifier =
                                            Modifier.weight(
                                                1f
                                            )
                                    ) {
                                        Text(
                                            step.title,
                                            color =
                                                TerminalText,
                                            fontWeight =
                                                FontWeight.Bold
                                        )

                                        Text(
                                            step.description,
                                            color =
                                                TerminalMuted,
                                            fontSize =
                                                9.sp
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            pendingCommand =
                                                PendingAutomationCommand(
                                                    title =
                                                        step.title,
                                                    detail =
                                                        step.description,
                                                    command =
                                                        step.command
                                                )
                                        },
                                        enabled =
                                            !busy
                                    ) {
                                        Text(
                                            when (
                                                step.kind
                                            ) {
                                                ProjectAutomationStepKind.INSTALL ->
                                                    "Install…"

                                                ProjectAutomationStepKind.TEST ->
                                                    "Test…"

                                                ProjectAutomationStepKind.BUILD ->
                                                    "Build…"
                                            }
                                        )
                                    }
                                }

                                Text(
                                    TerminalSecretMasker
                                        .redact(
                                            step.command
                                        ),
                                    color =
                                        TerminalSecondary,
                                    fontFamily =
                                        FontFamily.Monospace,
                                    fontSize =
                                        9.sp
                                )
                            }
                        }
                    }
            }

            UltimateProjectPipelinePanel(
                workspace = workspace,
                plan = plan,
                distribution = distribution,
                selectedToolchains =
                    selectedToolchains,
                onOpenDeployment =
                    onOpenDeployment,
                onAiHandoff =
                    onAiHandoff
            )

            TerminalPanelTitle(
                "Deploy",
                if (
                    plan.deployHints.isEmpty()
                ) {
                    "Sağlayıcı dosyası algılanmadı; Deployment Merkezi hedef seçimini gösterecek."
                } else {
                    "Algılanan hedefler: ${plan.deployHints.joinToString { it.title }}"
                }
            )

            Button(
                onClick =
                    onOpenDeployment,
                enabled =
                    !busy
            ) {
                Text(
                    "Deployment Merkezi'ni Aç"
                )
            }

            plan.notes
                .forEach { note ->
                    Text(
                        "• $note",
                        color =
                            TerminalWarning,
                        fontSize =
                            9.sp,
                        lineHeight =
                            14.sp
                    )
                }

            Card(
                modifier =
                    Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            TerminalBackground
                    )
            ) {
                SelectionContainer {
                    Text(
                        output,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(
                                    min = 90.dp,
                                    max = 360.dp
                                )
                                .padding(
                                    10.dp
                                ),
                        color =
                            if (
                                output.contains(
                                    "çalıştırılamadı",
                                    ignoreCase =
                                        true
                                )
                            ) {
                                TerminalError
                            } else {
                                TerminalText
                            },
                        fontFamily =
                            FontFamily.Monospace,
                        fontSize =
                            9.sp,
                        lineHeight =
                            13.sp
                    )
                }
            }
        }
    }
}
