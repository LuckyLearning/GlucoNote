package com.gluconote.ui.stats

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gluconote.GlucoNoteApp
import com.gluconote.data.local.RangeStatsRow
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
import com.gluconote.util.bucketWindowStart
import com.gluconote.util.computeBucketStats
import com.gluconote.util.formatDate
import com.gluconote.util.rangeWindowStart
import com.gluconote.util.shouldDownsample
import com.gluconote.util.statsFromMoments
import com.gluconote.util.trendLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.util.TimeZone

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

/**
 * 一次统计查询的全部输入条件。
 *
 * 相同条件不重复查（`distinctUntilChanged`），条件变化时用 `flatMapLatest`
 * **取消上一次查询** —— 否则快速连点「近 7 天 / 近 30 天 / 近 1 年」时，
 * 旧查询会排队返回，画面先闪回旧数据再跳到新数据。
 *
 * [revision] 是「表被写过几次」的计数：同样条件下数据变了也要重查。
 */
private data class StatsQuery(
    val period: String?,
    val range: RangeType,
    val gran: Granularity,
    val settings: Settings,
    val revision: Long
)

/**
 * 统计分析页。
 *
 * 改造前：`repo.observeAll()` 拉全表，再在内存里做 5 遍过滤 + 聚合
 * （趋势 / 5 个标签达标率 / 日-月-年分桶 / 7 天 / 30 天）。
 * 20 万行时每次参数变化约 286 ms，且全部发生在 UI 侧协程。
 *
 * 改造后：
 * - 达标率、区间聚合、趋势取数全部下推给 SQL（只回传结果行，合计 ~16 ms）；
 * - 只有「日/月/年分桶」留在 Kotlin，且输入窗口强制限制（60 天 / 24 个月 / 10 年）；
 * - 趋势点数 > 400 自动按自然日降采样（1 年 × 5 次/天 = 1825 个点，逐个画数值标签必然糊）；
 * - 变更令牌驱动重查，编辑记录也能刷新（旧版靠总数变化，编辑后数字不更新）。
 */
class StatsViewModel(app: android.app.Application) : AndroidViewModel(app) {
    private val repo = RecordRepository((app as GlucoNoteApp).database)
    private val settingsRepo = SettingsRepository(app)

    private val filterPeriod = MutableStateFlow<String?>(null)
    // 默认「近 7 天」：一天的数据点太少，趋势几乎没有意义
    private val rangeType = MutableStateFlow(RangeType.WEEK)
    private val granularity = MutableStateFlow(Granularity.MONTH)

    @OptIn(ExperimentalCoroutinesApi::class)
    val ui: StateFlow<StatsUiState> = combine(
        filterPeriod,
        rangeType,
        granularity,
        settingsRepo.settingsFlow,
        repo.observeRecordsChanged().scan(0L) { n, _ -> n + 1 }
    ) { period, range, gran, settings, revision ->
        StatsQuery(period, range, gran, settings, revision)
    }
        .distinctUntilChanged()
        .flatMapLatest { q -> flow { emit(loadStats(q)) } }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun setFilter(period: String?) = filterPeriod.update { period }
    fun setRangeType(t: RangeType) = rangeType.update { t }
    fun setGranularity(g: Granularity) = granularity.update { g }

    // ------------------------------------------------------------------ 查询

    private suspend fun loadStats(q: StatsQuery): StatsUiState {
        val now = System.currentTimeMillis()
        val settings = q.settings
        val period = q.period
        // 通达率 / 趋势 / 区间卡共用同一个时间窗（与旧版 scopedRecords 一致）
        val from = rangeWindowStart(q.range, now)

        val rateMap = repo.rateByPeriod(
            from = from,
            to = now,
            period = period,
            fMin = settings.fastingMin,
            fMax = settings.fastingMax,
            pMin = settings.postMin,
            pMax = settings.postMax
        ).associate { Period.fromKey(it.period) to RateResult(it.total, it.inRange) }

        return StatsUiState(
            settings = settings,
            filterPeriod = period,
            rangeType = q.range,
            granularity = q.gran,
            filterPeriodLabel = period?.let { Period.fromKey(it).plainLabel() },
            ratePeriods = if (period != null) listOf(Period.fromKey(period)) else Period.all,
            trend = buildTrend(q.range, from, now, period),
            rateMap = rateMap,
            buckets = buildBuckets(q.gran, now, period, settings),
            range7 = statsOf(repo.rangeStats(now - 7 * DAY_MS, now, period)),
            range30 = statsOf(repo.rangeStats(now - 30 * DAY_MS, now, period))
        )
    }

    /**
     * 趋势点。先查条数决定要不要降采样，避免「先拉原始点再丢掉」的白跑一趟。
     */
    private suspend fun buildTrend(
        range: RangeType,
        from: Long,
        now: Long,
        period: String?
    ): List<TrendPoint> {
        val count = repo.countBetween(from, now, period)
        if (count == 0) return emptyList()

        if (shouldDownsample(count)) {
            // 按本地自然日聚合：tzShift 用当前时区偏移把 UTC 毫秒对齐到本地日边界
            val tzShift = TimeZone.getDefault().getOffset(now).toLong()
            return repo.dailyTrend(from, now, period, tzShift).map { d ->
                // 用桶内最早一条的时间戳做标签，显示的是本地日期而不是 UTC 日期的前一天
                TrendPoint(
                    ts = d.firstTs,
                    value = d.valueAvg,
                    label = formatDate(d.firstTs),
                    period = ""
                )
            }
        }

        return repo.trendRows(from, now, period).map { r ->
            TrendPoint(
                ts = r.measureTime,
                value = r.value,
                label = trendLabel(range, r.measureTime),
                period = r.period
            )
        }
    }

    /**
     * 日/月/年分桶。留在 Kotlin（`computeBucketStats` 是纯函数、好测），
     * 但输入窗口必须限死，否则「按日」等于把整表读进内存。
     */
    private suspend fun buildBuckets(
        gran: Granularity,
        now: Long,
        period: String?,
        settings: Settings
    ): List<Bucket> {
        val from = bucketWindowStart(gran, now)
        val records = repo.getBetweenFiltered(from, now, period)
        return computeBucketStats(records, gran, settings)
    }

    private fun statsOf(row: RangeStatsRow): StatsResult = statsFromMoments(
        count = row.count,
        valueAvg = row.valueAvg,
        valueMax = row.valueMax,
        valueMin = row.valueMin,
        valueAvgSq = row.valueAvgSq
    )
}
