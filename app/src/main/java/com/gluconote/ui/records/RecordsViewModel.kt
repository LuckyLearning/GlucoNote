package com.gluconote.ui.records

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gluconote.GlucoNoteApp
import com.gluconote.data.model.GlucoseRecord
import com.gluconote.data.model.Settings
import com.gluconote.data.repository.RecordRepository
import com.gluconote.data.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max

data class RecordsUiState(
    val items: List<GlucoseRecord> = emptyList(),
    val filterPeriod: String? = null,
    val initialLoading: Boolean = true,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false
)

/**
 * 记录 Tab：keyset 分页 + 表失效通知驱动刷新。
 *
 * 两个改造点：
 * 1. **刷新信号从「总数」换成 Room 的表失效流**。只靠总数的话，编辑一条记录时
 *    数目不变 → 列表显示的还是旧值；自己写 `COUNT(*) + MAX(createdAt)` 令牌虽然能
 *    覆盖编辑，但 20 万行时每次要 38 ms（`createdAt` 无索引 → 全表扫描），
 *    `InvalidationTracker.createFlow` 是零查询开销的。
 * 2. **重载不再逐页重放**。旧实现为了保住「已加载 N 条」会循环发 N/25 次分页查询；
 *    现在直接一条 `LIMIT N` 的首屏查询，条数不变、结果一致，查询次数从 O(N/25) 降到 1。
 */
class RecordsViewModel(app: android.app.Application) : AndroidViewModel(app) {
    private val repo = RecordRepository((app as GlucoNoteApp).database)
    private val settingsRepo = SettingsRepository(app)

    private val _ui = MutableStateFlow(RecordsUiState())
    val ui: StateFlow<RecordsUiState> = _ui.asStateFlow()

    val settings: StateFlow<Settings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    private var refreshJob: Job? = null
    private var moreJob: Job? = null

    init {
        reload(resetItems = true)
        viewModelScope.launch {
            // 失效流没有"初始快照"概念，首屏已由上面的 reload 完成，直接收集即可
            repo.observeRecordsChanged().collect { reload(resetItems = false) }
        }
    }

    fun setFilter(period: String?) {
        if (_ui.value.filterPeriod == period) return
        _ui.update {
            it.copy(
                filterPeriod = period,
                items = emptyList(),
                initialLoading = true,
                loadingMore = false,
                endReached = false
            )
        }
        reload(resetItems = true)
    }

    fun refresh() = reload(resetItems = true)

    /**
     * @param resetItems true = 回到第一页（换筛选、下拉刷新）；
     *                   false = 保住已加载条数，一条 SQL 重取（表变更后静默校正）。
     */
    private fun reload(resetItems: Boolean) {
        moreJob?.cancel()
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val period = _ui.value.filterPeriod
            if (resetItems) {
                _ui.update {
                    it.copy(
                        items = emptyList(),
                        initialLoading = true,
                        loadingMore = false,
                        endReached = false
                    )
                }
            }
            // 非重置时按当前已加载条数重取，避免用户滚动位置被"拉回顶部"
            val limit = if (resetItems) PAGE_SIZE else max(_ui.value.items.size, PAGE_SIZE)
            val page = repo.page(period, cursorTime = null, cursorId = null, limit = limit)
            _ui.update {
                it.copy(
                    items = page,
                    initialLoading = false,
                    loadingMore = false,
                    endReached = page.size < limit
                )
            }
        }
    }

    fun loadNext() {
        val snap = _ui.value
        if (snap.initialLoading || snap.loadingMore || snap.endReached) return
        if (moreJob?.isActive == true) return
        val last = snap.items.lastOrNull() ?: return
        moreJob = viewModelScope.launch {
            _ui.update { it.copy(loadingMore = true) }
            try {
                val page = repo.page(
                    period = snap.filterPeriod,
                    cursorTime = last.measureTime,
                    cursorId = last.id,
                    limit = PAGE_SIZE
                )
                _ui.update {
                    it.copy(
                        items = it.items + page,
                        loadingMore = false,
                        endReached = page.size < PAGE_SIZE
                    )
                }
            } catch (e: CancellationException) {
                _ui.update { it.copy(loadingMore = false) }
                throw e
            } catch (_: Exception) {
                _ui.update { it.copy(loadingMore = false) }
            }
        }
    }

    fun delete(r: GlucoseRecord, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            // 先本地移除（点哪条哪条立刻消失），随后令牌变化会静默重取校正
            _ui.update { state ->
                state.copy(items = state.items.filterNot { it.id == r.id })
            }
            repo.delete(r)
            onDeleted()
        }
    }

    companion object {
        const val PAGE_SIZE = 25
    }
}
