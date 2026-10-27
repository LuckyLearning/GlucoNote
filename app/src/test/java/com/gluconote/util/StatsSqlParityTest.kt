package com.gluconote.util

import com.gluconote.data.model.GlucoseRecord
import com.gluconote.data.model.Period
import com.gluconote.data.model.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * SQL 聚合版与内存版（`StatsUtils` 原实现）的语义对拍。
 *
 * 这些断言的作用是**锁住改写**：查询从 Kotlin 下推到 SQL 以后，
 * 用户看到的数字必须逐值一致，否则就是回归。
 */
class StatsSqlParityTest {

    private val settings = Settings()

    private fun rec(id: String, ts: Long, v: Float, p: String) =
        GlucoseRecord(id, ts, v, p, "")

    // ---------------------------------------------------------- 达标判定

    /**
     * SQL 里写的是 `value BETWEEN fMin AND fMax`（闭区间），
     * Kotlin 里写的是 `value < min` / `value > max` 判高低。
     * 两者在边界上必须完全一致。
     */
    @Test
    fun sqlRule_matchesLevelOf_onAllBoundaries() {
        val cases = listOf(4.3999f, 4.4f, 4.4001f, 6.9f, 7.0f, 7.0001f, 9.9f, 10f, 10.0001f)
        val periods = Period.entries.map { it.key }
        for (key in periods) {
            for (v in cases) {
                val byKotlin = levelOf(v, key, settings) == "normal"
                val bySql = isInRangeBySqlRule(
                    v, key,
                    settings.fastingMin, settings.fastingMax,
                    settings.postMin, settings.postMax
                )
                assertEquals("period=$key value=$v", byKotlin, bySql)
            }
        }
    }

    @Test
    fun sqlRule_onlyFastingUsesFastingRange() {
        // 7.5 对空腹算偏高，对餐后算正常
        assertFalse(isInRangeBySqlRule(7.5f, Period.FASTING.key, 4.4f, 7.0f, 4.4f, 10f))
        assertTrue(isInRangeBySqlRule(7.5f, Period.BREAKFAST.key, 4.4f, 7.0f, 4.4f, 10f))
        // 睡前属于"非空腹"，走餐后区间
        assertTrue(isInRangeBySqlRule(9.9f, Period.SLEEP.key, 4.4f, 7.0f, 4.4f, 10f))
    }

    /** 自定义区间也要一致（用户在「我的」里改过目标值的情况） */
    @Test
    fun sqlRule_matchesLevelOf_onCustomRanges() {
        val custom = Settings(fastingMin = 5.0f, fastingMax = 6.5f, postMin = 5.5f, postMax = 8.8f)
        var v = 4.9f
        while (v <= 9.0f) {
            for (key in listOf(Period.FASTING.key, Period.DINNER.key)) {
                val byKotlin = levelOf(v, key, custom) == "normal"
                val bySql = isInRangeBySqlRule(v, key, 5.0f, 6.5f, 5.5f, 8.8f)
                assertEquals("value=$v period=$key", byKotlin, bySql)
            }
            v += 0.1f
        }
    }

    // ---------------------------------------------------------- 标准差换算

    /**
     * SQL 用二阶矩 `AVG(value*value)` 换算标准差；内存版用 `map { (x-avg)² }.average()`。
     * 数学等价，但浮点实现不同，允许 1e-4 误差。
     */
    @Test
    fun statsFromMoments_matchesInMemoryVariance() {
        val now = 1_700_000_000_000L
        val list = (0 until 50).map {
            rec("$it", now - it * 3_600_000L, 4f + (it % 11) * 0.7f, Period.LUNCH.key)
        }
        val expected = computeRangeStats(list, now - 400 * DAY_MS, now)

        val values = list.map { it.value }
        val avg = values.average().toFloat()
        val avgSq = values.map { it * it }.average().toFloat()
        val actual = statsFromMoments(
            count = values.size,
            valueAvg = avg,
            valueMax = values.maxOrNull(),
            valueMin = values.minOrNull(),
            valueAvgSq = avgSq
        )

        assertEquals(expected.avg, actual.avg, 1e-4f)
        assertEquals(expected.max, actual.max, 1e-4f)
        assertEquals(expected.min, actual.min, 1e-4f)
        assertEquals(expected.std, actual.std, 1e-4f)
        assertEquals(expected.count, actual.count)
    }

    /** 浮点误差可能让 `avgSq - avg²` 微负，必须夹到 0，否则 sqrt 得到 NaN */
    @Test
    fun statsFromMoments_neverReturnsNaN() {
        val r = statsFromMoments(
            count = 3,
            valueAvg = 6f,
            valueMax = 6f,
            valueMin = 6f,
            valueAvgSq = 36f - 1e-3f
        )
        assertEquals(0f, r.std, 0f)
        assertFalse(r.std.isNaN())
    }

    /** 空窗口：SQL 返回 count=0 / avg=null，要落回全零而不是崩 */
    @Test
    fun statsFromMoments_emptyIsAllZero() {
        val r = statsFromMoments(0, null, null, null, null)
        assertEquals(0, r.count)
        assertEquals(0f, r.avg, 0f)
        assertEquals(0f, r.std, 0f)
    }

    /** 单点样本：方差为 0 */
    @Test
    fun statsFromMoments_singleSampleHasZeroStd() {
        val r = statsFromMoments(1, 6.2f, 6.2f, 6.2f, 6.2f * 6.2f)
        assertEquals(0f, r.std, 1e-5f)
        assertEquals(6.2f, r.avg, 1e-5f)
    }

    // ---------------------------------------------------------- 降采样阈值

    @Test
    fun shouldDownsample_onlyAboveThreshold() {
        assertFalse(shouldDownsample(0))
        assertFalse(shouldDownsample(1))
        assertFalse(shouldDownsample(MAX_TREND_POINTS))
        assertTrue(shouldDownsample(MAX_TREND_POINTS + 1))
        // 1 年 × 5 次/天 = 1825 点，必须降采样
        assertTrue(shouldDownsample(1825))
        // 30 天 × 5 次/天 = 150 点，保持原始点
        assertFalse(shouldDownsample(150))
    }

    @Test
    fun maxTrendPoints_isTheDocumentedThreshold() {
        assertEquals(400, MAX_TREND_POINTS)
    }

    // ---------------------------------------------------------- 分桶窗口

    @Test
    fun bucketWindowStart_capsInputSize() {
        val now = 1_700_000_000_000L
        assertEquals(now - 60 * DAY_MS, bucketWindowStart(Granularity.DAY, now))
        assertEquals(now - 730 * DAY_MS, bucketWindowStart(Granularity.MONTH, now))
        assertEquals(now - 3650 * DAY_MS, bucketWindowStart(Granularity.YEAR, now))
        assertTrue(bucketWindowStart(Granularity.MONTH, now) < bucketWindowStart(Granularity.DAY, now))
        assertTrue(bucketWindowStart(Granularity.YEAR, now) < bucketWindowStart(Granularity.MONTH, now))
    }

    // ---------------------------------------------------------- 趋势标签

    @Test
    fun trendLabel_dayWindowShowsClockTime() {
        val ts = 1_700_000_000_000L
        // 1 天窗口看的是"几点"，更长的窗口看的是"哪天"
        assertTrue(trendLabel(RangeType.DAY, ts).length <= 5)
        assertEquals(formatDate(ts), trendLabel(RangeType.WEEK, ts))
        assertEquals(formatDate(ts), trendLabel(RangeType.YEAR, ts))
    }

    /** 二阶矩公式与内存版在其他样本形态上也要一致（全相同值 / 极端离散） */
    @Test
    fun statsFromMoments_holdsForDegenerateSamples() {
        val now = 1_700_000_000_000L

        val same = (0 until 20).map { rec("s$it", now - it * DAY_MS, 5.5f, Period.FASTING.key) }
        val sameExpected = computeRangeStats(same, 0, now)
        val sameActual = statsFromMoments(20, 5.5f, 5.5f, 5.5f, 5.5f * 5.5f)
        assertEquals(0f, sameActual.std, 1e-5f)
        assertEquals(sameExpected.std, sameActual.std, 1e-5f)

        val spread = (0 until 20).map { rec("d$it", now - it * DAY_MS, if (it % 2 == 0) 2f else 20f, Period.LUNCH.key) }
        val spreadExpected = computeRangeStats(spread, 0, now)
        val avg = 11f
        val avgSq = (2f * 2f + 20f * 20f) / 2f
        val spreadActual = statsFromMoments(20, avg, 20f, 2f, avgSq)
        assertEquals(sqrt(81f), spreadActual.std, 1e-3f)
        assertEquals(spreadExpected.std, spreadActual.std, 1e-3f)
    }
}
