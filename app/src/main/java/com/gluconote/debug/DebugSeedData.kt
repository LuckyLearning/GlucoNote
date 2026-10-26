package com.gluconote.debug

import com.gluconote.data.model.GlucoseRecord

import com.gluconote.data.model.Period

import com.gluconote.data.model.Settings

import com.gluconote.util.startOfDay

import java.util.Calendar

/**

 * Debug-only seed dataset for verifying Home / Records / Stats / Profile.

 * Covers ~14 days, all [Period] keys, low / normal / high / very-high values,

 * and notes on a subset of rows. Relative to default [Settings] thresholds.

 */

object DebugSeedData {

    /** Clinical defaults used by the seed so colour bands match known ranges. */

    val demoSettings: Settings = Settings(

        fastingMin = 4.4f,

        fastingMax = 7.0f,

        postMin = 4.4f,

        postMax = 10.0f,

        lowThreshold = 3.9f

    )

    /**

     * Build a fresh batch anchored at "today" (device local).

     * Day 0 = today, day 13 = 13 days ago.

     */

    fun buildRecords(nowMs: Long = System.currentTimeMillis()): List<GlucoseRecord> {

        val todayStart = startOfDay(nowMs)

        val out = mutableListOf<GlucoseRecord>()

        data class Spec(

            val dayOffset: Int,

            val hour: Int,

            val minute: Int,

            val period: Period,

            val value: Float,

            val note: String = ""

        )

        val specs = mutableListOf<Spec>()

        // Today: full day, mixed levels

        specs += Spec(0, 7, 5, Period.FASTING, 5.6f, "起床后")

        specs += Spec(0, 9, 10, Period.BREAKFAST, 8.2f)

        specs += Spec(0, 13, 45, Period.LUNCH, 11.5f, "外卖偏油")

        specs += Spec(0, 19, 30, Period.DINNER, 9.1f)

        specs += Spec(0, 22, 15, Period.SLEEP, 6.8f)

        // Yesterday: include a low

        specs += Spec(1, 6, 50, Period.FASTING, 3.6f, "偏低，喝了糖水")

        specs += Spec(1, 9, 0, Period.BREAKFAST, 7.4f)

        specs += Spec(1, 14, 10, Period.LUNCH, 9.8f)

        specs += Spec(1, 20, 5, Period.DINNER, 12.8f, "生日聚餐")

        specs += Spec(1, 22, 40, Period.SLEEP, 8.5f)

        // Day -2: very high post-meal

        specs += Spec(2, 7, 20, Period.FASTING, 8.4f, "空腹偏高")

        specs += Spec(2, 9, 30, Period.BREAKFAST, 14.2f, "超高")

        specs += Spec(2, 13, 50, Period.LUNCH, 10.2f)

        specs += Spec(2, 19, 40, Period.DINNER, 7.9f)

        specs += Spec(2, 22, 0, Period.SLEEP, 6.2f)

        // Day -3..-6: mostly normal with occasional outliers

        val midWeek = listOf(

            listOf(5.2f, 7.8f, 8.5f, 9.0f, 6.5f),

            listOf(4.8f, 6.9f, 9.2f, 8.1f, 5.9f),

            listOf(6.1f, 10.5f, 7.6f, 11.0f, 7.2f),

            listOf(3.8f, 8.0f, 8.8f, 7.5f, 6.0f)

        )

        midWeek.forEachIndexed { i, vals ->

            val d = 3 + i

            specs += Spec(d, 7, 0 + i, Period.FASTING, vals[0])

            specs += Spec(d, 9, 5 + i * 2, Period.BREAKFAST, vals[1])

            specs += Spec(d, 13, 40 + i, Period.LUNCH, vals[2])

            specs += Spec(d, 19, 20 + i * 3, Period.DINNER, vals[3])

            specs += Spec(d, 22, 10 + i, Period.SLEEP, vals[4], if (i == 2) "加班夜宵" else "")

        }

        // Day -7..-13: sparser for week/month stats

        val sparse = listOf(

            7 to listOf(

                Triple(7, Period.FASTING, 5.9f),

                Triple(14, Period.LUNCH, 9.5f),

                Triple(22, Period.SLEEP, 6.4f)

            ),

            8 to listOf(

                Triple(7, Period.FASTING, 7.8f),

                Triple(9, Period.BREAKFAST, 13.5f),

                Triple(20, Period.DINNER, 10.8f)

            ),

            9 to listOf(

                Triple(6, Period.FASTING, 4.5f),

                Triple(13, Period.LUNCH, 8.0f),

                Triple(19, Period.DINNER, 8.6f),

                Triple(22, Period.SLEEP, 5.5f)

            ),

            10 to listOf(

                Triple(7, Period.FASTING, 5.1f),

                Triple(9, Period.BREAKFAST, 7.2f),

                Triple(19, Period.DINNER, 15.0f)

            ),

            11 to listOf(

                Triple(7, Period.FASTING, 6.6f),

                Triple(14, Period.LUNCH, 9.0f),

                Triple(22, Period.SLEEP, 7.0f)

            ),

            12 to listOf(

                Triple(7, Period.FASTING, 3.5f),

                Triple(9, Period.BREAKFAST, 8.8f),

                Triple(20, Period.DINNER, 9.3f),

                Triple(22, Period.SLEEP, 6.1f)

            ),

            13 to listOf(

                Triple(7, Period.FASTING, 5.4f),

                Triple(13, Period.LUNCH, 11.8f),

                Triple(19, Period.DINNER, 8.4f),

                Triple(22, Period.SLEEP, 5.8f)

            )

        )

        sparse.forEach { (d, rows) ->

            rows.forEachIndexed { idx, (hour, period, value) ->

                val note = when {

                    value >= 14f -> "调试：极高"

                    value < 4.0f -> "调试：偏低"

                    d == 13 && idx == 0 -> "两周前起点"

                    else -> ""

                }

                specs += Spec(d, hour, 5 + idx * 7, period, value, note)

            }

        }

        val created = nowMs

        for (s in specs) {

            val cal = Calendar.getInstance().apply {

                timeInMillis = todayStart

                add(Calendar.DAY_OF_YEAR, -s.dayOffset)

                set(Calendar.HOUR_OF_DAY, s.hour)

                set(Calendar.MINUTE, s.minute)

                set(Calendar.SECOND, 0)

                set(Calendar.MILLISECOND, 0)

            }

            val measure = cal.timeInMillis.coerceAtMost(nowMs)

            out += GlucoseRecord(

                id = "debug-${s.dayOffset}-${s.period.key}-${s.hour}${s.minute}",

                measureTime = measure,

                value = s.value,

                period = s.period.key,

                note = s.note,

                createdAt = created

            )

        }

        return out.sortedByDescending { it.measureTime }

    }

    /**

     * Multi-year bulk dataset for paging / Stats stress tests.

     * ~2.5 years ending [nowMs], ~4 readings/day across all periods (~3600 rows).

     */

    fun buildLargeRecords(nowMs: Long = System.currentTimeMillis()): List<GlucoseRecord> {

        val todayStart = startOfDay(nowMs)

        val days = 900 // ~2.5 years

        val slots = listOf(

            Period.FASTING to (7 to 5),

            Period.BREAKFAST to (9 to 20),

            Period.LUNCH to (13 to 40),

            Period.DINNER to (19 to 25),

            Period.SLEEP to (22 to 10)

        )

        val out = ArrayList<GlucoseRecord>(days * 4)

        val created = nowMs

        for (dayOffset in 0 until days) {

            // Skip ~1 in 7 days partially to look realistic (sick / missed)

            val skipSlot = dayOffset % 7

            slots.forEachIndexed { idx, (period, hm) ->

                if (idx == skipSlot) return@forEachIndexed

                // Mild jitter on minute

                val minute = (hm.second + (dayOffset * 3 + idx * 7) % 11) % 60

                val hour = hm.first

                // Deterministic value bands by period + day

                val base = when (period) {

                    Period.FASTING -> 5.2f

                    Period.BREAKFAST -> 7.5f

                    Period.LUNCH -> 8.2f

                    Period.DINNER -> 8.0f

                    Period.SLEEP -> 6.4f

                }

                val wave = ((dayOffset % 13) - 6) * 0.15f

                val spike = when {

                    dayOffset % 23 == 0 && period != Period.FASTING -> 4.5f // very high

                    dayOffset % 29 == 0 && period == Period.FASTING -> -2.0f // low

                    dayOffset % 11 == idx -> 2.2f // high

                    else -> ((dayOffset + idx * 5) % 5) * 0.25f

                }

                val value = (base + wave + spike).coerceIn(2.8f, 18.5f)

                val note = when {

                    value < 3.9f -> "\u504f\u4f4e"

                    value > 13f -> "\u504f\u9ad8"

                    dayOffset % 19 == 0 -> "\u8fd0\u52a8\u540e"

                    dayOffset % 17 == 0 -> "\u5916\u98df"

                    else -> ""

                }

                val cal = Calendar.getInstance().apply {

                    timeInMillis = todayStart

                    add(Calendar.DAY_OF_YEAR, -dayOffset)

                    set(Calendar.HOUR_OF_DAY, hour)

                    set(Calendar.MINUTE, minute)

                    set(Calendar.SECOND, 0)

                    set(Calendar.MILLISECOND, 0)

                }

                val measure = cal.timeInMillis.coerceAtMost(nowMs)

                out += GlucoseRecord(

                    id = "bulk-$dayOffset-${period.key}-$hour$minute",

                    measureTime = measure,

                    value = (Math.round(value * 10f) / 10f),

                    period = period.key,

                    note = note,

                    createdAt = created

                )

            }

        }

        return out.sortedByDescending { it.measureTime }

    }

}

