package com.gluconote.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.gluconote.data.model.Period

/**
 * 快捷按钮 / 筛选条的装饰色点：与原型一致
 * （空腹·绿 / 早餐后·红 / 午餐后·绿 / 晚餐后·蓝 / 睡前·绿）。
 * 仅作视觉提示，不表示达标状态（达标状态由 GlucoseValueBadge 按数值判定）。
 */
private val Tones = mapOf(
    Period.FASTING to "normal",
    Period.BREAKFAST to "high",
    Period.LUNCH to "normal",
    Period.DINNER to "low",
    Period.SLEEP to "normal"
)

@Composable
fun Period.toneColor(): Color {
    val dark = LocalIsDark.current
    return when (Tones[this] ?: "normal") {
        "low" -> if (dark) LowColorDark else LowColorLight
        "high" -> if (dark) HighColorDark else HighColorLight
        else -> if (dark) NormalColorDark else NormalColorLight
    }
}
