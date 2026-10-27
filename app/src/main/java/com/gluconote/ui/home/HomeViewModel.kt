package com.gluconote.ui.home

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gluconote.GlucoNoteApp
import com.gluconote.data.model.GlucoseRecord
import com.gluconote.data.model.Settings
import com.gluconote.data.repository.RecordRepository
import com.gluconote.data.repository.SettingsRepository
import com.gluconote.util.DAY_MS
import com.gluconote.util.startOfDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val todayCount: Int = 0,
    val lastValue: Float? = null,
    val lastPeriod: String = "",
    val lastTime: Long? = null,
    val weekAvg: Float = 0f,
    val recent: List<GlucoseRecord> = emptyList(),
    val settings: Settings = Settings()
)

/**
 * 首页数据。
 *
 * 改造前：`repo.observeAll()` 把整表读进内存，然后每来一条变更就全表重算
 * 「今日条数 / 近 7 天均值 / 最近一条 / 最近 5 条」——20 万行时每次约 286 ms。
 *
 * 改造后：四个**索引支撑的**小查询各自订阅，`observeAll` 彻底移除：
 * - `observeHomeAgg`：一条 SQL 同时拿今日条数与近 7 天均值（7 天窗口 ≈ 35 行）；
 * - `observeLatest` / `observeRecent5`：keyset 倒序 `LIMIT`，走 `idx_measure_time_id`。
 */
class HomeViewModel(app: android.app.Application) : AndroidViewModel(app) {
    private val repo = RecordRepository((app as GlucoNoteApp).database)
    private val settingsRepo = SettingsRepository(app)

    /** 「今日 / 近 7 天」的时间窗；跨天后需要重新设定，所以带一个低频心跳 */
    private data class Windows(val todayStart: Long, val weekAgo: Long)

    private val windows: Flow<Windows> = flow {
        while (true) {
            val todayStart = startOfDay(System.currentTimeMillis())
            emit(Windows(todayStart, todayStart - 7 * DAY_MS))
            delay(WINDOW_REFRESH_MS)
        }
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState = windows
        .flatMapLatest { w ->
            // 窗口参数变了才重新订阅一次（实际一天一次），避免每次心跳都重查
            combine(
                repo.observeHomeAgg(w.todayStart, w.weekAgo),
                repo.observeLatest(),
                repo.observeRecent5(),
                settingsRepo.settingsFlow
            ) { agg, latest, recent, settings ->
                HomeUiState(
                    todayCount = agg.todayCount,
                    lastValue = latest?.value,
                    lastPeriod = latest?.period ?: "",
                    lastTime = latest?.measureTime,
                    weekAvg = agg.weekAvg ?: 0f,
                    recent = recent,
                    settings = settings
                )
            }
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** 删除一条记录（首页「最近」列表右侧删除按钮） */
    fun delete(r: GlucoseRecord) {
        viewModelScope.launch { repo.delete(r) }
    }

    private companion object {
        /** 跨天心跳间隔：够及时，又不至于频繁重订阅 */
        const val WINDOW_REFRESH_MS = 60_000L
    }
}
