package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val StreamBoxColorScheme =
  darkColorScheme(
    primary = ElectricCrimson,
    onPrimary = OnElectricCrimson,
    primaryContainer = ElectricCrimsonContainer,
    onPrimaryContainer = OnElectricCrimsonContainer,
    secondary = BroadcastCyan,
    onSecondary = OnBroadcastCyan,
    secondaryContainer = BroadcastCyanContainer,
    onSecondaryContainer = OnBroadcastCyanContainer,
    tertiary = AmberGold,
    onTertiary = OnAmberGold,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = SurfaceElevated,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondary,
    surfaceContainerHigh = SurfaceHighlight,
    outline = OutlineSubtle,
    error = ErrorRose,
    onError = ObsidianBg,
  )

private val StreamBoxShapes =
  Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )

@Composable
fun StreamBoxTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = StreamBoxColorScheme,
    typography = StreamBoxTypography,
    shapes = StreamBoxShapes,
    content = content,
  )
}
