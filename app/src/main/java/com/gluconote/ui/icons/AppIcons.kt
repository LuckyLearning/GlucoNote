package com.gluconote.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 自绘图标，替代 `material-icons-extended`。
 *
 * 整个应用只用到 5 个图标，其中 `BarChart` / `ListAlt` 属于 extended 包。
 * 该包会把约 1500 个图标全部编进 dex（实测占用 ~10 MB），
 * 为两个图标付这个代价不划算 —— 这里用 `ImageVector` 直接画。
 *
 * 尺寸/视口与 Material 图标一致（24dp / 24x24），可直接放进 `Icon()`。
 */
object AppIcons {

    /** 柱状图：三根高低不同的柱子，Material BarChart 的视觉等价物。 */
    val BarChart: ImageVector by lazy {
        ImageVector.Builder(
            name = "BarChart",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            fun bar(left: Float, top: Float, width: Float) {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(left, top)
                    horizontalLineTo(left + width)
                    verticalLineTo(20f)
                    horizontalLineTo(left)
                    close()
                }
            }
            bar(4f, 10f, 4f)
            bar(10f, 4f, 4f)
            bar(16f, 14f, 4f)
        }.build()
    }
}
