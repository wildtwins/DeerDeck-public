package com.wildtwins.deerdeck.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import com.wildtwins.deerdeck.data.settings.AppThemeMode

private val LightColors = lightColorScheme(
    primary = BestBuyBlue,
    onPrimary = Color.White,
    secondary = BrightBlue,
    onSecondary = Color.White,
    tertiary = Pine,
    onTertiary = Color.White,
    background = PanelBlue,
    onBackground = DeepBlue,
    surface = Color(0xFFFFFFFF),
    onSurface = DeepBlue,
    primaryContainer = SoftBlue,
    onPrimaryContainer = DeepBlue,
    secondaryContainer = Color(0xFFD8E8FF),
    onSecondaryContainer = DeepBlue,
    surfaceVariant = LineBlue,
    onSurfaceVariant = SlateBlue,
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FC2FF),
    onPrimary = Color(0xFF00315F),
    secondary = Color(0xFFA8C8FF),
    onSecondary = Color(0xFF173153),
    tertiary = Color(0xFFA9D5A7),
    onTertiary = Color(0xFF183817),
    background = Color(0xFF101820),
    onBackground = Color(0xFFE4ECF7),
    surface = Color(0xFF182331),
    onSurface = Color(0xFFE4ECF7),
    primaryContainer = Color(0xFF004A88),
    onPrimaryContainer = Color(0xFFD7E8FF),
    secondaryContainer = Color(0xFF243B5C),
    onSecondaryContainer = Color(0xFFD7E8FF),
    surfaceVariant = Color(0xFF29374A),
    onSurfaceVariant = Color(0xFFC3D3E6),
    error = Color(0xFFFFB4AB),
)

@Composable
fun DeerDeckTheme(
    themeMode: AppThemeMode = AppThemeMode.System,
    content: @Composable () -> Unit,
) {
    val useDarkTheme = when (themeMode) {
        AppThemeMode.System -> isSystemInDarkTheme()
        AppThemeMode.Light -> false
        AppThemeMode.Dark -> true
    }

    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
