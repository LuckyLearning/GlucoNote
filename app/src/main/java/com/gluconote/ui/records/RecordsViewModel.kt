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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
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
 * \u8bb0\u5f55 Tab\uff1a\u6309\u9700\u5206\u9875\u52a0\u8f7d\u5386\u53f2\uff08\u4e0d\u4e00\u6b21 observeAll\uff09\u3002
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
    private var ignoreCountEvents: Int = 0

    init {
        refresh()
        viewModelScope.launch {
            repo.observeCount()
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    if (ignoreCountEvents > 0) {
                        ignoreCountEvents--
                        return@collect
                    }
                    softReload()
                }
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
        refresh()
    }

    fun refresh() {
        moreJob?.cancel()
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _ui.update {
                it.copy(initialLoading = true, loadingMore = false, endReached = false, items = emptyList())
            }
            val page = repo.pageAfter(
                period = _ui.value.filterPeriod,
                cursorTime = null,
                cursorId = null,
                limit = PAGE_SIZE
            )
            _ui.update {
                it.copy(
                    items = page,
                    initialLoading = false,
                    endReached = page.size < PAGE_SIZE
                )
            }
        }
    }

    private fun softReload() {
        moreJob?.cancel()
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val period = _ui.value.filterPeriod
            val target = max(_ui.value.items.size.coerceAtLeast(PAGE_SIZE), PAGE_SIZE)
            val acc = ArrayList<GlucoseRecord>(target)
            var cursorTime: Long? = null
            var cursorId: String? = null
            var end = false
            while (acc.size < target) {
                val page = repo.pageAfter(period, cursorTime, cursorId, PAGE_SIZE)
                if (page.isEmpty()) {
                    end = true
                    break
                }
                acc.addAll(page)
                if (page.size < PAGE_SIZE) {
                    end = true
                    break
                }
                cursorTime = page.last().measureTime
                cursorId = page.last().id
            }
            _ui.update {
                it.copy(
                    items = acc,
                    initialLoading = false,
                    loadingMore = false,
                    endReached = end
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
                val page = repo.pageAfter(
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
            ignoreCountEvents++
            repo.delete(r)
            _ui.update { state ->
                state.copy(items = state.items.filterNot { it.id == r.id })
            }
            onDeleted()
        }
    }

    companion object {
        const val PAGE_SIZE = 25
    }
}
