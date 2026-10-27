package com.gluconote.data.model

/**
 * 外观主题模式。数据层只存 [key]（字符串），便于 DataStore 与后续加值。
 */
enum class ThemeMode(val key: String, val label: String) {
    /** 跟随系统深/浅色 */
    SYSTEM("system", "跟随系统"),

    /** 始终浅色 */
    LIGHT("light", "浅色"),

    /** 始终深色 */
    DARK("dark", "深色");

    companion object {
        fun fromKey(key: String?): ThemeMode =
            values().firstOrNull { it.key == key } ?: SYSTEM
    }
}
