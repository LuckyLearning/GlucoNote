package com.gluconote.data.local

/**
 * 查询投影（Room 行类型）。
 *
 * 这些类**只用来接 SQL 结果**，不参与业务逻辑；
 * 字段名必须与 `@Query` 里的 `AS 别名` 完全一致（Room 按名字映射）。
 */

/** 首页概览：一条 SQL 同时拿「今日条数」和「近 7 天均值」 */
data class HomeAggRow(
    val todayCount: Int,
    /** 窗口内没有记录时 `AVG` 返回 NULL */
    val weekAvg: Float?
)

/** 按标签分组的达标率 */
data class PeriodRateRow(
    val period: String,
    val total: Int,
    val inRange: Int
)

/**
 * 区间聚合。SQLite 没有 `STDDEV`，用二阶矩换算：
 * `std = sqrt(avgSq - avg²)`（见 `util/statsFromMoments`）。
 */
data class RangeStatsRow(
    val count: Int,
    val valueAvg: Float?,
    val valueMax: Float?,
    val valueMin: Float?,
    val valueAvgSq: Float?
)

/** 趋势原始点（三列都在覆盖索引里，可走 index-only 扫描，不必回表构造完整体） */
data class TrendRow(
    val measureTime: Long,
    val value: Float,
    val period: String
)

/**
 * 趋势按本地日聚合后的点。
 *
 * [day] 是「本地日序号」= `(measureTime + tzShift) / 86400000`，
 * 由调用方传入当前时区偏移，避免用 SQLite 的 `localtime` 修饰符（时区/DST 易出错）。
 * [firstTs] 是该桶内最早一条记录的时间戳，只用来生成日期标签，
 * 保证显示的是本地日期而不是 UTC 日期的前一天。
 */
data class DailyPoint(
    val day: Long,
    val firstTs: Long,
    val valueAvg: Float,
    val valueMax: Float,
    val valueMin: Float,
    val count: Int
)
