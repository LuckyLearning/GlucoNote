package com.gluconote.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.gluconote.data.model.Settings
import com.gluconote.util.TrendPoint

@Composable
fun TrendLineChart(
    points: List<TrendPoint>,
    settings: Settings,
    modifier: Modifier = Modifier
) {
    Canvas(modifier.fillMaxWidth().height(220.dp)) {
        val w = size.width
        val h = size.height
        val padL = 40.dp.toPx()
        val padR = 16.dp.toPx()
        val padT = 24.dp.toPx()
        val padB = 28.dp.toPx()
        val plotW = w - padL - padR
        val plotH = h - padT - padB

        if (points.isEmpty()) return@Canvas

        val values = points.map { it.value }
        val minV = (values.minOrNull() ?: 0f) - 1f
        val maxV = (values.maxOrNull() ?: 10f) + 1f
        val range = (maxV - minV).coerceAtLeast(1f)

        fun yFor(v: Float) = padT + plotH * (1f - (v - minV) / range)
        fun xFor(i: Int) = padL + if (points.size == 1) plotW / 2f else plotW * i / (points.size - 1)

        // 目标区间带（餐后范围）
        val bandTop = yFor(settings.postMax)
        val bandBottom = yFor(settings.postMin)
        drawRect(
            color = Color(0xFF4CAF50),
            topLeft = Offset(padL, bandTop),
            size = Size(plotW, (bandBottom - bandTop).coerceAtLeast(0f)),
            alpha = 0.15f
        )

        // 坐标轴
        drawLine(
            color = Color.Gray,
            start = Offset(padL, padT),
            end = Offset(padL, padT + plotH),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = Color.Gray,
            start = Offset(padL, padT + plotH),
            end = Offset(padL + plotW, padT + plotH),
            strokeWidth = 1.dp.toPx()
        )

        // 折线
        val path = Path()
        points.forEachIndexed { i, p ->
            val x = xFor(i)
            val y = yFor(p.value)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Color(0xFF3F7D54), style = Stroke(2.dp.toPx()))

        // 数据点 + 数值标签
        val labelPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textAlign = Paint.Align.CENTER
            textSize = 11.dp.toPx()
        }
        points.forEachIndexed { i, p ->
            val x = xFor(i)
            val y = yFor(p.value)
            drawCircle(Color(0xFF3F7D54), 3.dp.toPx(), Offset(x, y))
            drawContext.canvas.nativeCanvas.drawText(
                "%.1f".format(p.value),
                x,
                y - 8.dp.toPx(),
                labelPaint
            )
        }
    }
}
