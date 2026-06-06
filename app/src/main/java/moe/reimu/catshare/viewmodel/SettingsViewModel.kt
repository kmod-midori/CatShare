package moe.reimu.catshare.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.reimu.catshare.utils.AppSettings

// ============================================================
// SettingsViewModel — 设置界面状态管理
// ============================================================

data class SettingsUiState(
    val deviceName: String = "",
    val verboseLogging: Boolean = false,
    val autoAccept: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = AppSettings(application)
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        _uiState.value = SettingsUiState(
            deviceName = settings.deviceName,
            verboseLogging = settings.verboseLogging,
            autoAccept = settings.autoAccept,
        )
    }

    fun updateDeviceName(name: String) {
        _uiState.value = _uiState.value.copy(deviceName = name)
    }

    fun toggleVerboseLogging() {
        _uiState.value = _uiState.value.copy(verboseLogging = !_uiState.value.verboseLogging)
    }

    fun toggleAutoAccept() {
        _uiState.value = _uiState.value.copy(autoAccept = !_uiState.value.autoAccept)
    }

    fun saveSettings() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            val state = _uiState.value
            settings.deviceName = state.deviceName
            settings.verboseLogging = state.verboseLogging
            settings.autoAccept = state.autoAccept
            _uiState.value = _uiState.value.copy(isSaving = false, saveSuccess = true)
        }
    }
}