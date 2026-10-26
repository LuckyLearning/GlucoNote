package com.gluconote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gluconote.data.model.Settings
import com.gluconote.util.levelOf

// ── 浅色方案 ─────────────────────────────────────────────
private val LightColors = lightColorScheme(
    primary = Color(0xFF2F7D53),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC5EAD3),
    onPrimaryContainer = Color(0xFF0B3D24),
    secondary = Color(0xFF4E6E5D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD2E8DB),
    onSecondaryContainer = Color(0xFF0D2B1B),
    tertiary = Color(0xFF3D6373),
    onTertiary = Color(0xFFFFFFFF),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6FAF6),
    onBackground = Color(0xFF171D19),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171D19),
    surfaceVariant = Color(0xFFDDE5DD),
    onSurfaceVariant = Color(0xFF414942),
    surfaceContainer = Color(0xFFF0F5EF),
    surfaceContainerHigh = Color(0xFFEAEFE9),
    outline = Color(0xFF717971),
    outlineVariant = Color(0xFFC1C9BF)
)

// ── 深色方案 ─────────────────────────────────────────────
private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD6AC),
    onPrimary = Color(0xFF00391E),
    primaryContainer = Color(0xFF17522F),
    onPrimaryContainer = Color(0xFFC5EAD3),
    secondary = Color(0xFFB6CCBE),
    onSecondary = Color(0xFF22352A),
    secondaryContainer = Color(0xFF384B40),
    onSecondaryContainer = Color(0xFFD2E8DB),
    tertiary = Color(0xFFA5CDDF),
    onTertiary = Color(0xFF063543),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1511),
    onBackground = Color(0xFFDFE4DE),
    surface = Color(0xFF171D19),
    onSurface = Color(0xFFDFE4DE),
    surfaceVariant = Color(0xFF414942),
    onSurfaceVariant = Color(0xFFC1C9BF),
    surfaceContainer = Color(0xFF1E241F),
    surfaceContainerHigh = Color(0xFF282F29),
    outline = Color(0xFF8B938A),
    outlineVariant = Color(0xFF414942)
)

/** 圆角体系：小（输入框/芯片）、中（卡片）、大（弹窗/底板） */
private val GlucoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** 深浅模式标记：状态色、图表文字等非 Material 槽位需要 */
val LocalIsDark = staticCompositionLocalOf { false }

@Composable
fun GlucoNoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    androidx.compose.runtime.CompositionLocalProvider(LocalIsDark provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            shapes = GlucoShapes,
            content = content
        )
    }
}

/** 按当前主题取状态色（低/正常/高） */
@Composable
fun statusColor(value: Float, periodKey: String, settings: Settings): Color {
    val dark = LocalIsDark.current
    return when (levelOf(value, periodKey, settings)) {
        "low" -> if (dark) LowColorDark else LowColorLight
        "high" -> if (dark) HighColorDark else HighColorLight
        else -> if (dark) NormalColorDark else NormalColorLight
    }
}

/** 非回调上下文里取状态色 */
fun statusColor(value: Float, periodKey: String, settings: Settings, dark: Boolean): Color =
    when (levelOf(value, periodKey, settings)) {
        "low" -> if (dark) LowColorDark else LowColorLight
        "high" -> if (dark) HighColorDark else HighColorLight
        else -> if (dark) NormalColorDark else NormalColorLight
    }
