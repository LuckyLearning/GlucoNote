package com.gluconote.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

/** 达标率环形图：底环 + 进度弧 + 中心百分比（文字颜色随主题） */
@Composable
fun DonutChart(
    rate: Float,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF4CAF50)
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface

    val textPaint = remember {
        Paint().apply {
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
    }
    textPaint.color = textColor.toArgb()

    Canvas(modifier) {
        val stroke = 11.dp.toPx()
        val r = (size.minDimension - stroke) / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        textPaint.textSize = 16.dp.toPx()
        drawCircle(
            color = trackColor,
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
        drawContext.canvas.nativeCanvas.drawText(
            "${(rate * 100).toInt()}%",
            c.x,
            c.y + 5.dp.toPx(),
            textPaint
        )
    }
}
