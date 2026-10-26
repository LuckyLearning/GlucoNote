package com.gluconote.ui.profile

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gluconote.GlucoNoteApp
import com.gluconote.data.model.Settings
import com.gluconote.data.repository.RecordRepository
import com.gluconote.data.repository.SettingsRepository
import com.gluconote.util.exportCsv
import com.gluconote.util.parseCsv
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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

    init {
        viewModelScope.launch {
            settingsRepo.settingsFlow.collect { s ->
                _ui.update {
                    it.copy(
                        settings = s,
                        fastingMinText = s.fastingMin.toString(),
                        fastingMaxText = s.fastingMax.toString(),
                        postMinText = s.postMin.toString(),
                        postMaxText = s.postMax.toString()
                    )
                }
            }
        }
    }

    fun setFastingMin(t: String) = _ui.update { it.copy(fastingMinText = t) }
    fun setFastingMax(t: String) = _ui.update { it.copy(fastingMaxText = t) }
    fun setPostMin(t: String) = _ui.update { it.copy(postMinText = t) }
    fun setPostMax(t: String) = _ui.update { it.copy(postMaxText = t) }

    fun save(onSaved: () -> Unit) {
        val s = _ui.value
        val ns = Settings(
            fastingMin = s.fastingMinText.toFloatOrNull() ?: s.settings.fastingMin,
            fastingMax = s.fastingMaxText.toFloatOrNull() ?: s.settings.fastingMax,
            postMin = s.postMinText.toFloatOrNull() ?: s.settings.postMin,
            postMax = s.postMaxText.toFloatOrNull() ?: s.settings.postMax
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
}
