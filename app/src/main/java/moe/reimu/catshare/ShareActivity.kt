package moe.reimu.catshare

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelUuid
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import moe.reimu.catshare.models.DiscoveredDevice
import moe.reimu.catshare.models.FileInfo
import moe.reimu.catshare.models.TaskInfo
import moe.reimu.catshare.services.P2pSenderService
import moe.reimu.catshare.ui.DefaultCard
import moe.reimu.catshare.ui.theme.CatShareTheme
import moe.reimu.catshare.utils.BleUtils
import moe.reimu.catshare.utils.DeviceUtils
import moe.reimu.catshare.utils.NotificationUtils
import moe.reimu.catshare.utils.ShizukuUtils
import moe.reimu.catshare.utils.TAG
import java.nio.ByteBuffer
import kotlin.random.Random

class ShareActivity : ComponentActivity() {
    private lateinit var bluetoothManager: BluetoothManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bluetoothManager = getSystemService(BluetoothManager::class.java)
        val adapter = bluetoothManager.adapter
        if (adapter == null || !adapter.isEnabled) {
            NotificationUtils.showBluetoothToast(this)
            finish()
            return
        }

        val wifiManager = getSystemService(WifiManager::class.java)
        if (!wifiManager.isWifiEnabled) {
            NotificationUtils.showWifiToast(this)
            finish()
            return
        }

        val fileInfos = try {
            if (intent.action == Intent.ACTION_SEND) {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (uri != null) {
                    listOf(uri).mapNotNull { extractFileInfo(it) }
                } else {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    listOf(
                        FileInfo(
                            Uri.EMPTY, "", "", 0, text
                        )
                    )
                }
            } else {
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                uris?.mapNotNull { extractFileInfo(it) } ?: emptyList()
            }
        } catch (e: Throwable) {
            Toast.makeText(this, R.string.no_file_shared, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (fileInfos.isEmpty()) {
            Toast.makeText(this, R.string.no_file_shared, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Log.i(TAG, "Shared ${fileInfos.size} files")

        ShizukuUtils.bindService()

        enableEdgeToEdge()
        setContent {
            CatShareTheme {
                ShareActivityContent(fileInfos)
            }
        }
    }

    private fun extractFileInfo(uri: Uri): FileInfo? {
        val cr = contentResolver
        val proj = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE
        )
        return cr.query(uri, proj, null, null)?.use {
            if (it.moveToFirst()) {
                val mimeIndex = it.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                FileInfo(
                    uri,
                    it.getString(it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)),
                    if (mimeIndex < 0) {
                        "application/octet-stream"
                    } else {
                        it.getString(mimeIndex)
                    },
                    it.getInt(it.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)),
                    null
                )
            } else {
                null
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareActivityContent(files: List<FileInfo>) {
    val context = LocalContext.current
    val discoveredDevices = deviceScanner()

    var senderService by remember { mutableStateOf<P2pSenderService?>(null) }

    LifecycleStartEffect(context) {
        var isBound = false

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                val binder = service as P2pSenderService.LocalBinder
                isBound = true
                senderService = binder.getService()
            }

            override fun onServiceDisconnected(name: ComponentName) {
                isBound = false
                senderService = null
            }
        }

        context.bindService(
            Intent(context, P2pSenderService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )

        onStopOrDispose {
            if (isBound) {
                context.unbindService(connection)
                isBound = false
                senderService = null
            }
        }
    }

    val listState = rememberLazyListState()
    val iconMod = Modifier
        .size(48.dp)
        .padding(end = 16.dp)

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(text = stringResource(R.string.choose_recipient)) })
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            if (discoveredDevices.isEmpty()) {
                item {
                    Text(stringResource(R.string.scanning_desc))
                }
            } else {
                items(discoveredDevices, key = { it.id }) {
                    DefaultCard(onClick = {
                        val task = TaskInfo(
                            id = Random.nextInt(),
                            device = it,
                            files = files
                        )
                        P2pSenderService.startTaskChecked(context, task)
                    }) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AccountCircle,
                                contentDescription = null,
                                modifier = iconMod
                            )
                            Column {
                                Text(
                                    text = if (BuildConfig.DEBUG) {
                                        "${it.name} (${it.id}, ${it.device.address})"
                                    } else {
                                        it.name
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = it.brand ?: stringResource(R.string.unknown)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun deviceScanner(): List<DiscoveredDevice> {
    val context = LocalContext.current
    var discoveredDevices by remember { mutableStateOf(emptyList<DiscoveredDevice>()) }

    LifecycleResumeEffect(context) {
        val manager = context.getSystemService(BluetoothManager::class.java)
        val adapter = manager.adapter
        val devicesLock = Object()

        val callback = object : ScanCallback() {
            override fun onScanFailed(errorCode: Int) {
                println()
            }

            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val record = result.scanRecord ?: return
                var supports5Ghz = false
                var deviceName: String? = null
                var brandId: Byte? = null
                var senderId: String? = null

                for ((uuid, data) in record.serviceData.entries) {
                    when (data.size) {
                        6 -> {
                            // UUID contains brand and 5GHz flag
                            val buf = ByteBuffer.allocate(16)
                            buf.putLong(uuid.uuid.mostSignificantBits)
                            buf.putLong(uuid.uuid.leastSignificantBits)
                            val arr = buf.array()
                            supports5Ghz = arr[2].toInt() == 1
                            brandId = arr[3]
                        }

                        27 -> {
                            // Data contains device name and ID
                            val nameBuf = mutableListOf<Byte>()
                            for (i in 10..25) {
                                if (data[i].toInt() != 0) {
                                    nameBuf.add(data[i])
                                } else {
                                    break
                                }
                            }

                            val senderIdRaw = data[8].toInt().shl(8).or(data[9].toInt())
                            senderId = String.format("%04x", senderIdRaw)

                            var name = nameBuf.toByteArray().decodeToString()
                            if (name.last() == '\t') {
                                name = name.removeSuffix("\t") + "..."
                            }
                            deviceName = name
                        }
                    }
                }

                if (deviceName == null || senderId == null) {
                    return
                }

                val brand = brandId?.let {
                    DeviceUtils.deviceNameById(it)
                }

                val newDevice = DiscoveredDevice(
                    result.device, senderId, deviceName, brand, supports5Ghz
                )
                var replaced = false
                synchronized(devicesLock) {
                    val newList = discoveredDevices.map {
                        if (it.id == senderId) {
                            replaced = true
                            newDevice
                        } else {
                            it
                        }
                    }.toMutableList()
                    if (!replaced) {
                        newList.add(newDevice)
                    }
                    discoveredDevices = newList
                }
            }
        }

        var startedScanner: BluetoothLeScanner? = null

        if (adapter != null) {
            val scanner = adapter.bluetoothLeScanner
            val filters = listOf(
                ScanFilter.Builder().setServiceUuid(ParcelUuid(BleUtils.ADV_SERVICE_UUID)).build()
            )
            val settings =
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

            try {
                scanner.startScan(filters, settings, callback)
                startedScanner = scanner
                Log.d(TAG, "Started scanning")
            } catch (e: SecurityException) {
                Log.e(TAG, "Failed to start scan", e)
            }
        }

        onPauseOrDispose {
            try {
                startedScanner?.stopScan(callback)
                Log.d(TAG, "Stopped scanning")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop scan", e)
            }
        }
    }

    return discoveredDevices
}