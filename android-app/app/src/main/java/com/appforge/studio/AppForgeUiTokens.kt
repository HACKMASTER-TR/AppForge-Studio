package com.appforge.studio

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal val Bg =
    Color(0xFF050B18)

internal val Card2 =
    Color(0xFF0C1628)

internal val CardRaised =
    Color(0xFF111E34)

internal val Accent =
    Color(0xFF43D7FF)

internal val AccentBlue =
    Color(0xFF3E8CFF)

internal val AccentViolet =
    Color(0xFF7A5CFF)

internal val AccentGreen =
    Color(0xFF41D6A3)

internal val AccentOrange =
    Color(0xFFFFB454)

internal val TextPrimary =
    Color(0xFFF4F8FF)

internal val TextSecondary =
    Color(0xFF8FA6C8)

private val AppForgeDarkColors =
    darkColorScheme(
        primary = Accent,
        onPrimary = Color(0xFF00131D),

        primaryContainer =
            Color(0xFF123764),
        onPrimaryContainer =
            TextPrimary,

        secondary =
            AccentViolet,
        onSecondary =
            Color(0xFF0C071D),

        secondaryContainer =
            Color(0xFF30205C),
        onSecondaryContainer =
            TextPrimary,

        tertiary =
            AccentGreen,

        background =
            Bg,
        onBackground =
            TextPrimary,

        surface =
            Card2,
        onSurface =
            TextPrimary,

        surfaceVariant =
            CardRaised,
        onSurfaceVariant =
            TextSecondary,

        outline =
            Color(0xFF294263),

        error =
            Color(0xFFFF6B82)
    )

private val AppForgeShapes =
    Shapes(
        small =
            RoundedCornerShape(14.dp),
        medium =
            RoundedCornerShape(20.dp),
        large =
            RoundedCornerShape(28.dp)
    )

@Composable
internal fun AppForgeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme =
            AppForgeDarkColors,
        shapes =
            AppForgeShapes,
        content =
            content
    )
}
