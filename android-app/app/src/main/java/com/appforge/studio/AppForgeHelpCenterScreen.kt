@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.appforge.studio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appforge.studio.ai.AppForgeKnowledgeBase

@Composable
internal fun AppForgeHelpCenterScreen(
    languageCode: String,
    onBack: () -> Unit
) {
    val helpConfiguration =
        LocalConfiguration.current

    val helpScreenWidthDp =
        helpConfiguration.screenWidthDp

    val helpScreenHeightDp =
        helpConfiguration.screenHeightDp

    val helpCompact =
        helpScreenWidthDp < 380

    val helpTablet =
        minOf(
            helpScreenWidthDp,
            helpScreenHeightDp
        ) >= 600

    val helpWide =
        helpScreenWidthDp >= 600

    val helpContentMaxWidth =
        if (helpWide) 900.dp else 10000.dp

    val helpHorizontalPadding =
        when {
            helpCompact -> 10.dp
            helpTablet -> 28.dp
            else -> 16.dp
        }

    var query by
        remember {
            mutableStateOf(
                ""
            )
        }

    var selectedCategory by
        remember {
            mutableStateOf(
                "Tümü"
            )
        }

    val allArticles =
        remember {
            AppForgeKnowledgeBase
                .helpArticles()
        }

    val categories =
        remember(
            allArticles
        ) {
            listOf(
                "Tümü"
            ) +
                AppForgeKnowledgeBase
                    .helpCategories()
        }

    val matchedArticles =
        remember(
            query,
            selectedCategory,
            allArticles
        ) {
            val source =
                if (
                    query
                        .trim()
                        .isBlank()
                ) {
                    allArticles
                } else {
                    AppForgeKnowledgeBase
                        .searchHelp(
                            query =
                                query,
                            maxResults =
                                100
                        )
                }

            source.filter {
                selectedCategory ==
                    "Tümü" ||
                it.category ==
                    selectedCategory
            }
        }

    Column(
        modifier =
            Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        "Yardım Merkezi",
                        fontWeight =
                            FontWeight.Bold
                    )

                    if (!helpCompact) {
                        Text(
                            "${allArticles.size} AppForge yardım konusu • cihaz içinde arama",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(
                    onClick =
                        onBack
                ) {
                    Text(
                        "←"
                    )
                }
            },
            colors =
                TopAppBarDefaults
                    .topAppBarColors(
                        containerColor =
                            Bg
                    )
        )

        LazyColumn(
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = helpContentMaxWidth)
                    .fillMaxWidth().fillMaxSize(),
            contentPadding =
                PaddingValues(
                    horizontal = helpHorizontalPadding,
                    vertical =
                        if (helpCompact) 10.dp else 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(if (helpCompact) 9.dp else 14.dp)
        ) {
            item {
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                Card2
                        ),
                    shape =
                        RoundedCornerShape(if (helpCompact) 18.dp else 22.dp)
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(if (helpCompact) 12.dp else 16.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                12.dp
                            )
                    ) {
                        Text(
                            "🔎 AppForge hakkında ara",
                            fontWeight =
                                FontWeight.Bold,
                            fontSize =
                                if (helpCompact) 18.sp else 20.sp
                        )

                        Text(
                            "AppForge, Terminal, Excel Tools, VideoForge, APK, AAB, keystore, Billing, Firebase, Play Store veya başka bir özelliği yaz.",
                            color =
                                TextSecondary,
                            lineHeight =
                                19.sp
                        )

                        OutlinedTextField(
                            value =
                                query,
                            onValueChange = {
                                query =
                                    it
                            },
                            modifier =
                                Modifier.fillMaxWidth(),
                            singleLine =
                                true,
                            label = {
                                Text(
                                    "Yardım konusu ara"
                                )
                            },
                            placeholder = {
                                Text(
                                    "Örn. keystore, APK, Billing, Python"
                                )
                            },
                            trailingIcon = {
                                if (
                                    query.isNotBlank()
                                ) {
                                    TextButton(
                                        onClick = {
                                            query =
                                                ""
                                        }
                                    ) {
                                        Text(
                                            "Temizle"
                                        )
                                    }
                                }
                            }
                        )

                        Text(
                            if (
                                query.isBlank()
                            ) {
                                "${matchedArticles.size} konu gösteriliyor"
                            } else {
                                "\"$query\" için ${matchedArticles.size} sonuç"
                            },
                            color =
                                Accent,
                            fontSize =
                                12.sp,
                            fontWeight =
                                FontWeight.Bold
                        )
                    }
                }
            }

            item {
                LazyRow(
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {
                    items(
                        categories,
                        key = {
                            it
                        }
                    ) {
                        category ->
                        FilterChip(
                            selected =
                                selectedCategory ==
                                    category,
                            onClick = {
                                selectedCategory =
                                    category
                            },
                            label = {
                                Text(
                                    category
                                )
                            }
                        )
                    }
                }
            }

            if (
                matchedArticles.isEmpty()
            ) {
                item {
                    Card(
                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    Card2
                            ),
                        shape =
                            RoundedCornerShape(if (helpCompact) 15.dp else 18.dp)
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(if (helpCompact) 12.dp else 16.dp),
                            verticalArrangement =
                                Arrangement.spacedBy(
                                    8.dp
                                )
                        ) {
                            Text(
                                "Sonuç bulunamadı",
                                fontWeight =
                                    FontWeight.Bold
                            )

                            Text(
                                "Farklı bir kelime dene. Örn. APK, WebView, Firebase, Pro, build, imza veya Play Store.",
                                color =
                                    TextSecondary
                            )
                        }
                    }
                }
            } else {
                items(
                    matchedArticles,
                    key = {
                        it.title
                    }
                ) {
                    article ->
                    Card(
                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    Card2
                            ),
                        shape =
                            RoundedCornerShape(if (helpCompact) 17.dp else 20.dp),
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(if (helpCompact) 12.dp else 16.dp),
                            verticalArrangement =
                                Arrangement.spacedBy(
                                    8.dp
                                )
                        ) {
                            AssistChip(
                                onClick = {
                                    selectedCategory =
                                        article.category
                                },
                                label = {
                                    Text(
                                        article.category
                                    )
                                }
                            )

                            Text(
                                article.title,
                                fontWeight =
                                    FontWeight.Bold,
                                fontSize =
                                    if (helpCompact) 16.sp else 18.sp
                            )

                            Text(
                                article.text,
                                color =
                                    TextSecondary,
                                lineHeight =
                                    20.sp
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                Color(0xFF102037)
                        ),
                    shape =
                        RoundedCornerShape(if (helpCompact) 17.dp else 20.dp)
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(if (helpCompact) 12.dp else 16.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                7.dp
                            )
                    ) {
                        Text(
                            "✨ Yerel AI ile devam et",
                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            "Yardım Merkezi sabit ve doğrulanmış AppForge bilgilerini anında gösterir. Daha özel proje sorularında ana ekrandaki Yerel AI Asistanı'nı kullanabilirsin.",
                            color =
                                TextSecondary,
                            lineHeight =
                                19.sp
                        )
                    }
                }
            }
        }
    }
}
