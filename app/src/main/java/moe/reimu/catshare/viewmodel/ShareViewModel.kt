package moe.reimu.catshare.viewmodel

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.reimu.catshare.models.DiscoveredDevice
import moe.reimu.catshare.models.FileInfo
import moe.reimu.catshare.utils.BleUtils

// ============================================================
// ShareViewModel — 发送界面状态管理
// 管理：BLE 扫描、设备列表、文件信息
// ============================================================

data class ShareUiState(
    val files: List<FileInfo> = emptyList(),
    val discoveredDevices: List<DiscoveredDevice> = emptyList(),
    val isScanning: Boolean = false,
    val isBluetoothEnabled: Boolean = false,
    val sendingDevice: DiscoveredDevice? = null,
    val isSending: Boolean = false,
)

class ShareViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ShareUiState())
    val uiState: StateFlow<ShareUiState> = _uiState.asStateFlow()

    private val bluetoothAdapter: BluetoothAdapter? =
        (application.getSystemService(Application.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter

    private var scanner: BluetoothLeScanner? = null
    private var isScanning = false

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val record = result.scanRecord ?: return

            val deviceName = record.deviceName ?: device.name ?: "Unknown"
            val brandId = BleUtils.parseBrandId(record)
            val supports5Ghz = BleUtils.parse5GhzSupport(record)
            val senderId = BleUtils.parseSenderId(record)

            val discovered = DiscoveredDevice(
                name = deviceName,
                address = device.address,
                brandId = brandId,
                supports5Ghz = supports5Ghz,
                senderId = senderId,
            )

            val currentDevices = _uiState.value.discoveredDevices.toMutableList()
            val existingIndex = currentDevices.indexOfFirst { it.address == device.address }
            if (existingIndex >= 0) {
                currentDevices[existingIndex] = discovered
            } else {
                currentDevices.add(discovered)
            }
            _uiState.value = _uiState.value.copy(discoveredDevices = currentDevices)
        }

        override fun onScanFailed(errorCode: Int) {
            _uiState.value = _uiState.value.copy(isScanning = false)
            isScanning = false
        }
    }

    fun setFiles(files: List<FileInfo>) {
        _uiState.value = _uiState.value.copy(files = files)
    }

    fun startScanning() {
        if (isScanning) return
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) {
            _uiState.value = _uiState.value.copy(isBluetoothEnabled = false)
            return
        }

        _uiState.value = _uiState.value.copy(
            isBluetoothEnabled = true,
            isScanning = true,
            discoveredDevices = emptyList()
        )

        scanner = adapter.bluetoothLeScanner
        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(BleUtils.ADV_SERVICE_UUID))
                .build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner?.startScan(filters, settings, scanCallback)
            isScanning = true
        } catch (e: SecurityException) {
            _uiState.value = _uiState.value.copy(isScanning = false)
        }
    }

    fun stopScanning() {
        try {
            scanner?.stopScan(scanCallback)
        } catch (_: Exception) {}
        isScanning = false
        _uiState.value = _uiState.value.copy(isScanning = false)
    }

    fun startSending(device: DiscoveredDevice) {
        _uiState.value = _uiState.value.copy(sendingDevice = device, isSending = true)
        // 实际发送逻辑由 P2pSenderService 处理
    }

    fun finishSending() {
        _uiState.value = _uiState.value.copy(sendingDevice = null, isSending = false)
    }

    override fun onCleared() {
        super.onCleared()
        stopScanning()
    }
}