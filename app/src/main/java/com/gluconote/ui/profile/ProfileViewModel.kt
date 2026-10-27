package com.gluconote.ui.profile

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gluconote.BuildConfig
import com.gluconote.GlucoNoteApp
import com.gluconote.data.model.Settings
import com.gluconote.data.model.ThemeMode
import com.gluconote.data.repository.RecordRepository
import com.gluconote.data.repository.SettingsRepository
import com.gluconote.debug.DebugSeedData
import com.gluconote.util.exportCsv
import com.gluconote.util.parseCsv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val settings: Settings = Settings(),
    val fastingMinText: String = "",
    val fastingMaxText: String = "",
    val postMinText: String = "",
    val postMaxText: String = ""
)

class ProfileViewModel(app: android.app.Application) : AndroidViewModel(app) {
    private val settingsRepo = SettingsRepository(app)
    private val repo = RecordRepository((app as GlucoNoteApp).database)

    private val _ui = MutableStateFlow(ProfileUiState())
    val ui: StateFlow<ProfileUiState> = _ui.asStateFlow()

    /** 只在首次收到设置时把数值灌进输入框；之后（比如改了主题）不要覆盖用户正在编辑的内容 */
    private var textSeeded = false

    init {
        viewModelScope.launch {
            settingsRepo.settingsFlow.collect { s ->
                _ui.update { prev ->
                    if (!textSeeded) {
                        textSeeded = true
                        prev.copy(
                            settings = s,
                            fastingMinText = s.fastingMin.toString(),
                            fastingMaxText = s.fastingMax.toString(),
                            postMinText = s.postMin.toString(),
                            postMaxText = s.postMax.toString()
                        )
                    } else {
                        prev.copy(settings = s)
                    }
                }
            }
        }
    }

    /** 切换外观主题（浅色 / 深色 / 跟随系统），立即生效并持久化 */
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepo.saveThemeMode(mode) }
    }

    fun setFastingMin(t: String) = _ui.update { it.copy(fastingMinText = t) }
    fun setFastingMax(t: String) = _ui.update { it.copy(fastingMaxText = t) }
    fun setPostMin(t: String) = _ui.update { it.copy(postMinText = t) }
    fun setPostMax(t: String) = _ui.update { it.copy(postMaxText = t) }

    fun save(onSaved: () -> Unit, onError: (String) -> Unit = {}) {
        val s = _ui.value
        val fMin = s.fastingMinText.toFloatOrNull()
        val fMax = s.fastingMaxText.toFloatOrNull()
        val pMin = s.postMinText.toFloatOrNull()
        val pMax = s.postMaxText.toFloatOrNull()
        if (fMin == null || fMax == null || pMin == null || pMax == null) {
            onError("请输入有效目标值")
            return
        }
        if (fMin >= fMax || pMin >= pMax) {
            onError("下限须小于上限")
            return
        }
        if (listOf(fMin, fMax, pMin, pMax).any { it < 1f || it > 33f }) {
            onError("目标值须在 1–33 mmol/L")
            return
        }
        val ns = Settings(
            fastingMin = fMin,
            fastingMax = fMax,
            postMin = pMin,
            postMax = pMax,
            lowThreshold = s.settings.lowThreshold
        )
        viewModelScope.launch {
            settingsRepo.save(ns)
            onSaved()
        }
    }

    suspend fun exportText(): String = exportCsv(repo.getAll())

    suspend fun importText(text: String): Int {
        val list = parseCsv(text)
        list.forEach { repo.insert(it) }
        return list.size
    }

    /**
     * Debug only: replace all records with [DebugSeedData] and reset target ranges.
     * Returns inserted count, or -1 if called from a non-debug build.
     */
    suspend fun loadDebugData(): Int = withContext(Dispatchers.IO) {
        if (!BuildConfig.DEBUG) return@withContext -1
        val records = DebugSeedData.buildRecords()
        repo.clear()
        repo.insertAll(records)
        settingsRepo.save(DebugSeedData.demoSettings)
        records.size
    }

    /** Debug only: ~2.5y bulk history for paging stress tests. */
    suspend fun loadLargeDebugData(): Int = withContext(Dispatchers.IO) {
        if (!BuildConfig.DEBUG) return@withContext -1
        val records = DebugSeedData.buildLargeRecords()
        repo.clear()
        repo.insertAll(records)
        settingsRepo.save(DebugSeedData.demoSettings)
        records.size
    }

    /** Debug only: wipe all glucose records. */
    suspend fun clearAllData(): Boolean {
        if (!BuildConfig.DEBUG) return false
        repo.clear()
        return true
    }
}