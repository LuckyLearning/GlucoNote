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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val todayCount: Int = 0,
    val lastValue: Float? = null,
    val lastPeriod: String = "",
    val weekAvg: Float = 0f,
    val recent: List<GlucoseRecord> = emptyList(),
    val settings: Settings = Settings()
)

class HomeViewModel(app: android.app.Application) : AndroidViewModel(app) {
    private val repo = RecordRepository((app as GlucoNoteApp).database)
    private val settingsRepo = SettingsRepository(app)

    val uiState = combine(
        repo.observeAll(),
        settingsRepo.settingsFlow
    ) { records, settings ->
        val startToday = startOfDay(System.currentTimeMillis())
        val today = records.filter { it.measureTime >= startToday }
        val weekAgo = startToday - 7 * DAY_MS
        val week = records.filter { it.measureTime >= weekAgo }
        val weekAvg = if (week.isEmpty()) 0f else week.map { it.value }.average().toFloat()
        HomeUiState(
            todayCount = today.size,
            lastValue = records.firstOrNull()?.value,
            lastPeriod = records.firstOrNull()?.period ?: "",
            weekAvg = weekAvg,
            recent = records.take(5),
            settings = settings
        )
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** 删除一条记录（首页「最近」列表右侧删除按钮） */
    fun delete(r: GlucoseRecord) {
        viewModelScope.launch { repo.delete(r) }
    }
}
