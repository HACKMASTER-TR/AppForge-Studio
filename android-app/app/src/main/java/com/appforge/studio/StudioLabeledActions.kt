package com.appforge.studio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
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
    val screenWidthDp =
        LocalConfiguration.current.screenWidthDp

    val compact =
        screenWidthDp < 380

    val expanded =
        screenWidthDp >= 600

    val horizontalPadding =
        when {
            compact -> 4.dp
            expanded -> 10.dp
            else -> 7.dp
        }

    val verticalPadding =
        if (compact) {
            3.dp
        } else {
            4.dp
        }

    val iconSize =
        when {
            compact -> 19.sp
            expanded -> 23.sp
            else -> 21.sp
        }

    val labelSize =
        when {
            compact -> 8.sp
            expanded -> 10.sp
            else -> 9.sp
        }

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
                    horizontal = horizontalPadding,
                    vertical = verticalPadding
                ),
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {
        Text(
            text = icon,
            fontSize = iconSize,
            maxLines = 1
        )

        Text(
            text = label,
            fontSize = labelSize,
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

    val screenWidthDp =
        LocalConfiguration.current.screenWidthDp

    val compact =
        screenWidthDp < 380

    val expanded =
        screenWidthDp >= 600

    val outerHorizontalPadding =
        when {
            compact -> 6.dp
            expanded -> 18.dp
            else -> 10.dp
        }

    val innerHorizontalPadding =
        if (compact) {
            2.dp
        } else {
            5.dp
        }

    val itemSpacing =
        if (compact) {
            1.dp
        } else {
            3.dp
        }

    Surface(
        color =
            Color(0xFF0C1118),
        shape =
            RoundedCornerShape(
                if (compact) {
                    16.dp
                } else {
                    18.dp
                }
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = outerHorizontalPadding,
                    vertical = 4.dp
                )
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (expanded) {
                            Modifier
                        } else {
                            Modifier.horizontalScroll(
                                scroll
                            )
                        }
                    )
                    .padding(
                        horizontal = innerHorizontalPadding,
                        vertical = 3.dp
                    ),
            horizontalArrangement =
                if (expanded) {
                    Arrangement.SpaceEvenly
                } else {
                    Arrangement.spacedBy(
                        itemSpacing
                    )
                },
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
