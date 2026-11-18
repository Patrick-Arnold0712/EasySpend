package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Immutable
data class EasySpendSemanticColors(
    val isDark: Boolean,
    val safe: Color,
    val safeContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val hairline: Color,
    val trackBackground: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val heroTextPrimary: Color,
    val heroTextSecondary: Color,
    val heroDivider: Color,
    val heroPillBackground: Color
) {
    val cardBorder: BorderStroke
        get() = BorderStroke(0.5.dp, hairline)
}

private val LightSemanticColors = EasySpendSemanticColors(
    isDark = false,
    safe = SafeGreen,
    safeContainer = SafeGreenContainer,
    warning = WarningAmber,
    warningContainer = WarningAmberContainer,
    danger = AlertRed,
    dangerContainer = AlertRedContainer,
    hairline = OutlineSubtle,
    trackBackground = Color(0xFFE5E5EA),
    heroStart = Color(0xFF1F745E),
    heroEnd = Color(0xFF2C8F74),
    heroTextPrimary = Color.White,
    heroTextSecondary = Color(0xFFD5F2E8),
    heroDivider = Color.White.copy(alpha = 0.18f),
    heroPillBackground = Color.White.copy(alpha = 0.16f)
)

private val DarkSemanticColors = EasySpendSemanticColors(
    isDark = true,
    safe = SafeGreenDark,
    safeContainer = SafeGreenContainerDark,
    warning = WarningAmberDark,
    warningContainer = WarningAmberContainerDark,
    danger = AlertRedDark,
    dangerContainer = AlertRedContainerDark,
    hairline = DarkOutline,
    trackBackground = Color(0xFF32323A),
    heroStart = Color(0xFF19352E),
    heroEnd = Color(0xFF21463C),
    heroTextPrimary = Color(0xFFF2F7F5),
    heroTextSecondary = Color(0xFFB5D8CC),
    heroDivider = Color.White.copy(alpha = 0.12f),
    heroPillBackground = Color.White.copy(alpha = 0.10f)
)

val LocalEasySpendColors = staticCompositionLocalOf { LightSemanticColors }

val MaterialTheme.semanticColors: EasySpendSemanticColors
    @Composable
    get() = LocalEasySpendColors.current

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldDarkPrimary,
    onPrimary = EmeraldDarkOnPrimary,
    primaryContainer = EmeraldDarkContainer,
    onPrimaryContainer = EmeraldDarkOnContainer,
    secondary = AmberDarkSecondary,
    onSecondary = AmberDarkOnSecondary,
    secondaryContainer = AmberDarkContainer,
    onSecondaryContainer = AmberDarkOnContainer,
    tertiary = SlateContainer,
    onTertiary = SlateOnContainer,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceContainer,
    onSurfaceVariant = DarkInkMuted,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    error = AlertRedDark,
    errorContainer = AlertRedContainerDark
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = EmeraldOnPrimary,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = EmeraldOnContainer,
    secondary = AmberSecondary,
    onSecondary = AmberOnSecondary,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = AmberOnContainer,
    tertiary = SlateTertiary,
    onTertiary = SlateOnTertiary,
    tertiaryContainer = SlateContainer,
    onTertiaryContainer = SlateOnContainer,
    background = AlabasterBackground,
    onBackground = InkOnSurface,
    surface = AlabasterSurface,
    onSurface = InkOnSurface,
    surfaceVariant = SurfaceContainer,
    onSurfaceVariant = InkMuted,
    surfaceContainerLow = SurfaceContainerLow,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    outline = OutlineSubtle,
    outlineVariant = OutlineSubtle,
    error = AlertRed,
    errorContainer = AlertRedContainer
)

val EasySpendShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun EasySpendTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val semantic = if (darkTheme) DarkSemanticColors else LightSemanticColors

    CompositionLocalProvider(LocalEasySpendColors provides semantic) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = EasySpendShapes,
            content = content
        )
    }
}
