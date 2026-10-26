package com.gluconote.data.model

/**
 * 目标血糖范围设置（默认采用临床常用控糖目标，可在「我的」中修改）。
 */
data class Settings(
    val fastingMin: Float = 4.4f,
    val fastingMax: Float = 7.0f,
    val postMin: Float = 4.4f,
    val postMax: Float = 10.0f,
    val lowThreshold: Float = 3.9f
)
