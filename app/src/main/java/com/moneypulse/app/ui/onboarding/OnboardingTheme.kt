package com.moneypulse.app.ui.onboarding

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Light theme colors
private val LightColorPalette = lightColors(
    primary = Color(0xFF2E7D32), // Green
    primaryVariant = Color(0xFF005005),
    secondary = Color(0xFF1976D2), // Blue
    background = Color.White,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black
)

// Dark theme colors
private val DarkColorPalette = darkColors(
    primary = Color(0xFF4CAF50), // Light Green
    primaryVariant = Color(0xFF388E3C),
    secondary = Color(0xFF42A5F5), // Light Blue
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun OnboardingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) {
        DarkColorPalette
    } else {
        LightColorPalette
    }

    MaterialTheme(
        colors = colors,
        content = content
    )
} 