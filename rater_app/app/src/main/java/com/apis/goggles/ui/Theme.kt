package com.apis.goggles.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.apis.goggles.data.Action

// Honey palette, after the goggle's avatar colour #FCEA7E.
private val Light = lightColorScheme(
    primary = Color(0xFF6F5600),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFCEA7E),
    onPrimaryContainer = Color(0xFF241A00),
    secondary = Color(0xFF5E5A4C),
    secondaryContainer = Color(0xFFE6DFC8),
    background = Color(0xFFFFFBF0),
    surface = Color(0xFFFFFBF0),
    surfaceContainerLow = Color(0xFFFBF4E2),
    surfaceContainer = Color(0xFFF5EED9),
    surfaceContainerHigh = Color(0xFFEFE8D3),
    outlineVariant = Color(0xFFD9D0B8),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFCEA7E),
    onPrimary = Color(0xFF3A2F00),
    primaryContainer = Color(0xFF544200),
    onPrimaryContainer = Color(0xFFFCEA7E),
    secondary = Color(0xFFCFC6AE),
    secondaryContainer = Color(0xFF4A4535),
    background = Color(0xFF17140C),
    surface = Color(0xFF17140C),
    surfaceContainerLow = Color(0xFF1F1B12),
    surfaceContainer = Color(0xFF241F15),
    surfaceContainerHigh = Color(0xFF2E2A1F),
    outlineVariant = Color(0xFF4A4535),
)

@Immutable
data class ActionColors(val discard: Color, val downrank: Color, val neutral: Color, val boost: Color) {
    fun of(a: Action) = when (a) {
        Action.DISCARD -> discard
        Action.DOWNRANK -> downrank
        Action.NEUTRAL -> neutral
        Action.BOOST -> boost
    }
}

val LocalActionColors = staticCompositionLocalOf {
    ActionColors(Color.Red, Color.Yellow, Color.Gray, Color.Green)
}

@Composable
fun GogglesTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val actions = if (dark) {
        ActionColors(Color(0xFFFF8A80), Color(0xFFFFB74D), Color(0xFFB8B2A0), Color(0xFF8BD48F))
    } else {
        ActionColors(Color(0xFFC62828), Color(0xFFD96C00), Color(0xFF6E6957), Color(0xFF2E7D32))
    }
    val base = Typography()
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = base.copy(
            headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        ),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalActionColors provides actions, content = content)
    }
}
