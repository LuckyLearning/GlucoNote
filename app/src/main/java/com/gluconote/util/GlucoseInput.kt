package com.gluconote.util

/**
 * 血糖值键盘输入的规范化规则（纯逻辑，便于单测）。
 *
 * 目标：让「适老四步录入」的数字键盘不会产生非法输入 ——
 *  - 只有一个小数点；
 *  - 最多 1 位小数（血糖仪读数精度）；
 *  - 整数部分最多 3 位（覆盖到 99.9，再大就靠超范围提醒兜住）；
 *  - 去掉无意义的前导零（"05" → "5"，但 "0." 保留）。
 */
object GlucoseInput {

    /** 整数部分最多几位 */
    const val MAX_INT_DIGITS = 3

    /** 小数最多几位 */
    const val MAX_DECIMALS = 1

    /** 超出常见范围（只用于提示，不阻断保存） */
    const val PLAUSIBLE_MIN = 1.0f
    const val PLAUSIBLE_MAX = 50.0f

    /**
     * 敲一个键之后的新文本。
     *
     * @param current 当前文本
     * @param key 按下的键：数字、"0"-"9" 或 "."
     * @return 新文本；非法（会超限）时返回 null，界面应保持原样
     */
    fun append(current: String, key: String): String? {
        if (key == ".") {
            return when {
                current.contains('.') -> null
                current.isEmpty() -> "0."
                else -> "$current."
            }
        }
        val digit = key.firstOrNull()?.takeIf { it.isDigit() } ?: return null
        val dot = current.indexOf('.')
        if (dot < 0) {
            // 整数部分
            if (current.length >= MAX_INT_DIGITS) return null
            return if (current == "0") digit.toString() else "$current$digit"
        }
        // 小数部分
        val decimals = current.length - dot - 1
        if (decimals >= MAX_DECIMALS) return null
        return "$current$digit"
    }

    /** 退格；空串保持不变 */
    fun backspace(current: String): String =
        if (current.isEmpty()) current else current.dropLast(1)

    /** 解析成血糖值；空串、孤立小数点、0 都返回 null */
    fun parse(text: String): Float? {
        val v = text.toFloatOrNull() ?: return null
        return v.takeIf { it > 0f }
    }

    /** 是否落在常见范围内（不阻断保存） */
    fun isPlausible(value: Float): Boolean = value in PLAUSIBLE_MIN..PLAUSIBLE_MAX
}
