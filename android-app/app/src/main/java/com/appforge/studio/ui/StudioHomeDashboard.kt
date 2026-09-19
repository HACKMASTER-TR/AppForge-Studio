package com.appforge.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appforge.studio.io.SavedProject
import java.text.DateFormat
import java.util.Date

@Composable
internal fun HomeTopTitle() {
    Column {
        Row(
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Text(
                "AppForge ",
                fontWeight =
                    FontWeight.Black,
                fontSize =
                    21.sp
            )

            Text(
                "Studio",
                color =
                    MaterialTheme
                        .colorScheme
                        .primary,
                fontWeight =
                    FontWeight.Black,
                fontSize =
                    21.sp
            )
        }

        Text(
            "Cihazda üretim merkezi",
            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,
            fontSize =
                12.sp
        )
    }
}

@Composable
internal fun ModernHomeHero(
    projectCount: Int,
    buildCount: Int,
    proUnlocked: Boolean,
    onCreateQuick: () -> Unit,
    onCreateAdvanced: () -> Unit
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(28.dp)
                )
                .background(
                    Brush.linearGradient(
                        colors =
                            listOf(
                                MaterialTheme
                                    .colorScheme
                                    .primaryContainer,
                                MaterialTheme
                                    .colorScheme
                                    .secondaryContainer
                            )
                    )
                )
                .padding(22.dp)
    ) {
        Column(
            verticalArrangement =
                Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape =
                    RoundedCornerShape(999.dp),
                color =
                    MaterialTheme
                        .colorScheme
                        .surface
                        .copy(alpha = 0.72f)
            ) {
                Text(
                    "PROJE ÜRETİMİ",
                    modifier =
                        Modifier.padding(
                            horizontal = 12.dp,
                            vertical = 7.dp
                        ),
                    fontWeight =
                        FontWeight.Bold,
                    fontSize =
                        11.sp
                )
            }

            Text(
                "Fikrini projeye,\nprojeni uygulamaya dönüştür.",
                style =
                    MaterialTheme
                        .typography
                        .headlineSmall,
                fontWeight =
                    FontWeight.Black
            )

            Text(
                "Proje türünü seç. AppForge uygun derleme yöntemini otomatik belirlesin.",
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
            )

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {
                HomeStat(
                    projectCount.toString(),
                    "Proje",
                    Modifier.weight(1f)
                )

                HomeStat(
                    buildCount.toString(),
                    "Derleme",
                    Modifier.weight(1f)
                )

                HomeStat(
                    if (proUnlocked) {
                        "PRO"
                    } else {
                        "FREE"
                    },
                    "Plan",
                    Modifier.weight(1f)
                )
            }

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        10.dp
                    )
            ) {
                Button(
                    onClick =
                        onCreateQuick,
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(52.dp)
                ) {
                    Text(
                        "YENİ PROJE",
                        fontWeight =
                            FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick =
                        onCreateAdvanced,
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(52.dp)
                ) {
                    Text(
                        "GELİŞMİŞ AYARLAR",
                        fontSize =
                            11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeStat(
    value: String,
    label: String,
    modifier: Modifier
) {
    Surface(
        modifier = modifier,
        shape =
            RoundedCornerShape(18.dp),
        color =
            MaterialTheme
                .colorScheme
                .surface
                .copy(alpha = 0.70f)
    ) {
        Column(
            modifier =
                Modifier.padding(
                    horizontal = 10.dp,
                    vertical = 12.dp
                ),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {
            Text(
                value,
                fontWeight =
                    FontWeight.Black,
                fontSize =
                    16.sp,
                maxLines = 1
            )

            Text(
                label,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant,
                fontSize =
                    11.sp
            )
        }
    }
}

@Composable
internal fun ModernHomeActionRow(
    firstBadge: String,
    firstTitle: String,
    firstSubtitle: String,
    firstClick: () -> Unit,
    secondBadge: String,
    secondTitle: String,
    secondSubtitle: String,
    secondClick: () -> Unit
) {
    Row(
        modifier =
            Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(12.dp)
    ) {
        ModernHomeActionCard(
            badge = firstBadge,
            title = firstTitle,
            subtitle = firstSubtitle,
            onClick = firstClick,
            modifier =
                Modifier.weight(1f)
        )

        ModernHomeActionCard(
            badge = secondBadge,
            title = secondTitle,
            subtitle = secondSubtitle,
            onClick = secondClick,
            modifier =
                Modifier.weight(1f)
        )
    }
}

@Composable
private fun ModernHomeActionCard(
    badge: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier
) {
    ElevatedCard(
        modifier =
            modifier.clickable {
                onClick()
            },
        shape =
            RoundedCornerShape(22.dp),
        elevation =
            CardDefaults
                .elevatedCardElevation(
                    defaultElevation = 3.dp
                )
    ) {
        Column(
            modifier =
                Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(9.dp)
        ) {
            Surface(
                shape =
                    RoundedCornerShape(999.dp),
                color =
                    MaterialTheme
                        .colorScheme
                        .primaryContainer
            ) {
                Text(
                    badge,
                    modifier =
                        Modifier.padding(
                            horizontal = 9.dp,
                            vertical = 5.dp
                        ),
                    fontWeight =
                        FontWeight.Bold,
                    fontSize =
                        9.sp
                )
            }

            Text(
                title,
                fontWeight =
                    FontWeight.Bold,
                maxLines = 1,
                overflow =
                    TextOverflow.Ellipsis
            )

            Text(
                subtitle,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant,
                fontSize =
                    12.sp,
                maxLines = 2,
                overflow =
                    TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun OwnerAdminCard(
    terminalTitle: String,
    onClick: () -> Unit,
    onAdminClick: () -> Unit
) {
    ElevatedCard(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier =
                Modifier.padding(18.dp),
            verticalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Yönetici çalışma alanı",
                fontWeight =
                    FontWeight.Bold
            )

            Text(
                "Gelişmiş terminal ve yönetim araçlarına buradan ulaş.",
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant,
                fontSize =
                    13.sp
            )

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onClick,
                    modifier =
                        Modifier.weight(1f)
                ) {
                    Text(terminalTitle)
                }

                Button(
                    onClick = onAdminClick,
                    modifier =
                        Modifier.weight(1f)
                ) {
                    Text("Admin Paneli")
                }
            }
        }
    }
}

@Composable
internal fun HomeSectionTitle(
    title: String,
    subtitle: String
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(3.dp)
    ) {
        Text(
            title,
            style =
                MaterialTheme
                    .typography
                    .titleMedium,
            fontWeight =
                FontWeight.Black
        )

        Text(
            subtitle,
            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,
            fontSize =
                12.sp
        )
    }
}

@Composable
internal fun EmptyProjectCard(
    text: String
) {
    ElevatedCard(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier =
                Modifier.padding(22.dp),
            verticalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Henüz proje yok",
                fontWeight =
                    FontWeight.Bold
            )

            Text(
                text,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun ModernProjectCard(
    project: SavedProject,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    onClick()
                },
        shape =
            RoundedCornerShape(22.dp)
    ) {
        Row(
            modifier =
                Modifier.padding(17.dp),
            verticalAlignment =
                Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape =
                    RoundedCornerShape(16.dp),
                color =
                    MaterialTheme
                        .colorScheme
                        .primaryContainer
            ) {
                Text(
                    project
                        .name
                        .trim()
                        .take(1)
                        .uppercase()
                        .ifBlank {
                            "A"
                        },
                    modifier =
                        Modifier.padding(
                            horizontal = 15.dp,
                            vertical = 11.dp
                        ),
                    fontWeight =
                        FontWeight.Black
                )
            }

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {
                Text(
                    project.name,
                    fontWeight =
                        FontWeight.Bold,
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis
                )

                Text(
                    project.packageName,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                    fontSize =
                        12.sp,
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis
                )

                Text(
                    DateFormat
                        .getDateTimeInstance(
                            DateFormat.SHORT,
                            DateFormat.SHORT
                        )
                        .format(
                            Date(project.updatedAt)
                        ),
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                    fontSize =
                        11.sp
                )
            }

            Text(
                "Aç",
                color =
                    MaterialTheme
                        .colorScheme
                        .primary,
                fontWeight =
                    FontWeight.Bold
            )
        }
    }
}

@Composable
internal fun HomeToolRow(
    firstLabel: String,
    firstClick: () -> Unit,
    secondLabel: String,
    secondClick: () -> Unit
) {
    Row(
        modifier =
            Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = firstClick,
            modifier =
                Modifier.weight(1f)
        ) {
            Text(
                firstLabel,
                maxLines = 1,
                overflow =
                    TextOverflow.Ellipsis
            )
        }

        OutlinedButton(
            onClick = secondClick,
            modifier =
                Modifier.weight(1f)
        ) {
            Text(
                secondLabel,
                maxLines = 1,
                overflow =
                    TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun ModernProCard(
    proUnlocked: Boolean,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    onClick()
                },
        shape =
            RoundedCornerShape(22.dp)
    ) {
        Row(
            modifier =
                Modifier.padding(18.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Column(
                modifier =
                    Modifier.weight(1f)
            ) {
                Text(
                    if (proUnlocked) {
                        "AppForge Pro aktif"
                    } else {
                        "AppForge Pro"
                    },
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    if (proUnlocked) {
                        "Pro özelliklerin kullanıma hazır."
                    } else {
                        "Planını ve özelliklerini görüntüle."
                    },
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                    fontSize =
                        13.sp
                )
            }

            Text(
                "Aç",
                color =
                    MaterialTheme
                        .colorScheme
                        .primary,
                fontWeight =
                    FontWeight.Bold
            )
        }
    }
}
