package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val SolarObsidianColorScheme = darkColorScheme(
    primary = SolarAmber,
    onPrimary = OnSolarAmber,
    primaryContainer = SolarAmberContainer,
    onPrimaryContainer = OnSolarAmberContainer,
    secondary = TelemetryCyan,
    onSecondary = OnTelemetryCyan,
    secondaryContainer = TelemetryCyanContainer,
    onSecondaryContainer = OnTelemetryCyanContainer,
    tertiary = RootEmerald,
    onTertiary = OnRootEmerald,
    tertiaryContainer = RootEmeraldContainer,
    onTertiaryContainer = OnRootEmeraldContainer,
    background = ObsidianCanvas,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = OutlineSubtle,
    error = AlertCrimson,
    errorContainer = AlertCrimsonContainer
)

val SolisShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SolarObsidianColorScheme,
        typography = Typography,
        shapes = SolisShapes,
        content = content
    )
}
