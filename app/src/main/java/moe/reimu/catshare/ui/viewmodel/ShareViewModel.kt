package moe.reimu.catshare.ui.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import moe.reimu.catshare.MyApplication
import moe.reimu.catshare.models.DiscoveredDevice
import moe.reimu.catshare.models.FileInfo
import moe.reimu.catshare.services.P2pSenderService
import moe.reimu.catshare.utils.BleUtils
import moe.reimu.catshare.utils.DeviceUtils
import java.nio.ByteBuffer

data class ShareUiState(
    val devices: List<DiscoveredDevice> = emptyList(),
    val isScanning: Boolean = false,
    val errorMessage: String? = null,
)

class ShareViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(ShareUiState())
    val uiState: StateFlow<ShareUiState> = _uiState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ShareUiState(),
    )

    private var scanner: BluetoothLeScanner? = null

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val record = result?.scanRecord ?: return
            var supports5Ghz = false
            var deviceName: String?
            var brandId: Byte? = null

            for ((uuid, data) in record.serviceData.entries) {
                when (data.size) {
                    6 -> {
                        // 品牌 + 5GHz 标志位
                        val buf = ByteBuffer.allocate(16)
                        buf.putLong(uuid.uuid.mostSignificantBits)
                        buf.putLong(uuid.uuid.leastSignificantBits)
                        val arr = buf.array()
                        supports5Ghz = arr[2].toInt() == 1
                        brandId = arr[3]
                    }

                    27 -> {
                        // 设备名称 + senderId
                        val nameBuf = mutableListOf<Byte>()
                        for (i in 10..25) {
                            if (data[i].toInt() != 0) {
                                nameBuf.add(data[i])
                            } else {
                                break
                            }
                        }
                        val senderIdRaw = data[8].toInt().shl(8).or(data[9].toInt())
                        val senderId = String.format("%04x", senderIdRaw)
                        var name = nameBuf.toByteArray().decodeToString()
                        if (name.lastOrNull() == '\t') {
                            name = name.removeSuffix("\t") + "..."
                        }
                        deviceName = name
                        val brand = brandId?.let { DeviceUtils.deviceNameById(it) }
                        val newDevice = DiscoveredDevice(
                            device = result.device,
                            id = senderId,
                            name = deviceName,
                            brand = brand,
                            supports5Ghz = supports5Ghz,
                        )
                        addOrReplace(newDevice)
                    }
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _uiState.update {
                it.copy(
                    errorMessage = "Scan failed (code=$errorCode)",
                    isScanning = false,
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (_uiState.value.isScanning) return
        val context = getApplication<MyApplication>()
        val btManager = context.getSystemService(BluetoothManager::class.java)
        val adapter = btManager.adapter ?: return

        try {
            val filters = listOf(
                ScanFilter.Builder().setServiceUuid(ParcelUuid(BleUtils.ADV_SERVICE_UUID)).build()
            )
            val settings =
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            adapter.bluetoothLeScanner.startScan(filters, settings, scanCallback)
            scanner = adapter.bluetoothLeScanner
            _uiState.update { it.copy(isScanning = true, errorMessage = null) }
        } catch (e: SecurityException) {
            _uiState.update {
                it.copy(errorMessage = "Bluetooth permission denied: ${e.message}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        try {
            scanner?.stopScan(scanCallback)
        } catch (_: Exception) {
        }
        scanner = null
        _uiState.update { it.copy(isScanning = false) }
    }

    private fun addOrReplace(device: DiscoveredDevice) {
        _uiState.update { state ->
            val replaced = state.devices.map {
                if (it.id == device.id) device else it
            }
            val newList =
                if (replaced.size == state.devices.size && replaced.none { it.id == device.id }) {
                    state.devices + device
                } else {
                    replaced
                }
            state.copy(devices = newList)
        }
    }

    fun startTask(device: DiscoveredDevice, files: List<FileInfo>) {
        val task = moe.reimu.catshare.models.TaskInfo(
            id = kotlin.random.Random.nextInt(),
            device = device,
            files = files,
        )
        P2pSenderService.startTaskChecked(getApplication(), task)
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
    }
}
