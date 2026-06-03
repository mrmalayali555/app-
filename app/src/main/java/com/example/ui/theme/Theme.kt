package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val ImmersiveDarkColorScheme = darkColorScheme(
    primary = PrimaryPurple,
    onPrimary = BackgroundDark,
    primaryContainer = PrimaryDark,
    onPrimaryContainer = PrimaryPurple,
    secondary = GreenLive,
    onSecondary = BackgroundDark,
    background = BackgroundDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondary,
    outline = BorderLight,
    outlineVariant = BorderDark,
    error = RedError,
    onErrorContainer = RedErrorBg
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark theme
    dynamicColor: Boolean = false, // Disable dynamic color to maintain Immersive UI look
    content: @Composable () -> Unit,
) {
    val colorScheme = ImmersiveDarkColorScheme
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
