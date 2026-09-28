package com.aetherweb.app.ui.theme

import android.app.Activity
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val PremiumColorScheme = darkColorScheme(
    primary = PremiumPrimary,
    onPrimary = Color.White,
    primaryContainer = PremiumPrimaryDark,
    onPrimaryContainer = Color.White,
    secondary = PremiumSecondary,
    onSecondary = Color.White,
    background = PremiumBackground,
    onBackground = PremiumTextPrimary,
    surface = PremiumSurface,
    onSurface = PremiumTextPrimary,
    surfaceVariant = PremiumBorder,
    onSurfaceVariant = PremiumTextPrimary,
    error = PremiumAccent
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force Dark Theme for Premium Look
    content: @Composable () -> Unit
) {
    val colorScheme = PremiumColorScheme
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
