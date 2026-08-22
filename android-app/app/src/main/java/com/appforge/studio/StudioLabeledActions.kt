package com.appforge.studio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun LabeledActionButton(
    icon: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier =
            modifier
                .semantics {
                    contentDescription = label
                }
                .clickable(
                    onClick = onClick
                )
                .padding(
                    horizontal = 7.dp,
                    vertical = 4.dp
                ),
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {
        Text(
            text = icon,
            fontSize = 21.sp,
            maxLines = 1
        )

        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight =
                FontWeight.Medium,
            color =
                Color(0xFFA5ADB7),
            maxLines = 1,
            overflow =
                TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun BuilderShortcutBar(
    onHome: () -> Unit,
    onHistory: () -> Unit,
    onTemplates: () -> Unit,
    onAi: () -> Unit,
    onSettings: () -> Unit,
    onAccount: () -> Unit
) {
    val scroll =
        rememberScrollState()

    Surface(
        color =
            Color(0xFF0C1118),
        shape =
            RoundedCornerShape(
                18.dp
            ),
        modifier =
            Modifier.padding(
                horizontal = 10.dp,
                vertical = 4.dp
            )
    ) {
        Row(
            modifier =
                Modifier
                    .horizontalScroll(
                        scroll
                    )
                    .padding(
                        horizontal = 5.dp,
                        vertical = 3.dp
                    ),
            horizontalArrangement =
                Arrangement.spacedBy(
                    3.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            LabeledActionButton(
                icon = "⌂",
                label = "Ana Sayfa",
                onClick = onHome
            )

            LabeledActionButton(
                icon = "🧾",
                label = "Geçmiş",
                onClick = onHistory
            )

            LabeledActionButton(
                icon = "🧩",
                label = "Şablonlar",
                onClick = onTemplates
            )

            LabeledActionButton(
                icon = "✨",
                label = "Yerel AI",
                onClick = onAi
            )

            LabeledActionButton(
                icon = "⚙",
                label = "Ayarlar",
                onClick = onSettings
            )

            LabeledActionButton(
                icon = "👤",
                label = "Hesap",
                onClick = onAccount
            )
        }
    }
}
