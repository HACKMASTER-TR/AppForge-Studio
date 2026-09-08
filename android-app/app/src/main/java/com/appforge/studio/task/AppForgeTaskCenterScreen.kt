@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.appforge.studio.task

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale


private val TaskBg =
    Color(
        0xFF060711
    )

private val TaskSurface =
    Color(
        0xFF101426
    )

private val TaskSurfaceRaised =
    Color(
        0xFF171B32
    )

private val TaskPrimary =
    Color(
        0xFF63D9FF
    )

private val TaskText =
    Color(
        0xFFF4F7FF
    )

private val TaskMuted =
    Color(
        0xFFA9B1C7
    )

private val TaskSuccess =
    Color(
        0xFF7AE7B7
    )

private val TaskWarning =
    Color(
        0xFFFFB45E
    )

private val TaskError =
    Color(
        0xFFFF8E9B
    )


private object TaskCenterCopy {

    private val tr =
        mapOf(
            "title" to
                "Görev Merkezi",
            "subtitle" to
                "Ağır işlemler, kuyruk ve ilerleme",
            "active" to
                "Aktif",
            "total" to
                "Toplam",
            "clear" to
                "Bitenleri Temizle",
            "empty" to
                "Henüz görev yok.",
            "empty_body" to
                "İçe aktarma, yedekleme ve diğer ağır işlemler burada görünecek.",
            "queued" to
                "Kuyrukta",
            "running" to
                "Çalışıyor",
            "success" to
                "Tamamlandı",
            "failed" to
                "Başarısız",
            "cancelled" to
                "İptal edildi",
            "cancel" to
                "İptal",
            "retry" to
                "Tekrar Dene",
            "attempt" to
                "Deneme"
        )

    private val en =
        mapOf(
            "title" to
                "Task Center",
            "subtitle" to
                "Heavy jobs, queue and progress",
            "active" to
                "Active",
            "total" to
                "Total",
            "clear" to
                "Clear Finished",
            "empty" to
                "No tasks yet.",
            "empty_body" to
                "Imports, backups and other heavy jobs will appear here.",
            "queued" to
                "Queued",
            "running" to
                "Running",
            "success" to
                "Completed",
            "failed" to
                "Failed",
            "cancelled" to
                "Cancelled",
            "cancel" to
                "Cancel",
            "retry" to
                "Retry",
            "attempt" to
                "Attempt"
        )

    private val de =
        mapOf(
            "title" to
                "Aufgabenzentrale",
            "subtitle" to
                "Schwere Aufgaben, Warteschlange und Fortschritt",
            "active" to
                "Aktiv",
            "total" to
                "Gesamt",
            "clear" to
                "Fertige löschen",
            "empty" to
                "Noch keine Aufgaben.",
            "empty_body" to
                "Importe, Sicherungen und andere schwere Aufgaben erscheinen hier.",
            "queued" to
                "In Warteschlange",
            "running" to
                "Läuft",
            "success" to
                "Abgeschlossen",
            "failed" to
                "Fehlgeschlagen",
            "cancelled" to
                "Abgebrochen",
            "cancel" to
                "Abbrechen",
            "retry" to
                "Erneut versuchen",
            "attempt" to
                "Versuch"
        )

    private val ar =
        mapOf(
            "title" to
                "مركز المهام",
            "subtitle" to
                "المهام الثقيلة والطابور والتقدم",
            "active" to
                "نشط",
            "total" to
                "الإجمالي",
            "clear" to
                "مسح المكتملة",
            "empty" to
                "لا توجد مهام بعد.",
            "empty_body" to
                "ستظهر هنا عمليات الاستيراد والنسخ الاحتياطي والمهام الثقيلة الأخرى.",
            "queued" to
                "في الطابور",
            "running" to
                "قيد التشغيل",
            "success" to
                "مكتملة",
            "failed" to
                "فشلت",
            "cancelled" to
                "ملغاة",
            "cancel" to
                "إلغاء",
            "retry" to
                "إعادة المحاولة",
            "attempt" to
                "المحاولة"
        )


    fun text(
        configuredLanguage:
            String,
        key:
            String
    ): String {
        val language =
            if (
                configuredLanguage ==
                    "system"
            ) {
                Locale
                    .getDefault()
                    .language
                    .lowercase(
                        Locale.ROOT
                    )
            } else {
                configuredLanguage
                    .lowercase(
                        Locale.ROOT
                    )
            }

        val source =
            when (
                language
            ) {
                "en" ->
                    en

                "de" ->
                    de

                "ar" ->
                    ar

                else ->
                    tr
            }

        return source[key]
            ?: tr[key]
            ?: key
    }
}


@Composable
fun AppForgeTaskCenterScreen(
    languageCode:
        String,
    onBack:
        () -> Unit
) {
    /*
     * This is intentionally the ONLY UI layer observing task progress.
     * MainActivity/Home do not collect AppForgeTaskManager.tasks, so
     * rapid progress updates cannot recompose the root application.
     */
    val tasks by
        AppForgeTaskManager
            .tasks
            .collectAsState()

    fun t(
        key: String
    ): String =
        TaskCenterCopy
            .text(
                languageCode,
                key
            )

    val activeCount =
        tasks.count {
            it.state ==
                AppForgeTaskState.QUEUED ||
                it.state ==
                AppForgeTaskState.RUNNING
        }

    val finishedExists =
        tasks.any {
            it.state !=
                AppForgeTaskState.QUEUED &&
                it.state !=
                    AppForgeTaskState.RUNNING
        }

    val orderedTasks =
        tasks.sortedWith(
            compareBy<AppForgeTaskSnapshot> {
                when (
                    it.state
                ) {
                    AppForgeTaskState.RUNNING ->
                        0

                    AppForgeTaskState.QUEUED ->
                        1

                    else ->
                        2
                }
            }.thenByDescending {
                it.createdAt
            }
        )

    Scaffold(
        containerColor =
            TaskBg,
        topBar = {
            TopAppBar(
                colors =
                    TopAppBarDefaults
                        .topAppBarColors(
                            containerColor =
                                TaskBg,
                            titleContentColor =
                                TaskText
                        ),
                navigationIcon = {
                    TextButton(
                        onClick =
                            onBack
                    ) {
                        Text(
                            "‹",
                            color =
                                TaskPrimary,
                            fontSize =
                                30.sp
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            t(
                                "title"
                            ),
                            fontWeight =
                                FontWeight.Black,
                            fontSize =
                                19.sp
                        )

                        Text(
                            t(
                                "subtitle"
                            ),
                            color =
                                TaskMuted,
                            fontSize =
                                10.sp
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
                    horizontal =
                        14.dp,
                    vertical =
                        12.dp
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    10.dp
                ),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {
            item {
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .widthIn(
                                max =
                                    980.dp
                            ),
                    shape =
                        RoundedCornerShape(
                            20.dp
                        ),
                    colors =
                        CardDefaults
                            .cardColors(
                                containerColor =
                                    TaskSurface
                            )
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    16.dp
                                ),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                10.dp
                            )
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement
                                    .SpaceBetween,
                            verticalAlignment =
                                Alignment
                                    .CenterVertically
                        ) {
                            Column {
                                Text(
                                    "${t("active")}: $activeCount",
                                    color =
                                        if (
                                            activeCount >
                                                0
                                        ) {
                                            TaskPrimary
                                        } else {
                                            TaskMuted
                                        },
                                    fontWeight =
                                        FontWeight.Bold
                                )

                                Text(
                                    "${t("total")}: ${tasks.size}",
                                    color =
                                        TaskMuted,
                                    fontSize =
                                        11.sp
                                )
                            }

                            if (
                                finishedExists
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        AppForgeTaskManager
                                            .clearFinished()
                                    }
                                ) {
                                    Text(
                                        t(
                                            "clear"
                                        ),
                                        maxLines =
                                            1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (
                orderedTasks
                    .isEmpty()
            ) {
                item {
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .widthIn(
                                    max =
                                        980.dp
                                ),
                        shape =
                            RoundedCornerShape(
                                20.dp
                            ),
                        colors =
                            CardDefaults
                                .cardColors(
                                    containerColor =
                                        TaskSurface
                                )
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        22.dp
                                    ),
                            verticalArrangement =
                                Arrangement.spacedBy(
                                    7.dp
                                )
                        ) {
                            Text(
                                t(
                                    "empty"
                                ),
                                color =
                                    TaskText,
                                fontWeight =
                                    FontWeight.Bold,
                                fontSize =
                                    17.sp
                            )

                            Text(
                                t(
                                    "empty_body"
                                ),
                                color =
                                    TaskMuted,
                                fontSize =
                                    12.sp,
                                lineHeight =
                                    17.sp
                            )
                        }
                    }
                }
            } else {
                items(
                    items =
                        orderedTasks,
                    key = {
                        it.id
                    }
                ) {
                    task ->

                    TaskCenterCard(
                        task =
                            task,
                        languageCode =
                            languageCode
                    )
                }
            }

            item {
                Spacer(
                    Modifier.height(
                        18.dp
                    )
                )
            }
        }
    }
}


@Composable
private fun TaskCenterCard(
    task:
        AppForgeTaskSnapshot,
    languageCode:
        String
) {
    fun t(
        key: String
    ): String =
        TaskCenterCopy
            .text(
                languageCode,
                key
            )

    val stateKey =
        when (
            task.state
        ) {
            AppForgeTaskState.QUEUED ->
                "queued"

            AppForgeTaskState.RUNNING ->
                "running"

            AppForgeTaskState.SUCCESS ->
                "success"

            AppForgeTaskState.FAILED ->
                "failed"

            AppForgeTaskState.CANCELLED ->
                "cancelled"
        }

    val accent =
        when (
            task.state
        ) {
            AppForgeTaskState.SUCCESS ->
                TaskSuccess

            AppForgeTaskState.FAILED ->
                TaskError

            AppForgeTaskState.CANCELLED ->
                TaskWarning

            AppForgeTaskState.RUNNING ->
                TaskPrimary

            AppForgeTaskState.QUEUED ->
                TaskMuted
        }

    val progress =
        task.progress
            .coerceIn(
                0,
                100
            )

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .widthIn(
                    max =
                        980.dp
                ),
        shape =
            RoundedCornerShape(
                20.dp
            ),
        colors =
            CardDefaults
                .cardColors(
                    containerColor =
                        TaskSurface
                )
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        15.dp
                    ),
            verticalArrangement =
                Arrangement.spacedBy(
                    10.dp
                )
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth(),
                horizontalArrangement =
                    Arrangement
                        .SpaceBetween,
                verticalAlignment =
                    Alignment
                        .CenterVertically
            ) {
                Column(
                    modifier =
                        Modifier.weight(
                            1f
                        )
                ) {
                    Text(
                        task.name,
                        color =
                            TaskText,
                        fontWeight =
                            FontWeight.Bold,
                        fontSize =
                            15.sp,
                        maxLines =
                            2,
                        overflow =
                            TextOverflow
                                .Ellipsis
                    )

                    Text(
                        t(
                            stateKey
                        ),
                        color =
                            accent,
                        fontSize =
                            11.sp,
                        fontWeight =
                            FontWeight.Bold
                    )
                }

                Text(
                    "$progress%",
                    color =
                        accent,
                    fontWeight =
                        FontWeight.Black,
                    fontSize =
                        18.sp
                )
            }

            /*
             * Custom progress bar avoids depending on a specific
             * Material3 determinate-progress API signature.
             */
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(
                            8.dp
                        )
                        .background(
                            TaskSurfaceRaised,
                            RoundedCornerShape(
                                999.dp
                            )
                        )
            ) {
                if (
                    progress >
                        0
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(
                                    progress /
                                        100f
                                )
                                .fillMaxHeight()
                                .background(
                                    accent,
                                    RoundedCornerShape(
                                        999.dp
                                    )
                                )
                    )
                }
            }

            task.message
                ?.takeIf {
                    it.isNotBlank()
                }
                ?.let {
                    message ->

                    Text(
                        message,
                        color =
                            TaskMuted,
                        fontSize =
                            11.sp,
                        lineHeight =
                            16.sp
                    )
                }

            if (
                task.retryLimit >
                    0
            ) {
                Text(
                    "${t("attempt")}: " +
                        "${task.attempt}/" +
                        "${task.retryLimit + 1}",
                    color =
                        TaskMuted,
                    fontSize =
                        10.sp
                )
            }

            if (
                task.canCancel ||
                task.canRetry
            ) {
                HorizontalDivider(
                    color =
                        Color(
                            0xFF2A3047
                        )
                )

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {
                    if (
                        task.canCancel
                    ) {
                        Button(
                            onClick = {
                                AppForgeTaskManager
                                    .cancel(
                                        task.id
                                    )
                            },
                            modifier =
                                Modifier.weight(
                                    1f
                                ),
                            colors =
                                ButtonDefaults
                                    .buttonColors(
                                        containerColor =
                                            Color(
                                                0xFF7D3440
                                            )
                                    )
                        ) {
                            Text(
                                t(
                                    "cancel"
                                )
                            )
                        }
                    }

                    if (
                        task.canRetry
                    ) {
                        OutlinedButton(
                            onClick = {
                                AppForgeTaskManager
                                    .retry(
                                        task.id
                                    )
                            },
                            modifier =
                                Modifier.weight(
                                    1f
                                )
                        ) {
                            Text(
                                t(
                                    "retry"
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
