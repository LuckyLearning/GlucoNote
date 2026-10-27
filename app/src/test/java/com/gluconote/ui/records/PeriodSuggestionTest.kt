package com.gluconote.ui.records

import com.gluconote.data.model.Period
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/**
 * 只测纯逻辑：推荐时段按钟点映射。
 * 步骤闸门 [AddRecordViewModel.advanceFrom]/[AddRecordViewModel.backFrom] 依赖 AndroidViewModel，
 * 由真机手工用例覆盖（连点「下一步」不跳步）。
 */
class PeriodSuggestionTest {

    private fun at(hour: Int): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun earlyMorning_isSleep() {
        assertEquals(Period.SLEEP.key, AddRecordViewModel.suggestPeriodKey(at(0)))
        assertEquals(Period.SLEEP.key, AddRecordViewModel.suggestPeriodKey(at(4)))
    }

    @Test
    fun fastingWindow_is5to8() {
        assertEquals(Period.FASTING.key, AddRecordViewModel.suggestPeriodKey(at(5)))
        assertEquals(Period.FASTING.key, AddRecordViewModel.suggestPeriodKey(at(8)))
    }

    @Test
    fun breakfastWindow_is9to11() {
        assertEquals(Period.BREAKFAST.key, AddRecordViewModel.suggestPeriodKey(at(9)))
        assertEquals(Period.BREAKFAST.key, AddRecordViewModel.suggestPeriodKey(at(11)))
    }

    @Test
    fun lunchWindow_is12to14() {
        assertEquals(Period.LUNCH.key, AddRecordViewModel.suggestPeriodKey(at(12)))
        assertEquals(Period.LUNCH.key, AddRecordViewModel.suggestPeriodKey(at(14)))
    }

    @Test
    fun dinnerWindow_is15to19() {
        assertEquals(Period.DINNER.key, AddRecordViewModel.suggestPeriodKey(at(15)))
        assertEquals(Period.DINNER.key, AddRecordViewModel.suggestPeriodKey(at(19)))
    }

    @Test
    fun lateNight_isSleep() {
        assertEquals(Period.SLEEP.key, AddRecordViewModel.suggestPeriodKey(at(20)))
        assertEquals(Period.SLEEP.key, AddRecordViewModel.suggestPeriodKey(at(23)))
    }
}
