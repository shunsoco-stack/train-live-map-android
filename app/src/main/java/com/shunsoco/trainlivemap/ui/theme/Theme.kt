package com.shunsoco.trainlivemap.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = TokaidoOrange,
    onPrimary = Color(0xFF27140A),
    primaryContainer = Color(0xFFFFDCC2),
    onPrimaryContainer = Color(0xFF351000),
    secondary = RailBrownLight,
    onSecondary = RailCream,
    background = Color(0xFFF7F1EC),
    onBackground = Color(0xFF241A16),
    surface = RailCream,
    onSurface = Color(0xFF241A16),
    surfaceVariant = Color(0xFFEEDFD5),
    onSurfaceVariant = Color(0xFF55443A),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB77B),
    onPrimary = Color(0xFF4E2500),
    primaryContainer = Color(0xFF713700),
    onPrimaryContainer = Color(0xFFFFDCC2),
    secondary = RailMuted,
    onSecondary = Color(0xFF3A2A22),
    background = DarkBackground,
    onBackground = RailCream,
    surface = RailPanel,
    onSurface = RailCream,
    surfaceVariant = RailBrownLight,
    onSurfaceVariant = Color(0xFFE8D6CA),
    error = Color(0xFFFFB4AB),
)

@Composable
fun TrainLiveMapTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
