package com.nexcheck.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Blue600,
    onPrimary = Color.White,
    primaryContainer = Blue100,
    onPrimaryContainer = Blue900,
    secondary = Slate700,
    onSecondary = Color.White,
    secondaryContainer = Slate100,
    onSecondaryContainer = Slate800,
    tertiary = Color(0xFF0D9488),
    background = Slate50,
    onBackground = Slate900,
    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate500,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Slate50,
    surfaceContainerHigh = Slate100,
    surfaceContainerHighest = Slate200,
    outline = Slate300,
    outlineVariant = Slate200,
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    inverseSurface = Slate900,
    inverseOnSurface = Slate50,
    scrim = Color.Black
)

private val DarkColors = darkColorScheme(
    primary = Blue400,
    onPrimary = Color(0xFF0A1A40),
    primaryContainer = Color(0xFF1B3170),
    onPrimaryContainer = Blue100,
    secondary = Slate300,
    onSecondary = Slate900,
    secondaryContainer = Night700,
    onSecondaryContainer = Slate200,
    tertiary = Color(0xFF2DD4BF),
    background = Night900,
    onBackground = Slate100,
    surface = Night800,
    onSurface = Slate100,
    surfaceVariant = Night700,
    onSurfaceVariant = Slate400,
    surfaceContainerLowest = Night900,
    surfaceContainerLow = Night800,
    surfaceContainer = Night800,
    surfaceContainerHigh = Night700,
    surfaceContainerHighest = Night600,
    outline = Color(0xFF3A4A6B),
    outlineVariant = Night600,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF3B1418),
    onErrorContainer = Color(0xFFFECACA),
    inverseSurface = Slate100,
    inverseOnSurface = Slate900,
    scrim = Color.Black
)

val NexShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun NexCheckTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Ícones da barra de status/navegação: escuros no tema claro, claros no escuro
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    // Sem cor dinâmica (Material You): o app mantém a identidade azul da marca
    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatusColors else LightStatusColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Typography,
            shapes = NexShapes,
            content = content
        )
    }
}

// Acesso rápido: NexTheme.status.success etc.
object NexTheme {
    val status: StatusColors
        @Composable @ReadOnlyComposable get() = LocalStatusColors.current
}
