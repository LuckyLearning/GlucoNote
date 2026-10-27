package com.gluconote.ui.stats

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gluconote.GlucoNoteApp
import com.gluconote.data.model.Period
import com.gluconote.data.model.Settings
import com.gluconote.data.repository.RecordRepository
import com.gluconote.data.repository.SettingsRepository
import com.gluconote.util.Bucket
import com.gluconote.util.DAY_MS
import com.gluconote.util.Granularity
import com.gluconote.util.RangeType
import com.gluconote.util.RateResult
import com.gluconote.util.StatsResult
import com.gluconote.util.TrendPoint
import com.gluconote.util.computeBucketStats
import com.gluconote.util.computeRangeStats
import com.gluconote.util.computeRateByPeriod
import com.gluconote.util.computeTrend
import com.gluconote.util.scopedRecords
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class StatsUiState(
    val settings: Settings = Settings(),
    val filterPeriod: String? = null,
    /** 趋势图默认展示最近 7 天 */
    val rangeType: RangeType = RangeType.WEEK,
    val granularity: Granularity = Granularity.MONTH,
    val filterPeriodLabel: String? = null,
    val ratePeriods: List<Period> = Period.all,
    val trend: List<TrendPoint> = emptyList(),
    val rateMap: Map<Period, RateResult> = emptyMap(),
    val buckets: List<Bucket> = emptyList(),
    val range7: StatsResult = StatsResult(0f, 0f, 0f, 0f, 0),
    val range30: StatsResult = StatsResult(0f, 0f, 0f, 0f, 0)
)

class StatsViewModel(app: android.app.Application) : AndroidViewModel(app) {
    private val repo = RecordRepository((app as GlucoNoteApp).database)
    private val settingsRepo = SettingsRepository(app)

    private val filterPeriod = MutableStateFlow<String?>(null)
    // 默认「近 7 天」：一天的数据点太少，趋势几乎没有意义
    private val rangeType = MutableStateFlow(RangeType.WEEK)
    private val granularity = MutableStateFlow(Granularity.MONTH)

    val ui: StateFlow<StatsUiState> = combine(
        repo.observeAll(),
        settingsRepo.settingsFlow,
        filterPeriod,
        rangeType,
        granularity
    ) { records, settings, period, range, gran ->
        val now = System.currentTimeMillis()
        // 标签筛选（趋势/聚合/区间卡仍按标签；达标率再叠加时间窗）
        val filtered = if (period != null) records.filter { it.period == period } else records
        val scoped = scopedRecords(records, range, filterPeriod = period, now = now)
        StatsUiState(
            settings = settings,
            filterPeriod = period,
            rangeType = range,
            granularity = gran,
            filterPeriodLabel = period?.let { Period.fromKey(it).plainLabel() },
            ratePeriods = if (period != null) listOf(Period.fromKey(period)) else Period.all,
            trend = computeTrend(filtered, range, filterPeriod = null, now = now),
            rateMap = computeRateByPeriod(scoped, settings),
            buckets = computeBucketStats(filtered, gran, settings, filterPeriod = null),
            range7 = computeRangeStats(filtered, now - 7 * DAY_MS, now, filterPeriod = null),
            range30 = computeRangeStats(filtered, now - 30 * DAY_MS, now, filterPeriod = null)
        )
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun setFilter(period: String?) = filterPeriod.update { period }
    fun setRangeType(t: RangeType) = rangeType.update { t }
    fun setGranularity(g: Granularity) = granularity.update { g }
}
