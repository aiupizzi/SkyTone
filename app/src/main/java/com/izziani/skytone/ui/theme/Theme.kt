package com.izziani.skytone.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DuskColorScheme = darkColorScheme(
    primary = SunsetAmber,
    onPrimary = DuskNavy,
    secondary = SunsetCoral,
    onSecondary = DuskNavy,
    tertiary = SunsetRose,
    background = DuskNavy,
    onBackground = MistWhite,
    surface = DuskMidnight,
    onSurface = MistWhite,
    surfaceVariant = DuskPurple,
    onSurfaceVariant = MistMuted,
    outlineVariant = GlassBorder
)

@Composable
fun SkyToneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = DuskColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
