package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = CyanPrimaryDark,
    onPrimary = Color(0xFF003258),
    primaryContainer = CyanContainer,
    onPrimaryContainer = CyanLight,
    secondary = AmberAccentDark,
    onSecondary = Color(0xFF452B00),
    secondaryContainer = Color(0xFF633F00),
    onSecondaryContainer = AmberLight,
    tertiary = MintTertiaryDark,
    onTertiary = Color(0xFF003822),
    background = SlateDarkBg,
    onBackground = TextDarkPrimary,
    surface = SlateDarkSurface,
    onSurface = TextDarkPrimary,
    surfaceVariant = SlateDarkSurfaceVariant,
    onSurfaceVariant = TextDarkSecondary,
    outline = SlateDarkBorder
)

private val LightColorScheme = lightColorScheme(
    primary = CyanPrimary,
    onPrimary = Color.White,
    primaryContainer = CyanLight,
    onPrimaryContainer = Color(0xFF001E36),
    secondary = AmberAccent,
    onSecondary = Color.White,
    secondaryContainer = AmberLight,
    onSecondaryContainer = Color(0xFF2A1700),
    tertiary = MintTertiary,
    onTertiary = Color.White,
    background = SlateLightBg,
    onBackground = TextLightPrimary,
    surface = SlateLightSurface,
    onSurface = TextLightPrimary,
    surfaceVariant = SlateLightSurfaceVariant,
    onSurfaceVariant = TextLightSecondary,
    outline = SlateLightBorder
)

@Composable
fun FocusFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep branded FocusFlow look consistent
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
