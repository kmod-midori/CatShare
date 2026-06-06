package moe.reimu.catshare.viewmodel

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.reimu.catshare.exceptions.ShizukuException
import moe.reimu.catshare.services.GattServerService
import moe.reimu.catshare.utils.ShizukuUtils
import rikka.shizuku.Shizuku

// ============================================================
// MainViewModel — 主界面状态管理
// 管理：蓝牙/WiFi 状态、接收器开关、Shizuku 权限
// ============================================================

data class MainUiState(
    val isBluetoothEnabled: Boolean = false,
    val isWifiEnabled: Boolean = false,
    val isReceiverRunning: Boolean = false,
    val shizukuGranted: Boolean = false,
    val shizukuAvailable: Boolean = false,
    val isShizukuRequired: Boolean = false,
)

sealed class MainEvent {
    data object RequestBluetooth : MainEvent()
    data object RequestWifi : MainEvent()
    data object RequestShizuku : MainEvent()
    data object ToggleReceiver : MainEvent()
    data class ShowToast(val message: String) : MainEvent()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<MainEvent>()
    val events = _events.asSharedFlow()

    private val bluetoothManager = application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val wifiManager = application.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val receiverStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val running = intent?.getBooleanExtra(GattServerService.EXTRA_RUNNING, false) ?: false
            _uiState.value = _uiState.value.copy(isReceiverRunning = running)
        }
    }

    private val shizukuListener = object : Shizuku.OnRequestPermissionResultListener {
        override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                    shizukuGranted = grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED
                )
            }
        }
    }

    init {
        refreshStatus()
        registerReceivers()
    }

    private fun registerReceivers() {
        val context = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                receiverStateReceiver,
                IntentFilter(GattServerService.ACTION_STATE_CHANGED),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            context.registerReceiver(
                receiverStateReceiver,
                IntentFilter(GattServerService.ACTION_STATE_CHANGED)
            )
        }
        Shizuku.addRequestPermissionResultListener(shizukuListener)
    }

    fun refreshStatus() {
        _uiState.value = _uiState.value.copy(
            isBluetoothEnabled = bluetoothAdapter?.isEnabled == true,
            isWifiEnabled = wifiManager.isWifiEnabled,
            shizukuAvailable = ShizukuUtils.isAvailable(),
            shizukuGranted = ShizukuUtils.isGranted(),
            isShizukuRequired = !ShizukuUtils.hasLocalMacPermission()
        )
    }

    fun toggleReceiver() {
        val context = getApplication<Application>()
        val intent = GattServerService.getIntent(context)
        if (_uiState.value.isReceiverRunning) {
            context.stopService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun requestShizukuPermission() {
        viewModelScope.launch {
            try {
                ShizukuUtils.requestPermission(getApplication())
            } catch (e: ShizukuException) {
                _events.emit(MainEvent.ShowToast("Shizuku 权限请求失败: ${e.message}"))
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().unregisterReceiver(receiverStateReceiver)
        Shizuku.removeRequestPermissionResultListener(shizukuListener)
    }
}