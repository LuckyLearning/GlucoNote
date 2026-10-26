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
import kotlinx.coroutines.launch

data class RecordsUiState(
    val records: List<GlucoseRecord> = emptyList(),
    val settings: Settings = Settings(),
    val periodKey: String = Period.FASTING.key,
    val measureTime: Long = System.currentTimeMillis(),
    val valueText: String = "",
    val note: String = "",
    val editingId: String? = null,
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
    fun setValue(t: String) = _ui.update { it.copy(valueText = t) }
    fun setNote(t: String) = _ui.update { it.copy(note = t) }
    fun setMeasureTime(ts: Long) = _ui.update { it.copy(measureTime = ts) }
    fun showDatePicker(s: Boolean) = _ui.update { it.copy(showDatePicker = s) }
    fun showTimePicker(s: Boolean) = _ui.update { it.copy(showTimePicker = s) }

    fun startEdit(r: GlucoseRecord) = _ui.update {
        it.copy(
            editingId = r.id,
            periodKey = r.period,
            measureTime = r.measureTime,
            valueText = r.value.toString(),
            note = r.note
        )
    }

    fun reset() = _ui.update {
        it.copy(
            editingId = null,
            periodKey = Period.FASTING.key,
            measureTime = System.currentTimeMillis(),
            valueText = "",
            note = ""
        )
    }

    fun commit(value: Float, onSaved: () -> Unit) {
        val s = _ui.value
        viewModelScope.launch {
            repo.upsert(value, s.periodKey, s.measureTime, s.note, s.editingId)
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
