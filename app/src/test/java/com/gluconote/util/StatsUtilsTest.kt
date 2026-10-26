package com.gluconote.util

import com.gluconote.data.model.GlucoseRecord
import com.gluconote.data.model.Period
import com.gluconote.data.model.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class StatsUtilsTest {

    private val settings = Settings()

    private fun rec(id: String, ts: Long, v: Float, p: String) =
        GlucoseRecord(id, ts, v, p, "")

    @Test
    fun levelOf_classifiesCorrectly() {
        assertEquals("normal", levelOf(6.0f, Period.FASTING.key, settings))
        assertEquals("low", levelOf(3.5f, Period.FASTING.key, settings))
        assertEquals("high", levelOf(8.5f, Period.FASTING.key, settings))
        assertEquals("high", levelOf(11f, Period.BREAKFAST.key, settings))
        assertEquals("normal", levelOf(8f, Period.BREAKFAST.key, settings))
    }

    @Test
    fun computeRateByPeriod_countsInRange() {
        val list = listOf(
            rec("1", 1, 6f, Period.FASTING.key),
            rec("2", 2, 8f, Period.FASTING.key),
            rec("3", 3, 6f, Period.BREAKFAST.key)
        )
        val map = computeRateByPeriod(list, settings)
        assertEquals(2, map[Period.FASTING]?.total)
        assertEquals(1, map[Period.FASTING]?.inRange)
        assertEquals(0.5f, map[Period.FASTING]?.rate ?: 0f, 0.001f)
        assertEquals(1, map[Period.BREAKFAST]?.total)
        assertEquals(1, map[Period.BREAKFAST]?.inRange)
    }

    @Test
    fun computeRangeStats_aggregates() {
        val list = listOf(
            rec("1", 1000, 5f, Period.FASTING.key),
            rec("2", 2000, 7f, Period.FASTING.key),
            rec("3", 3000, 9f, Period.FASTING.key)
        )
        val s = computeRangeStats(list, 0, 4000)
        assertEquals(3, s.count)
        assertEquals(7f, s.avg, 0.001f)
        assertEquals(9f, s.max, 0.001f)
        assertEquals(5f, s.min, 0.001f)
    }

    @Test
    fun computeBucketStats_groupsByMonth() {
        val jan = System.currentTimeMillis()
        val feb = Calendar.getInstance().apply {
            timeInMillis = jan
            add(Calendar.MONTH, 1)
        }.timeInMillis
        val list = listOf(
            rec("1", jan, 6f, Period.FASTING.key),
            rec("2", feb, 8f, Period.FASTING.key)
        )
        val buckets = computeBucketStats(list, Granularity.MONTH, settings)
        assertEquals(2, buckets.size)
        assertEquals(1, buckets[0].count)
        assertEquals(1, buckets[1].count)
    }

    @Test
    fun computeTrend_filtersByTimeWindow() {
        val now = 1_700_000_000_000L
        val list = listOf(
            rec("old", now - 40 * DAY_MS, 6f, Period.FASTING.key),
            rec("week", now - 3 * DAY_MS, 7f, Period.FASTING.key),
            rec("today", now - 2 * 60 * 60 * 1000, 8f, Period.BREAKFAST.key)
        )
        val year = computeTrend(list, RangeType.YEAR, now = now)
        assertEquals(3, year.size)

        val month = computeTrend(list, RangeType.MONTH, now = now)
        assertEquals(2, month.size)

        val week = computeTrend(list, RangeType.WEEK, now = now)
        assertEquals(2, week.size)

        val day = computeTrend(list, RangeType.DAY, now = now)
        assertEquals(1, day.size)
        assertEquals("today", day[0].let { list.first { r -> r.measureTime == it.ts }.id })
    }

    @Test
    fun computeTrend_filtersByPeriod() {
        val now = 1_700_000_000_000L
        val list = listOf(
            rec("1", now - DAY_MS / 2, 6f, Period.FASTING.key),
            rec("2", now - DAY_MS / 3, 7f, Period.BREAKFAST.key)
        )
        val fasting = computeTrend(list, RangeType.WEEK, filterPeriod = Period.FASTING.key, now = now)
        assertEquals(1, fasting.size)
        assertEquals(Period.FASTING.key, fasting[0].period)
    }

    @Test
    fun rangeWindowStart_matchesDayCounts() {
        val now = 1_000_000L
        assertEquals(now - DAY_MS, rangeWindowStart(RangeType.DAY, now))
        assertEquals(now - 7 * DAY_MS, rangeWindowStart(RangeType.WEEK, now))
        assertEquals(now - 30 * DAY_MS, rangeWindowStart(RangeType.MONTH, now))
        assertEquals(now - 365 * DAY_MS, rangeWindowStart(RangeType.YEAR, now))
        assertTrue(rangeWindowStart(RangeType.YEAR, now) < rangeWindowStart(RangeType.MONTH, now))
    }

    @Test
    fun scopedRecords_and_rate_followTimeWindow() {
        val now = 1_700_000_000_000L
        val list = listOf(
            rec("old", now - 200 * DAY_MS, 6f, Period.FASTING.key),
            rec("mid", now - 20 * DAY_MS, 8f, Period.FASTING.key),
            rec("week", now - 2 * DAY_MS, 8f, Period.FASTING.key),
            rec("today", now - 2 * 60 * 60 * 1000L, 8f, Period.FASTING.key)
        )
        val yearScoped = scopedRecords(list, RangeType.YEAR, now = now)
        val weekScoped = scopedRecords(list, RangeType.WEEK, now = now)
        val dayScoped = scopedRecords(list, RangeType.DAY, now = now)

        assertEquals(4, yearScoped.size)
        assertEquals(2, weekScoped.size)
        assertEquals(1, dayScoped.size)

        val yearRate = computeRateByPeriod(yearScoped, settings)[Period.FASTING]!!
        val dayRate = computeRateByPeriod(dayScoped, settings)[Period.FASTING]!!
        assertEquals(4, yearRate.total)
        assertEquals(1, yearRate.inRange) // only "old" (6f) in range for fasting
        assertEquals(1, dayRate.total)
        assertEquals(0, dayRate.inRange) // 8f high for fasting
        assertTrue(yearRate.total > dayRate.total)
    }
}
