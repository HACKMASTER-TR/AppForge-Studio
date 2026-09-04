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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.io.File

private data class PendingConflictResolution(
    val path: String,
    val choice: GitConflictChoice
)

@Composable
internal fun AdvancedGitPanel(
    workspace: File,
    githubToken: String
) {
    val scope =
        rememberCoroutineScope()

    var snapshot by
        remember(
            workspace.absolutePath
        ) {
            mutableStateOf<
                AdvancedGitSnapshot?
                >(null)
        }

    var localBusy by
        remember {
            mutableStateOf(false)
        }

    var localOutput by
        remember(
            workspace.absolutePath
        ) {
            mutableStateOf(
                "Gelişmiş Git durumunu görmek için Yenile'ye dokunun."
            )
        }

    var selectedPath by
        remember(
            workspace.absolutePath
        ) {
            mutableStateOf("")
        }

    var branchName by
        remember(
            workspace.absolutePath
        ) {
            mutableStateOf("")
        }

    var pendingConflict by
        remember {
            mutableStateOf<
                PendingConflictResolution?
                >(null)
        }

    var githubBusy by
        remember {
            mutableStateOf(false)
        }

    var githubDashboard by
        remember(
            workspace.absolutePath
        ) {
            mutableStateOf<
                GitHubDevOpsDashboard?
                >(null)
        }

    var githubOutput by
        remember(
            workspace.absolutePath
        ) {
            mutableStateOf(
                "GitHub verileri yalnız Yenile'ye dokunulduğunda alınır."
            )
        }

    var prTitle by
        remember {
            mutableStateOf("")
        }

    var prHead by
        remember {
            mutableStateOf("")
        }

    var prBase by
        remember {
            mutableStateOf(
                "main"
            )
        }

    var prBody by
        remember {
            mutableStateOf("")
        }

    var confirmPr by
        remember {
            mutableStateOf(false)
        }

    fun updateSnapshot(
        value: AdvancedGitSnapshot
    ) {
        snapshot =
            value

        if (branchName.isBlank()) {
            branchName =
                value.branch
        }

        if (prHead.isBlank()) {
            prHead =
                value.branch
        }
    }

    fun runLocal(
        action:
            suspend () ->
                Pair<
                    AdvancedGitSnapshot?,
                    String
                    >
    ) {
        if (localBusy) {
            return
        }

        localBusy =
            true

        scope.launch {
            val result =
                runCatching {
                    action()
                }

            result
                .onSuccess {
                    it.first?.let(
                        ::updateSnapshot
                    )
                    localOutput =
                        TerminalSecretMasker.redact(
                            it.second
                        )
                }
                .onFailure {
                    localOutput =
                        "Git işlemi başarısız: ${
                            it.message
                                ?: "Bilinmeyen hata"
                        }"
                }

            localBusy =
                false
        }
    }

    fun refreshGitHub() {
        val origin =
            snapshot
                ?.originUrl
                .orEmpty()

        if (
            githubBusy ||
            origin.isBlank()
        ) {
            return
        }

        githubBusy =
            true

        scope.launch {
            runCatching {
                GitHubDevOpsClient
                    .loadDashboard(
                        originUrl =
                            origin,
                        accessToken =
                            githubToken
                    )
            }
                .onSuccess {
                    githubDashboard =
                        it
                    githubOutput =
                        "GitHub merkezi güncellendi: ${it.repository}"
                }
                .onFailure {
                    githubOutput =
                        "GitHub işlemi başarısız: ${
                            it.message
                                ?: "Bilinmeyen hata"
                        }"
                }

            githubBusy =
                false
        }
    }

    pendingConflict
        ?.let { pending ->
            AlertDialog(
                onDismissRequest = {
                    pendingConflict =
                        null
                },
                title = {
                    Text(
                        "Conflict çözümünü onayla"
                    )
                },
                text = {
                    Text(
                        when (
                            pending.choice
                        ) {
                            GitConflictChoice.OURS ->
                                "${pending.path} dosyasında mevcut dalın sürümü seçilecek ve dosya stage edilecek."

                            GitConflictChoice.THEIRS ->
                                "${pending.path} dosyasında karşı tarafın sürümü seçilecek ve dosya stage edilecek."

                            GitConflictChoice.MARK_RESOLVED ->
                                "${pending.path} dosyasının mevcut içeriği çözülmüş kabul edilip stage edilecek."
                        }
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val request =
                                pendingConflict
                                    ?: return@Button

                            pendingConflict =
                                null

                            runLocal {
                                val updated =
                                    AdvancedGitService
                                        .resolveConflict(
                                            workspace =
                                                workspace,
                                            path =
                                                request.path,
                                            choice =
                                                request.choice
                                        )

                                updated to
                                    "Conflict çözüldü: ${request.path}"
                            }
                        }
                    ) {
                        Text("Uygula")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            pendingConflict =
                                null
                        }
                    ) {
                        Text("Vazgeç")
                    }
                }
            )
        }

    if (confirmPr) {
        AlertDialog(
            onDismissRequest = {
                confirmPr =
                    false
            },
            title = {
                Text(
                    "Pull Request oluşturulsun mu?"
                )
            },
            text = {
                Text(
                    "$prHead → $prBase\n\n$prTitle"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmPr =
                            false

                        val origin =
                            snapshot
                                ?.originUrl
                                .orEmpty()

                        if (
                            origin.isBlank() ||
                            githubBusy
                        ) {
                            return@Button
                        }

                        githubBusy =
                            true

                        scope.launch {
                            runCatching {
                                GitHubDevOpsClient
                                    .createPullRequest(
                                        originUrl =
                                            origin,
                                        accessToken =
                                            githubToken,
                                        title =
                                            prTitle,
                                        head =
                                            prHead,
                                        base =
                                            prBase,
                                        body =
                                            prBody
                                    )
                            }
                                .onSuccess {
                                    githubOutput =
                                        "PR #${it.number} oluşturuldu: ${it.title}"
                                    prTitle =
                                        ""
                                    prBody =
                                        ""
                                }
                                .onFailure {
                                    githubOutput =
                                        "PR oluşturulamadı: ${
                                            it.message
                                                ?: "Bilinmeyen hata"
                                        }"
                                }

                            githubBusy =
                                false
                            refreshGitHub()
                        }
                    }
                ) {
                    Text("PR Oluştur")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        confirmPr =
                            false
                    }
                ) {
                    Text("Vazgeç")
                }
            }
        )
    }

    Column(
        modifier =
            Modifier.fillMaxWidth(),
        verticalArrangement =
            Arrangement.spacedBy(
                10.dp
            )
    ) {
        TerminalPanelTitle(
            "Gelişmiş Git",
            "Branch, seçili stage/unstage, diff ve conflict çözümü. Force/reset gibi yıkıcı işlemler burada otomatik çalıştırılmaz."
        )

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
                    runLocal {
                        val updated =
                            AdvancedGitService
                                .inspect(
                                    workspace
                                )

                        updated to
                            "Gelişmiş Git durumu yenilendi."
                    }
                },
                enabled =
                    !localBusy
            ) {
                Text("Yenile")
            }

            OutlinedButton(
                onClick = {
                    runLocal {
                        snapshot to
                            AdvancedGitService
                                .diff(
                                    workspace =
                                        workspace,
                                    staged =
                                        false,
                                    path =
                                        selectedPath
                                            .takeIf {
                                                it.isNotBlank()
                                            }
                                )
                    }
                },
                enabled =
                    !localBusy
            ) {
                Text("Working Diff")
            }

            OutlinedButton(
                onClick = {
                    runLocal {
                        snapshot to
                            AdvancedGitService
                                .diff(
                                    workspace =
                                        workspace,
                                    staged =
                                        true,
                                    path =
                                        selectedPath
                                            .takeIf {
                                                it.isNotBlank()
                                            }
                                )
                    }
                },
                enabled =
                    !localBusy
            ) {
                Text("Staged Diff")
            }
        }

        snapshot
            ?.let { current ->
                Card(
                    modifier =
                        Modifier.fillMaxWidth(),
                    colors =
                        CardDefaults
                            .cardColors(
                                containerColor =
                                    TerminalSurface
                            )
                ) {
                    Column(
                        modifier =
                            Modifier.padding(
                                12.dp
                            ),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                5.dp
                            )
                    ) {
                        Text(
                            "Dal: ${current.branch}",
                            color =
                                TerminalPrimary,
                            fontWeight =
                                FontWeight.Black
                        )

                        Text(
                            "Stage: ${current.staged.size} • Working: ${current.unstaged.size} • Conflict: ${current.conflicts.size}",
                            color =
                                TerminalMuted,
                            fontSize =
                                10.sp
                        )

                        current.originUrl
                            ?.let {
                                Text(
                                    TerminalSecretMasker
                                        .redact(
                                            it
                                        ),
                                    color =
                                        TerminalSecondary,
                                    fontFamily =
                                        FontFamily.Monospace,
                                    fontSize =
                                        10.sp
                                )
                            }

                        if (
                            current.branches
                                .isNotEmpty()
                        ) {
                            Text(
                                "Dallar: ${current.branches.joinToString()}",
                                color =
                                    TerminalMuted,
                                fontSize =
                                    10.sp
                            )
                        }
                    }
                }

                if (
                    current.conflicts
                        .isNotEmpty()
                ) {
                    TerminalPanelTitle(
                        "Conflict çözümü",
                        "OURS/THEIRS çalışma dosyasını değiştirir; seçimden önce ayrıca onay gösterilir."
                    )

                    current.conflicts
                        .take(20)
                        .forEach { path ->
                            Card(
                                modifier =
                                    Modifier
                                        .fillMaxWidth(),
                                colors =
                                    CardDefaults
                                        .cardColors(
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
                                        Arrangement
                                            .spacedBy(
                                                6.dp
                                            )
                                ) {
                                    Text(
                                        path,
                                        color =
                                            TerminalText,
                                        fontFamily =
                                            FontFamily.Monospace,
                                        fontSize =
                                            10.sp
                                    )

                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(
                                                    rememberScrollState()
                                                ),
                                        horizontalArrangement =
                                            Arrangement
                                                .spacedBy(
                                                    6.dp
                                                )
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                pendingConflict =
                                                    PendingConflictResolution(
                                                        path,
                                                        GitConflictChoice.OURS
                                                    )
                                            }
                                        ) {
                                            Text("Bizimki")
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                pendingConflict =
                                                    PendingConflictResolution(
                                                        path,
                                                        GitConflictChoice.THEIRS
                                                    )
                                            }
                                        ) {
                                            Text("Onlarınki")
                                        }

                                        Button(
                                            onClick = {
                                                pendingConflict =
                                                    PendingConflictResolution(
                                                        path,
                                                        GitConflictChoice.MARK_RESOLVED
                                                    )
                                            }
                                        ) {
                                            Text("Çözüldü")
                                        }
                                    }
                                }
                            }
                        }
                }
            }

        OutlinedTextField(
            value =
                selectedPath,
            onValueChange = {
                selectedPath =
                    it.take(
                        2_048
                    )
            },
            modifier =
                Modifier.fillMaxWidth(),
            label = {
                Text(
                    "Dosya yolu"
                )
            },
            placeholder = {
                Text(
                    "src/main.kt"
                )
            },
            singleLine =
                true
        )

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
                    runLocal {
                        val updated =
                            AdvancedGitService
                                .stagePath(
                                    workspace,
                                    selectedPath
                                )

                        updated to
                            "Stage edildi: $selectedPath"
                    }
                },
                enabled =
                    !localBusy &&
                        selectedPath
                            .isNotBlank()
            ) {
                Text("Stage")
            }

            OutlinedButton(
                onClick = {
                    runLocal {
                        val updated =
                            AdvancedGitService
                                .unstagePath(
                                    workspace,
                                    selectedPath
                                )

                        updated to
                            "Unstage edildi: $selectedPath"
                    }
                },
                enabled =
                    !localBusy &&
                        selectedPath
                            .isNotBlank()
            ) {
                Text("Unstage")
            }
        }

        OutlinedTextField(
            value =
                branchName,
            onValueChange = {
                branchName =
                    it.take(
                        120
                    )
            },
            modifier =
                Modifier.fillMaxWidth(),
            label = {
                Text(
                    "Dal adı"
                )
            },
            singleLine =
                true
        )

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
            OutlinedButton(
                onClick = {
                    runLocal {
                        val updated =
                            AdvancedGitService
                                .createBranch(
                                    workspace,
                                    branchName
                                )

                        updated to
                            "Dal oluşturuldu: $branchName"
                    }
                },
                enabled =
                    !localBusy &&
                        branchName
                            .isNotBlank()
            ) {
                Text("Dal Oluştur")
            }

            Button(
                onClick = {
                    runLocal {
                        val updated =
                            AdvancedGitService
                                .checkoutBranch(
                                    workspace,
                                    branchName
                                )

                        updated to
                            "Dala geçildi: $branchName"
                    }
                },
                enabled =
                    !localBusy &&
                        branchName
                            .isNotBlank()
            ) {
                Text("Dala Geç")
            }
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
                    localOutput,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(
                                min = 80.dp,
                                max = 360.dp
                            )
                            .padding(
                                10.dp
                            ),
                    color =
                        TerminalText,
                    fontFamily =
                        FontFamily.Monospace,
                    fontSize =
                        10.sp,
                    lineHeight =
                        14.sp
                )
            }
        }

        TerminalPanelTitle(
            "GitHub DevOps",
            "PR, Actions, Release ve artifact bilgileri doğrudan api.github.com üzerinden okunur. OAuth token yalnız bellekte kullanılır."
        )

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
                    refreshGitHub()
                },
                enabled =
                    !githubBusy &&
                        !snapshot
                            ?.originUrl
                            .isNullOrBlank()
            ) {
                Text(
                    if (githubBusy) {
                        "Yükleniyor…"
                    } else {
                        "GitHub Yenile"
                    }
                )
            }
        }

        githubDashboard
            ?.let { dashboard ->
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
                                12.dp
                            ),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                6.dp
                            )
                    ) {
                        Text(
                            dashboard.repository,
                            color =
                                TerminalPrimary,
                            fontWeight =
                                FontWeight.Black
                        )

                        Text(
                            "Açık PR: ${dashboard.pullRequests.size}",
                            color =
                                TerminalText
                        )

                        dashboard.pullRequests
                            .take(8)
                            .forEach {
                                Text(
                                    "#${it.number} ${it.head} → ${it.base} • ${it.title}",
                                    color =
                                        TerminalMuted,
                                    fontSize =
                                        10.sp
                                )
                            }

                        Text(
                            "Actions: ${dashboard.workflowRuns.size}",
                            color =
                                TerminalText
                        )

                        dashboard.workflowRuns
                            .take(8)
                            .forEach {
                                Text(
                                    "${it.name} • ${it.branch} • ${it.status}/${it.conclusion.ifBlank { "-" }}",
                                    color =
                                        TerminalMuted,
                                    fontSize =
                                        10.sp
                                )
                            }

                        Text(
                            "Releases: ${dashboard.releases.size} • Artifacts: ${dashboard.artifacts.size}",
                            color =
                                TerminalText
                        )

                        dashboard.releases
                            .take(5)
                            .forEach {
                                Text(
                                    "${it.tag} • ${it.name.ifBlank { it.tag }}",
                                    color =
                                        TerminalMuted,
                                    fontSize =
                                        10.sp
                                )
                            }

                        dashboard.artifacts
                            .take(5)
                            .forEach {
                                Text(
                                    "${it.name} • ${it.sizeBytes / 1024} KiB${if (it.expired) " • süresi dolmuş" else ""}",
                                    color =
                                        TerminalMuted,
                                    fontSize =
                                        10.sp
                                )
                            }
                    }
                }
            }

        OutlinedTextField(
            value =
                prTitle,
            onValueChange = {
                prTitle =
                    it.take(
                        256
                    )
            },
            modifier =
                Modifier.fillMaxWidth(),
            label = {
                Text(
                    "PR başlığı"
                )
            },
            singleLine =
                true
        )

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(
                    7.dp
                )
        ) {
            OutlinedTextField(
                value =
                    prHead,
                onValueChange = {
                    prHead =
                        it.take(
                            120
                        )
                },
                modifier =
                    Modifier.weight(
                        1f
                    ),
                label = {
                    Text("Kaynak")
                },
                singleLine =
                    true
            )

            OutlinedTextField(
                value =
                    prBase,
                onValueChange = {
                    prBase =
                        it.take(
                            120
                        )
                },
                modifier =
                    Modifier.weight(
                        1f
                    ),
                label = {
                    Text("Hedef")
                },
                singleLine =
                    true
            )
        }

        OutlinedTextField(
            value =
                prBody,
            onValueChange = {
                prBody =
                    it.take(
                        16 * 1024
                    )
            },
            modifier =
                Modifier.fillMaxWidth(),
            label = {
                Text(
                    "PR açıklaması"
                )
            },
            minLines =
                3,
            maxLines =
                8
        )

        Button(
            onClick = {
                confirmPr =
                    true
            },
            enabled =
                !githubBusy &&
                    githubToken
                        .isNotBlank() &&
                    !snapshot
                        ?.originUrl
                        .isNullOrBlank() &&
                    prTitle
                        .isNotBlank() &&
                    prHead
                        .isNotBlank() &&
                    prBase
                        .isNotBlank()
        ) {
            Text(
                if (
                    githubToken
                        .isBlank()
                ) {
                    "GitHub Bağlantısı Gerekli"
                } else {
                    "PR Oluştur…"
                }
            )
        }

        Text(
            githubOutput,
            color =
                TerminalMuted,
            fontSize =
                10.sp
        )
    }
}
