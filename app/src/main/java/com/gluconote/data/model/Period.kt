package com.gluconote.data.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gluconote.R

/**
 * 测量标签（同时是首页快捷按钮、录入选项与统计分析的分组维度）。
 */
enum class Period(
    val key: String,
    val labelRes: Int,
    val hintRes: Int
) {
    FASTING("fasting", R.string.period_fasting, R.string.period_fasting_hint),
    BREAKFAST("breakfast", R.string.period_breakfast, R.string.period_breakfast_hint),
    LUNCH("lunch", R.string.period_lunch, R.string.period_lunch_hint),
    DINNER("dinner", R.string.period_dinner, R.string.period_dinner_hint),
    SLEEP("sleep", R.string.period_sleep, R.string.period_sleep_hint);

    @Composable
    fun label(): String = stringResource(labelRes)

    @Composable
    fun hint(): String = stringResource(hintRes)

    /** 非 Composable 的纯文本标签，供 CSV 导出等无上下文场景使用 */
    fun plainLabel(): String = when (this) {
        FASTING -> "空腹"
        BREAKFAST -> "早餐后"
        LUNCH -> "午餐后"
        DINNER -> "晚餐后"
        SLEEP -> "睡前"
    }

    companion object {
        fun fromKey(key: String): Period =
            entries.firstOrNull { it.key == key } ?: FASTING

        val all: List<Period> = entries.toList()
    }
}
