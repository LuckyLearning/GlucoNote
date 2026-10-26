package com.gluconote.ui.records

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gluconote.GlucoNoteApp
import com.gluconote.data.model.Period
import com.gluconote.data.repository.RecordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 新增 / 编辑记录页状态。
 * 血糖值、备注文本由界面本地 remember 持有，保存时一次性传入 [commit]，
 * 避免每敲一字触发整页重组。
 */
data class AddRecordUiState(
    val periodKey: String = Period.FASTING.key,
    val measureTime: Long = System.currentTimeMillis(),
    val editingId: String? = null,
    val editingInitialValue: String = "",
    val editingInitialNote: String = "",
    val ready: Boolean = false,
    val showDatePicker: Boolean = false,
    val showTimePicker: Boolean = false
)

class AddRecordViewModel(app: android.app.Application) : AndroidViewModel(app) {
    private val repo = RecordRepository((app as GlucoNoteApp).database)

    private val _ui = MutableStateFlow(AddRecordUiState())
    val ui: StateFlow<AddRecordUiState> = _ui.asStateFlow()

    /** [periodArg] 来自首页快捷入口；[idArg] 来自列表编辑。 */
    fun bootstrap(periodArg: String, idArg: String) {
        if (_ui.value.ready) return
        viewModelScope.launch {
            if (idArg.isNotBlank()) {
                val r = repo.getById(idArg)
                if (r != null) {
                    _ui.update {
                        it.copy(
                            editingId = r.id,
                            editingInitialValue = r.value.toString(),
                            editingInitialNote = r.note,
                            periodKey = r.period,
                            measureTime = r.measureTime,
                            ready = true
                        )
                    }
                    return@launch
                }
            }
            _ui.update {
                it.copy(
                    periodKey = if (periodArg.isNotBlank()) periodArg else Period.FASTING.key,
                    measureTime = System.currentTimeMillis(),
                    ready = true
                )
            }
        }
    }

    fun setPeriod(key: String) = _ui.update { it.copy(periodKey = key) }
    fun setMeasureTime(ts: Long) = _ui.update { it.copy(measureTime = ts) }
    fun showDatePicker(s: Boolean) = _ui.update { it.copy(showDatePicker = s) }
    fun showTimePicker(s: Boolean) = _ui.update { it.copy(showTimePicker = s) }

    fun commit(value: Float, note: String, onSaved: () -> Unit) {
        val s = _ui.value
        viewModelScope.launch {
            repo.upsert(value, s.periodKey, s.measureTime, note, s.editingId)
            onSaved()
        }
    }
}
