package com.toddleai.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val ToddleColorScheme = lightColorScheme(
    primary = Terracotta,
    secondary = Moss,
    tertiary = Denim,
    background = SoftIvory,
    surface = WarmSand,
    onBackground = Charcoal,
    onSurface = Charcoal,
)

@Composable
fun ToddleAITheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ToddleColorScheme,
        typography = ToddleTypography,
        content = content,
    )
}
