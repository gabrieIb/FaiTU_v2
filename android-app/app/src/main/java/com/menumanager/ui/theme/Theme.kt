package com.menumanager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFFF8A65),
    onPrimary = Color(0xFF3B1707),
    primaryContainer = Color(0xFFFFD8C9),
    onPrimaryContainer = Color(0xFF331003),
    secondary = Color(0xFF5E6470),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1E4EB),
    onSecondaryContainer = Color(0xFF1B202A),
    tertiary = Color(0xFF4CB5AB),
    onTertiary = Color(0xFF00201C),
    background = Color(0xFFFFFBF7),
    onBackground = Color(0xFF1F1B18),
    surface = Color(0xFFFFFBF7),
    onSurface = Color(0xFF1F1B18),
    surfaceVariant = Color(0xFFF3E1D8),
    onSurfaceVariant = Color(0xFF53443C)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB59B),
    onPrimary = Color(0xFF4E1903),
    primaryContainer = Color(0xFF702503),
    onPrimaryContainer = Color(0xFFFFD8C9),
    secondary = Color(0xFFBFC7D4),
    onSecondary = Color(0xFF28303A),
    secondaryContainer = Color(0xFF3E4652),
    onSecondaryContainer = Color(0xFFE1E4EB),
    tertiary = Color(0xFF66D0C5),
    onTertiary = Color(0xFF003733),
    background = Color(0xFF181210),
    onBackground = Color(0xFFECE0DA),
    surface = Color(0xFF181210),
    onSurface = Color(0xFFECE0DA),
    surfaceVariant = Color(0xFF56433A),
    onSurfaceVariant = Color(0xFFE5CFC3)
)

@Composable
fun MenuManagerTheme(
    useDarkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}
