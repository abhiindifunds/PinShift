package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = DeepNavy,
    primaryContainer = ElectricCyanContainer,
    onPrimaryContainer = ElectricCyan,
    secondary = SignalCoral,
    onSecondary = DeepNavy,
    secondaryContainer = SignalCoralContainer,
    onSecondaryContainer = SignalCoral,
    tertiary = TelemetryAmber,
    onTertiary = DeepNavy,
    tertiaryContainer = TelemetryAmberContainer,
    onTertiaryContainer = TelemetryAmber,
    background = DeepNavy,
    onBackground = TextPrimaryDark,
    surface = SlateSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = SlateSurfaceElevated,
    onSurfaceVariant = TextSecondaryDark,
    outline = SlateBorder,
    outlineVariant = SlateBorder.copy(alpha = 0.5f),
    error = SignalCoral,
    onError = DeepNavy,
    errorContainer = SignalCoralContainer,
    onErrorContainer = SignalCoral
)

private val LightColorScheme = lightColorScheme(
    primary = DeepTeal,
    onPrimary = CrispWhite,
    primaryContainer = DeepTealContainer,
    onPrimaryContainer = DeepTeal,
    secondary = VividCoral,
    onSecondary = CrispWhite,
    secondaryContainer = VividCoralContainer,
    onSecondaryContainer = VividCoral,
    tertiary = TelemetryAmber,
    onTertiary = CrispWhite,
    tertiaryContainer = TelemetryAmberContainer.copy(alpha = 0.15f),
    onTertiaryContainer = TextPrimaryLight,
    background = AlabasterBg,
    onBackground = TextPrimaryLight,
    surface = CrispWhite,
    onSurface = TextPrimaryLight,
    surfaceVariant = SoftSlateSurface,
    onSurfaceVariant = TextSecondaryLight,
    outline = LightBorder,
    outlineVariant = LightBorder.copy(alpha = 0.6f),
    error = VividCoral,
    onError = CrispWhite,
    errorContainer = VividCoralContainer,
    onErrorContainer = VividCoral
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun PinShiftTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
