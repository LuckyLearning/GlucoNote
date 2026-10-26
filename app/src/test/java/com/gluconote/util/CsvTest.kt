package com.gluconote.util

import com.gluconote.data.model.GlucoseRecord
import com.gluconote.data.model.Period
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvTest {

    @Test
    fun roundTrip_preservesData() {
        val list = listOf(
            GlucoseRecord("a", 1000L, 6.2f, Period.BREAKFAST.key, "吃面"),
            GlucoseRecord("b", 2000L, 7.5f, Period.FASTING.key, "含,逗号\"引号")
        )
        val csv = exportCsv(list)
        val parsed = parseCsv(csv)
        assertEquals(2, parsed.size)
        assertEquals("a", parsed[0].id)
        assertEquals(6.2f, parsed[0].value, 0.001f)
        assertEquals(Period.BREAKFAST.key, parsed[0].period)
        assertEquals("吃面", parsed[0].note)
        assertEquals("含,逗号\"引号", parsed[1].note)
    }

    @Test
    fun parseHandlesHeaderAndBom() {
        val csv = "\uFEFFid,measure_time,value,period,period_label,note\n" +
            "x,2026-01-01 08:00:00,5.5,fasting,空腹,\n"
        val parsed = parseCsv(csv)
        assertEquals(1, parsed.size)
        assertEquals(5.5f, parsed[0].value, 0.001f)
        assertEquals(Period.FASTING.key, parsed[0].period)
    }
}
