package com.gluconote.ui.stats

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gluconote.GlucoNoteApp
import com.gluconote.data.model.GlucoseRecord
import com.gluconote.data.model.Settings
import com.gluconote.data.repository.RecordRepository
import com.gluconote.data.repository.SettingsRepository
import com.gluconote.util.Granularity
import com.gluconote.util.RangeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class StatsUiState(
    val records: List<GlucoseRecord> = emptyList(),
    val settings: Settings = Settings(),
    val filterPeriod: String? = null,
    val rangeType: RangeType = RangeType.DAY,
    val granularity: Granularity = Granularity.MONTH
)

class StatsViewModel(app: android.app.Application) : AndroidViewModel(app) {
    private val repo = RecordRepository((app as GlucoNoteApp).database)
    private val settingsRepo = SettingsRepository(app)

    private val _ui = MutableStateFlow(StatsUiState())
    val ui: StateFlow<StatsUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repo.observeAll(), settingsRepo.settingsFlow) { recs, set -> recs to set }
                .collect { (recs, set) ->
                    _ui.update { it.copy(records = recs, settings = set) }
                }
        }
    }

    fun setFilter(period: String?) = _ui.update { it.copy(filterPeriod = period) }
    fun setRangeType(t: RangeType) = _ui.update { it.copy(rangeType = t) }
    fun setGranularity(g: Granularity) = _ui.update { it.copy(granularity = g) }
}
