package com.gluconote.util

import com.gluconote.data.model.GlucoseRecord
import com.gluconote.data.model.Period
import com.gluconote.data.model.Settings
import org.junit.Assert.assertEquals
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
}
