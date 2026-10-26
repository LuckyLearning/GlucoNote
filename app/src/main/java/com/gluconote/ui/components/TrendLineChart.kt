package com.gluconote.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.gluconote.data.model.Settings
import com.gluconote.ui.theme.LocalIsDark
import com.gluconote.ui.theme.ChartBand
import com.gluconote.ui.theme.ChartLine
import com.gluconote.util.TrendPoint
import java.util.Locale

/**
 * 血糖趋势折线图：
 *  - 目标区间绿色带 + 文字标注；
 *  - 折线下方渐变填充；
 *  - 数据点数值按达标状态着色（修复旧版深色主题下黑字不可见的问题）。
 */
@Composable
fun TrendLineChart(
    points: List<TrendPoint>,
    settings: Settings,
    modifier: Modifier = Modifier
) {
    val dark = LocalIsDark.current
    val lineColor = ChartLine
    val bandColor = ChartBand
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val labelText = if (dark) Color(0xFFDFE4DE) else Color(0xFF37403A)

    Canvas(modifier.fillMaxWidth().height(220.dp)) {
        val w = size.width
        val h = size.height
        val padL = 40.dp.toPx()
        val padR = 16.dp.toPx()
        val padT = 28.dp.toPx()
        val padB = 28.dp.toPx()
        val plotW = w - padL - padR
        val plotH = h - padT - padB

        if (points.isEmpty() || plotW <= 0f || plotH <= 0f) return@Canvas

        val values = points.map { it.value }
        val minV = (values.minOrNull() ?: 0f) - 1f
        val maxV = (values.maxOrNull() ?: 10f) + 1f
        val range = (maxV - minV).coerceAtLeast(1f)

        fun yFor(v: Float) = padT + plotH * (1f - (v - minV) / range)
        fun xFor(i: Int) = padL + if (points.size == 1) plotW / 2f else plotW * i / (points.size - 1)

        // 目标区间带
        val bandTop = yFor(settings.postMax)
        val bandBottom = yFor(settings.postMin)
        drawRect(
            color = bandColor,
            topLeft = Offset(padL, bandTop),
            size = Size(plotW, (bandBottom - bandTop).coerceAtLeast(0f)),
            alpha = 0.15f
        )

        // 坐标轴
        drawLine(axisColor, Offset(padL, padT), Offset(padL, padT + plotH), 1.dp.toPx())
        drawLine(axisColor, Offset(padL, padT + plotH), Offset(padL + plotW, padT + plotH), 1.dp.toPx())

        // 折线 + 下方渐变
        val fillPath = Path()
        val linePath = Path()
        points.forEachIndexed { i, p ->
            val x = xFor(i)
            val y = yFor(p.value)
            if (i == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, padT + plotH)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(xFor(points.lastIndex), padT + plotH)
        fillPath.close()
        drawPath(
            fillPath,
            brush = Brush.verticalGradient(
                listOf(lineColor.copy(alpha = 0.25f), lineColor.copy(alpha = 0.02f)),
                startY = padT,
                endY = padT + plotH
            )
        )
        drawPath(linePath, lineColor, style = Stroke(2.5.dp.toPx()))

        // 数据点 + 状态着色数值
        val labelPaint = Paint().apply {
            color = labelText.toArgb()
            textAlign = Paint.Align.CENTER
            textSize = 11.dp.toPx()
            isAntiAlias = true
        }
        points.forEachIndexed { i, p ->
            val x = xFor(i)
            val y = yFor(p.value)
            drawCircle(lineColor, 3.dp.toPx(), Offset(x, y))
            drawContext.canvas.nativeCanvas.drawText(
                "%.1f".format(Locale.US, p.value),
                x,
                y - 8.dp.toPx(),
                labelPaint
            )
        }
    }
}
