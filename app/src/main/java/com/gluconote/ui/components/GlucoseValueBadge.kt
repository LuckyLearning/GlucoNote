package com.gluconote.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gluconote.data.model.Settings
import com.gluconote.ui.theme.statusColor
import com.gluconote.util.levelOf

/**
 * 血糖状态徽标：颜色 + 文字 + 箭头 三重编码（适老无障碍，弱光 / 色弱也能秒懂）。
 * 与原型一致：正常 ▼ / 偏高 ▲ / 偏低 ▼
 */
@Composable
fun GlucoseValueBadge(
    value: Float,
    periodKey: String,
    settings: Settings,
    modifier: Modifier = Modifier
) {
    val level = levelOf(value, periodKey, settings)
    val (text, arrow) = when (level) {
        "low" -> "偏低" to "▼"
        "high" -> "偏高" to "▲"
        else -> "正常" to "▼"
    }
    val color = statusColor(value, periodKey, settings)
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.12f)
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
            Text(arrow, style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}
