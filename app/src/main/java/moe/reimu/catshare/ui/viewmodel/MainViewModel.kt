package moe.reimu.catshare.ui.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import moe.reimu.catshare.services.GattServerService
import moe.reimu.catshare.utils.ServiceState
import moe.reimu.catshare.utils.registerInternalBroadcastReceiver
import rikka.shizuku.Shizuku

data class MainUiState(
    val isReceiverRunning: Boolean = false,
    val shizukuGranted: Boolean = false,
    val shizukuAvailable: Boolean = false,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ServiceState.ACTION_UPDATE_RECEIVER_STATE -> {
                    _uiState.update {
                        it.copy(isReceiverRunning = intent.getBooleanExtra("isRunning", false))
                    }
                }
            }
        }
    }

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        val available = shizukuPing()
        updateShizukuState(
            available = available,
            granted = grantResult == PackageManager.PERMISSION_GRANTED,
        )
    }

    private val shizukuBinderListener = Shizuku.OnBinderReceivedListener {
        val available = shizukuPing()
        updateShizukuState(
            available = available,
            granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED,
        )
    }

    private val shizukuDeadListener = Shizuku.OnBinderDeadListener {
        updateShizukuState(available = false, granted = false)
    }

    init {
        val app = getApplication<Application>()
        app.registerInternalBroadcastReceiver(
            receiver,
            IntentFilter(ServiceState.ACTION_UPDATE_RECEIVER_STATE),
        )
        app.sendBroadcast(ServiceState.getQueryIntent())

        val available = shizukuPing()
        updateShizukuState(
            available = available,
            granted = available && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED,
        )

        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        Shizuku.addBinderReceivedListenerSticky(shizukuBinderListener)
        Shizuku.addBinderDeadListener(shizukuDeadListener)
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(receiver)
        } catch (_: Throwable) {
        }
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        Shizuku.removeBinderReceivedListener(shizukuBinderListener)
        Shizuku.removeBinderDeadListener(shizukuDeadListener)
    }

    fun updateShizukuState(available: Boolean, granted: Boolean) {
        _uiState.update {
            it.copy(
                shizukuAvailable = available,
                shizukuGranted = granted,
            )
        }
    }

    fun toggleReceiver(targetOn: Boolean) {
        val app = getApplication<Application>()
        if (targetOn) {
            GattServerService.start(app)
        } else {
            GattServerService.stop(app)
        }
    }

    private fun shizukuPing(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
    }
}
