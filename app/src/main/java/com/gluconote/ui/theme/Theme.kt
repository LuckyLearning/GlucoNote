package com.gluconote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.gluconote.data.model.Settings
import com.gluconote.util.levelOf

private val LightColors = lightColorScheme(
    primary = Primary,
    secondary = Secondary,
    tertiary = Color(0xFF4CAF50)
)

private val DarkColors = darkColorScheme(
    primary = Primary,
    secondary = Secondary,
    tertiary = Color(0xFF4CAF50)
)

@Composable
fun GlucoNoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}

fun statusColor(value: Float, periodKey: String, settings: Settings): Color =
    when (levelOf(value, periodKey, settings)) {
        "low" -> LowColor
        "high" -> HighColor
        else -> NormalColor
    }
