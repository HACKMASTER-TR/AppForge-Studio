package com.appforge.studio

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import kotlin.math.roundToInt
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal val Bg =
    Color(0xFF000000)

internal val Card2 =
    Color(0xFF000000)

internal val CardRaised =
    Color(0xFF000000)

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
            Color(0xFF000000),
        onPrimaryContainer =
            TextPrimary,

        secondary =
            AccentViolet,
        onSecondary =
            Color(0xFF0C071D),

        secondaryContainer =
            Color(0xFF000000),
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

private val AppForgeTypography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = FontFamily.SansSerif),
        displayMedium = base.displayMedium.copy(fontFamily = FontFamily.SansSerif),
        displaySmall = base.displaySmall.copy(fontFamily = FontFamily.SansSerif),
        headlineLarge = base.headlineLarge.copy(fontFamily = FontFamily.SansSerif),
        headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.SansSerif),
        headlineSmall = base.headlineSmall.copy(fontFamily = FontFamily.SansSerif),
        titleLarge = base.titleLarge.copy(fontFamily = FontFamily.SansSerif),
        titleMedium = base.titleMedium.copy(fontFamily = FontFamily.SansSerif),
        titleSmall = base.titleSmall.copy(fontFamily = FontFamily.SansSerif),
        bodyLarge = base.bodyLarge.copy(fontFamily = FontFamily.SansSerif),
        bodyMedium = base.bodyMedium.copy(fontFamily = FontFamily.SansSerif),
        bodySmall = base.bodySmall.copy(fontFamily = FontFamily.SansSerif),
        labelLarge = base.labelLarge.copy(fontFamily = FontFamily.SansSerif),
        labelMedium = base.labelMedium.copy(fontFamily = FontFamily.SansSerif),
        labelSmall = base.labelSmall.copy(fontFamily = FontFamily.SansSerif)
    )
}

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
    val physicalDensity = LocalDensity.current
    val sourceConfiguration = LocalConfiguration.current
    val compactPortrait = sourceConfiguration.orientation == Configuration.ORIENTATION_PORTRAIT &&
        sourceConfiguration.screenWidthDp in 250..599
    val targetWidthDp = if(compactPortrait) 412 else sourceConfiguration.screenWidthDp.coerceAtLeast(1)
    val displayScale = sourceConfiguration.screenWidthDp.coerceAtLeast(1).toFloat() / targetWidthDp
    val pinnedDensity = Density(physicalDensity.density * displayScale, 1f)
    val pinnedConfiguration = Configuration(sourceConfiguration).apply {
        if(compactPortrait) {
            screenWidthDp = targetWidthDp
            screenHeightDp = (sourceConfiguration.screenHeightDp / displayScale).roundToInt()
        }
    }
    // All Compose typography and compact-breakpoints use the SAME S24 layout baseline.
    CompositionLocalProvider(
        LocalDensity provides pinnedDensity,
        LocalConfiguration provides pinnedConfiguration
    ) {
    MaterialTheme(
        colorScheme =
            AppForgeDarkColors,
        typography = AppForgeTypography,
        shapes =
            AppForgeShapes,
        content =
            content
    )
    }
}
