package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val VoxDarkColorScheme = darkColorScheme(
    primary = ElectricIndigoBright,
    onPrimary = Color(0xFF041C3A),
    primaryContainer = ElectricIndigoContainer,
    onPrimaryContainer = Color(0xFFD6EBFF),
    secondary = SkyBlueGlow,
    onSecondary = Color(0xFF031F3D),
    secondaryContainer = VoiceCyanContainer,
    onSecondaryContainer = Color(0xFFCDE9FF),
    tertiary = ModAmber,
    onTertiary = Color(0xFF2B1B00),
    tertiaryContainer = ModAmberContainer,
    onTertiaryContainer = Color(0xFFFFE2A8),
    error = LiveCoral,
    onError = Color.White,
    errorContainer = LiveCoralContainer,
    onErrorContainer = Color(0xFFFFD9DF),
    background = StudioObsidian,
    onBackground = TextPrimaryDark,
    surface = StudioMidnight,
    onSurface = TextPrimaryDark,
    surfaceVariant = StudioSlate,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = StudioObsidian,
    surfaceContainerLow = StudioMidnight,
    surfaceContainer = StudioSlate,
    surfaceContainerHigh = StudioElevated,
    surfaceContainerHighest = Color(0xFF253F6E),
    outline = StudioOutline,
    outlineVariant = StudioOutlineSubtle
)

private val VoxLightColorScheme = lightColorScheme(
    primary = SnapBlue,
    onPrimary = PureWhite,
    primaryContainer = BlueMist,
    onPrimaryContainer = DeepBlueAccent,
    secondary = RoyalBlue,
    onSecondary = PureWhite,
    secondaryContainer = VoiceTealContainerLight,
    onSecondaryContainer = DeepBlueAccent,
    tertiary = Color(0xFFC47A00),
    onTertiary = PureWhite,
    tertiaryContainer = Color(0xFFFFEBC2),
    onTertiaryContainer = Color(0xFF3E2500),
    error = LiveCoralLight,
    onError = PureWhite,
    errorContainer = LiveCoralContainerLight,
    onErrorContainer = Color(0xFF5A0016),
    background = IceWhite,
    onBackground = InkNavy,
    surface = PureWhite,
    onSurface = InkNavy,
    surfaceVariant = BlueSurfaceTint,
    onSurfaceVariant = SlateBlueText,
    surfaceContainerLowest = PureWhite,
    surfaceContainerLow = Color(0xFFF8FBFF),
    surfaceContainer = IceWhite,
    surfaceContainerHigh = BlueSurfaceTint,
    surfaceContainerHighest = BlueMist,
    outline = BlueOutline,
    outlineVariant = BlueOutlineSubtle
)

val VoxStageShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun VoxStageTheme(
    darkTheme: Boolean = false,
    forceDarkStatusBarBackground: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) VoxDarkColorScheme else VoxLightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme && !forceDarkStatusBarBackground
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = VoxStageShapes,
        content = content
    )
}
