package com.gluconote.ui.records

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gluconote.GlucoNoteApp
import com.gluconote.data.model.Period
import com.gluconote.data.model.Settings
import com.gluconote.data.repository.RecordRepository
import com.gluconote.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 新增 / 编辑记录页状态。
 * 新增走 4 步适老引导；编辑仍用单页表单。
 * 血糖值、备注文本由界面本地 remember 持有，保存时一次性传入 [commit]。
 */
data class AddRecordUiState(
    val periodKey: String = Period.FASTING.key,
    val measureTime: Long = System.currentTimeMillis(),
    val editingId: String? = null,
    val editingInitialValue: String = "",
    val editingInitialNote: String = "",
    val ready: Boolean = false,
    val showDatePicker: Boolean = false,
    /** 引导步骤 0 选状态 / 1 选日期 / 2 选时间 / 3 血糖值；编辑模式忽略 */
    val step: Int = 0,
    /** 按当前钟点推算的推荐标签，界面上打「推荐」角标 */
    val recommendedPeriodKey: String = Period.FASTING.key,
    val settings: Settings = Settings()
)

class AddRecordViewModel(app: android.app.Application) : AndroidViewModel(app) {
    private val repo = RecordRepository((app as GlucoNoteApp).database)
    private val settingsRepo = SettingsRepository(app)

    private val _ui = MutableStateFlow(AddRecordUiState())
    val ui: StateFlow<AddRecordUiState> = _ui.asStateFlow()

    /** [idArg] 非空表示编辑已有记录。 */
    fun bootstrap(idArg: String) {
        if (_ui.value.ready) return
        viewModelScope.launch {
            val settings = runCatching { settingsRepo.settingsFlow.first() }.getOrDefault(Settings())
            val recommended = suggestPeriodKey()
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
                            recommendedPeriodKey = recommended,
                            settings = settings,
                            ready = true
                        )
                    }
                    return@launch
                }
            }
            _ui.update {
                it.copy(
                    periodKey = recommended,
                    measureTime = System.currentTimeMillis(),
                    step = 0,
                    recommendedPeriodKey = recommended,
                    settings = settings,
                    ready = true
                )
            }
        }
    }

    fun setPeriod(key: String) = _ui.update { it.copy(periodKey = key) }
    fun setMeasureTime(ts: Long) = _ui.update { it.copy(measureTime = ts) }
    fun showDatePicker(s: Boolean) = _ui.update { it.copy(showDatePicker = s) }

    /**
     * 步骤闸门（前进）：只有 [expected] 与当前 step 一致时才前进。
     *
     * 连点两下「下一步」、或快速连击时，第二下会被直接丢弃，不会"跳步"
     * （旧实现只做 coerceAtMost，连点会一次跳两级、跳过选日期）。
     * 它同时是界面判定转场方向的唯一依据。
     *
     * @return true 表示确实前进了
     */
    fun advanceFrom(expected: Int): Boolean {
        val s = _ui.value
        if (s.step != expected || expected >= LAST_STEP) return false
        _ui.update { it.copy(step = expected + 1) }
        return true
    }

    /**
     * 步骤闸门（后退）。
     *
     * @return true 表示已经在第一步、应当退出页面
     */
    fun backFrom(expected: Int): Boolean {
        val s = _ui.value
        if (s.step != expected) return false
        if (expected <= 0) return true
        _ui.update { it.copy(step = expected - 1) }
        return false
    }

    fun setDateToToday() {
        mergeDateFrom(System.currentTimeMillis())
    }

    fun setDateToYesterday() {
        mergeDateFrom(System.currentTimeMillis() - 24L * 60 * 60 * 1000)
    }

    fun setTimeToNow() {
        val now = Calendar.getInstance()
        val merged = Calendar.getInstance().apply {
            timeInMillis = _ui.value.measureTime
            set(Calendar.HOUR_OF_DAY, now.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, now.get(Calendar.MINUTE))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        setMeasureTime(merged.timeInMillis)
    }

    fun setHourMinute(hour: Int, minute: Int) {
        val merged = Calendar.getInstance().apply {
            timeInMillis = _ui.value.measureTime
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        setMeasureTime(merged.timeInMillis)
    }

    private fun mergeDateFrom(dayMillis: Long) {
        val src = Calendar.getInstance().apply { timeInMillis = _ui.value.measureTime }
        val merged = Calendar.getInstance().apply {
            timeInMillis = dayMillis
            set(Calendar.HOUR_OF_DAY, src.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, src.get(Calendar.MINUTE))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        setMeasureTime(merged.timeInMillis)
    }

    fun commit(value: Float, note: String, onSaved: () -> Unit) {
        val s = _ui.value
        viewModelScope.launch {
            repo.upsert(value, s.periodKey, s.measureTime, note, s.editingId)
            onSaved()
        }
    }

    companion object {
        /** 引导最后一步（血糖值） */
        const val LAST_STEP = 3

        /** 按当前钟点推荐测量标签（对齐适老原型启发式，映射到 App 现有 Period）。 */
        fun suggestPeriodKey(nowMillis: Long = System.currentTimeMillis()): String {
            val h = Calendar.getInstance().apply { timeInMillis = nowMillis }
                .get(Calendar.HOUR_OF_DAY)
            return when {
                h in 5..8 -> Period.FASTING.key
                h in 9..11 -> Period.BREAKFAST.key
                h in 12..14 -> Period.LUNCH.key
                h in 15..19 -> Period.DINNER.key
                else -> Period.SLEEP.key
            }
        }
    }
}
