package moe.reimu.catshare.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.reimu.catshare.AppSettings

data class SettingsUiState(
    val deviceName: String = "",
    val verbose: Boolean = false,
    val autoAccept: Boolean = false,
)

class SettingsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsUiState(),
    )

    fun load(context: Context) {
        val settings = AppSettings(context)
        _uiState.update {
            SettingsUiState(
                deviceName = settings.deviceName,
                verbose = settings.verbose,
                autoAccept = settings.autoAccept,
            )
        }
    }

    fun updateDeviceName(name: String) {
        _uiState.update { it.copy(deviceName = name) }
    }

    fun toggleVerbose() {
        _uiState.update { it.copy(verbose = !it.verbose) }
    }

    fun toggleAutoAccept() {
        _uiState.update { it.copy(autoAccept = !it.autoAccept) }
    }

    fun save(context: Context) {
        val state = _uiState.value
        val settings = AppSettings(context)
        if (state.deviceName.isNotBlank()) {
            settings.deviceName = state.deviceName
        }
        settings.verbose = state.verbose
        settings.autoAccept = state.autoAccept
    }
}
