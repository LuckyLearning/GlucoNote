package com.gluconote.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp

@Composable
fun DonutChart(
    rate: Float,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF4CAF50)
) {
    Canvas(modifier) {
        val stroke = 12.dp.toPx()
        val r = (size.minDimension - stroke) / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            color = Color.LightGray.copy(alpha = 0.4f),
            radius = r,
            center = c,
            style = DrawStroke(stroke)
        )
        val sweep = 360f * rate.coerceIn(0f, 1f)
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(c.x - r, c.y - r),
            size = Size(r * 2f, r * 2f),
            style = DrawStroke(stroke)
        )
        val textPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textAlign = Paint.Align.CENTER
            textSize = 18.dp.toPx()
        }
        drawContext.canvas.nativeCanvas.drawText(
            "${(rate * 100).toInt()}%",
            c.x,
            c.y + 6.dp.toPx(),
            textPaint
        )
    }
}
