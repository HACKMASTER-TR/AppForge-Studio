package com.appforge.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val UnifiedAgentCardBackground = Color(0xFF111C20)
private val UnifiedAgentAccentStart = Color(0xFF6EE7B7)
private val UnifiedAgentAccentEnd = Color(0xFF60A5FA)
private val UnifiedAgentSecondaryText = Color(0xFFB8C2CC)

@Composable
internal fun UnifiedAgentHomeEntryCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = UnifiedAgentCardBackground
        ),
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            UnifiedAgentAccentStart.copy(alpha = 0.12f),
                            UnifiedAgentAccentEnd.copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    )
                )
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "✦",
                color = UnifiedAgentAccentStart,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "AI ile Uygulama / Oyun Oluştur",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Prompt → Yerel AI → Uygulama/Oyun → Cihaz Build",
                    color = UnifiedAgentSecondaryText,
                    fontSize = 12.sp
                )
            }

            Text(
                text = "→",
                color = UnifiedAgentAccentEnd,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
