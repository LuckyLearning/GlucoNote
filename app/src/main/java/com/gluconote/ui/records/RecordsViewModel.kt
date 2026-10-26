package com.gluconote.ui.records

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gluconote.GlucoNoteApp
import com.gluconote.data.model.GlucoseRecord
import com.gluconote.data.model.Period
import com.gluconote.data.model.Settings
import com.gluconote.data.repository.RecordRepository
import com.gluconote.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 记录页状态。
 *
 * 性能要点：血糖值 / 备注这类逐字输入的文本**不放进 StateFlow**——
 * 放进来意味着每敲一个字整个页面（含历史列表）都重组一次，输入一多就卡。
 * 文本由界面用本地 remember 状态持有，只在保存时一次性传入 [commit]。
 * [editingInitialValue] / [editingInitialNote] 仅作为进入编辑时的一次性初值。
 */
data class RecordsUiState(
    val records: List<GlucoseRecord> = emptyList(),
    val settings: Settings = Settings(),
    val periodKey: String = Period.FASTING.key,
    val measureTime: Long = System.currentTimeMillis(),
    val editingId: String? = null,
    val editingInitialValue: String = "",
    val editingInitialNote: String = "",
    val showDatePicker: Boolean = false,
    val showTimePicker: Boolean = false
)

class RecordsViewModel(app: android.app.Application) : AndroidViewModel(app) {
    private val repo = RecordRepository((app as GlucoNoteApp).database)
    private val settingsRepo = SettingsRepository(app)

    private val _ui = MutableStateFlow(RecordsUiState())
    val ui: StateFlow<RecordsUiState> = _ui.asStateFlow()

    val records: StateFlow<List<GlucoseRecord>> = repo.observeAll()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<Settings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), Settings())

    fun initPeriod(key: String) {
        if (key.isNotBlank()) _ui.update { it.copy(periodKey = key) }
    }

    fun setPeriod(key: String) = _ui.update { it.copy(periodKey = key) }
    fun setMeasureTime(ts: Long) = _ui.update { it.copy(measureTime = ts) }
    fun showDatePicker(s: Boolean) = _ui.update { it.copy(showDatePicker = s) }
    fun showTimePicker(s: Boolean) = _ui.update { it.copy(showTimePicker = s) }

    fun startEdit(r: GlucoseRecord) = _ui.update {
        it.copy(
            editingId = r.id,
            editingInitialValue = r.value.toString(),
            editingInitialNote = r.note,
            periodKey = r.period,
            measureTime = r.measureTime
        )
    }

    fun reset() = _ui.update {
        it.copy(
            editingId = null,
            editingInitialValue = "",
            editingInitialNote = "",
            periodKey = Period.FASTING.key,
            measureTime = System.currentTimeMillis()
        )
    }

    fun commit(value: Float, note: String, onSaved: () -> Unit) {
        val s = _ui.value
        viewModelScope.launch {
            repo.upsert(value, s.periodKey, s.measureTime, note, s.editingId)
            reset()
            onSaved()
        }
    }

    fun delete(r: GlucoseRecord, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repo.delete(r)
            onDeleted()
        }
    }
}
