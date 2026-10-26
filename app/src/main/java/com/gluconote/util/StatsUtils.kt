package com.gluconote.util

import com.gluconote.data.model.GlucoseRecord
import com.gluconote.data.model.Period
import com.gluconote.data.model.Settings
import kotlin.math.sqrt

enum class RangeType { DAY, WEEK, MONTH, YEAR }
enum class Granularity { DAY, MONTH, YEAR }

data class TrendPoint(
    val ts: Long,
    val value: Float,
    val label: String,
    val period: String
)

data class RateResult(val total: Int, val inRange: Int) {
    val rate: Float get() = if (total == 0) 0f else inRange.toFloat() / total
}

data class StatsResult(
    val avg: Float,
    val max: Float,
    val min: Float,
    val std: Float,
    val count: Int
)

data class Bucket(
    val label: String,
    val avg: Float,
    val max: Float,
    val min: Float,
    val rate: Float,
    val count: Int
)

/** 判定某次测量：偏低 / 正常 / 偏高（参考对应标签的目标区间） */
fun levelOf(value: Float, periodKey: String, settings: Settings): String {
    val (min, max) = if (periodKey == Period.FASTING.key) {
        settings.fastingMin to settings.fastingMax
    } else {
        settings.postMin to settings.postMax
    }
    return when {
        value < min -> "low"
        value > max -> "high"
        else -> "normal"
    }
}

/** 趋势点序列（按时间升序），可选按标签筛选 */
fun computeTrend(
    records: List<GlucoseRecord>,
    rangeType: RangeType,
    filterPeriod: String? = null
): List<TrendPoint> {
    val list = if (filterPeriod != null) records.filter { it.period == filterPeriod } else records
    return list.sortedBy { it.measureTime }.map {
        val label = when (rangeType) {
            RangeType.DAY -> formatTime(it.measureTime)
            else -> formatDate(it.measureTime)
        }
        TrendPoint(it.measureTime, it.value, label, it.period)
    }
}

/** 按 5 个标签分别统计达标率（无论是否筛选，始终分组便于横向对比） */
fun computeRateByPeriod(
    records: List<GlucoseRecord>,
    settings: Settings
): Map<Period, RateResult> {
    return Period.all.associateWith { p ->
        val recs = records.filter { it.period == p.key }
        val inRange = recs.count { levelOf(it.value, p.key, settings) == "normal" }
        RateResult(recs.size, inRange)
    }
}

private fun statsOf(list: List<GlucoseRecord>): StatsResult {
    if (list.isEmpty()) return StatsResult(0f, 0f, 0f, 0f, 0)
    val vals = list.map { it.value }
    val avg = vals.average().toFloat()
    val max = vals.maxOrNull() ?: 0f
    val min = vals.minOrNull() ?: 0f
    val variance = vals.map { (it - avg) * (it - avg) }.average().toFloat()
    val std = sqrt(variance)
    return StatsResult(avg, max, min, std, list.size)
}

/** 指定时间区间的聚合统计，可选按标签筛选 */
fun computeRangeStats(
    records: List<GlucoseRecord>,
    from: Long,
    to: Long,
    filterPeriod: String? = null
): StatsResult {
    val list = records.filter {
        it.measureTime in from..to && (filterPeriod == null || it.period == filterPeriod)
    }
    return statsOf(list)
}

/** 按年/月/日自然分桶的聚合（均值/极值/达标率），可选按标签筛选 */
fun computeBucketStats(
    records: List<GlucoseRecord>,
    granularity: Granularity,
    settings: Settings,
    filterPeriod: String? = null
): List<Bucket> {
    val list = if (filterPeriod != null) records.filter { it.period == filterPeriod } else records
    val buckets = LinkedHashMap<String, MutableList<GlucoseRecord>>()
    for (r in list) {
        val key = when (granularity) {
            Granularity.DAY -> formatDate(r.measureTime)
            Granularity.MONTH -> formatMonth(r.measureTime)
            Granularity.YEAR -> formatYear(r.measureTime)
        }
        buckets.getOrPut(key) { mutableListOf() }.add(r)
    }
    return buckets.keys.sorted().map { key ->
        val recs = buckets[key]!!
        val s = statsOf(recs)
        val rate = if (recs.isEmpty()) 0f else
            recs.count { levelOf(it.value, it.period, settings) == "normal" }.toFloat() / recs.size
        Bucket(key, s.avg, s.max, s.min, rate, recs.size)
    }
}
