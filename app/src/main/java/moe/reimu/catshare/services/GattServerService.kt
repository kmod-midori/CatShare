package moe.reimu.catshare.services


import android.annotation.SuppressLint
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import moe.reimu.catshare.BleSecurity
import moe.reimu.catshare.R
import moe.reimu.catshare.models.DeviceInfo
import moe.reimu.catshare.models.P2pInfo
import moe.reimu.catshare.utils.BleUtils
import moe.reimu.catshare.utils.JsonWithUnknownKeys
import moe.reimu.catshare.utils.NotificationUtils
import moe.reimu.catshare.utils.ServiceState
import moe.reimu.catshare.utils.ShizukuUtils
import moe.reimu.catshare.utils.TAG
import moe.reimu.catshare.utils.checkBluetoothPermissions
import moe.reimu.catshare.utils.getReceiverFlags
import java.util.Arrays
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

class GattServerService : Service() {

    private lateinit var btManager: BluetoothManager
    private var btAdvertiser: BluetoothLeAdvertiser? = null

    private var advertisingSet: AdvertisingSet? = null

    private val localDeviceInfoLock = Object()
    private var localDeviceInfo = DeviceInfo(
        0, BleSecurity.getEncodedPublicKey(), "02:00:00:00:00:00"
    )
    private var localDeviceStatusBytes = Json.encodeToString(localDeviceInfo).toByteArray()

    private val internalReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ServiceState.ACTION_QUERY_RECEIVER_STATE -> {
                    context.sendBroadcast(ServiceState.getUpdateIntent(true))
                }

                ServiceState.ACTION_STOP_SERVICE -> {
                    Log.i(GattServerService.TAG, "Received ACTION_STOP_SERVICE")
                    stopSelf()
                }
            }
        }
    }

    private val internalIntentFilter = IntentFilter().apply {
        addAction(ServiceState.ACTION_QUERY_RECEIVER_STATE)
        addAction(ServiceState.ACTION_STOP_SERVICE)
    }


    private val advSetCallback = object : AdvertisingSetCallback() {
        override fun onAdvertisingSetStarted(
            advertisingSet: AdvertisingSet?, txPower: Int, status: Int
        ) {
            if (status == ADVERTISE_SUCCESS) {
                this@GattServerService.advertisingSet = advertisingSet
            } else {
                Log.e(TAG, "Advertising failed: $status")
            }
        }
    }

    private var gattServer: BluetoothGattServer? = null

    @SuppressLint("MissingPermission")
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        private val writeRequests =
            ConcurrentHashMap<Pair<BluetoothDevice, Int>, Pair<ByteArray, Int>>()

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid != BleUtils.CHAR_STATUS_UUID) {
                gattServer?.sendResponse(device, requestId, 257, 0, null)
                return
            }

            val data = synchronized(localDeviceInfoLock) {
                if (offset < localDeviceStatusBytes.size) {
                    Arrays.copyOfRange(localDeviceStatusBytes, offset, localDeviceStatusBytes.size)
                } else {
                    null
                }
            }

            gattServer?.sendResponse(device, requestId, 0, 0, data)
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (characteristic.uuid != BleUtils.CHAR_P2P_UUID) {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, 257, 0, null)
                }
                return
            }

            val key = Pair(device, requestId)

            val writeReq = writeRequests.getOrPut(key) {
                Pair(ByteArray(1024), 0)
            }

            System.arraycopy(value, 0, writeReq.first, offset, value.size)
            val newLength = max(writeReq.second, offset + value.size)

            val data = if (preparedWrite) {
                writeRequests[key] = writeReq.copy(second = newLength)
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, 0, 0, null)
                }
                return
            } else {
                writeRequests.remove(key)
                Arrays.copyOfRange(writeReq.first, 0, newLength)
            }

            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, 0, 0, null)
            }

            var p2pInfo: P2pInfo = JsonWithUnknownKeys.decodeFromString(data.decodeToString())
            val ecKey = p2pInfo.key
            if (ecKey != null) {
                val cipher = BleSecurity.deriveSessionKey(ecKey)
                p2pInfo = P2pInfo(
                    ssid = cipher.decrypt(p2pInfo.ssid),
                    psk = cipher.decrypt(p2pInfo.psk),
                    mac = cipher.decrypt(p2pInfo.mac),
                    port = p2pInfo.port,
                    key = null
                )
            }
            startService(P2pReceiverService.getIntent(this@GattServerService, p2pInfo))
        }
    }

    override fun onCreate() {
        super.onCreate()

        if (!checkBluetoothPermissions()) {
            stopSelf()
            return
        }

        btManager = getSystemService(BluetoothManager::class.java)
        val btAdapter = btManager.adapter
        if (btAdapter == null || !btAdapter.isEnabled) {
            NotificationUtils.showBluetoothToast(this)
            stopSelf()
            return
        }
        btAdvertiser = btAdapter.bluetoothLeAdvertiser

        try {
            startForeground(
                NotificationUtils.GATT_SERVER_FG_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= 31 && e is ForegroundServiceStartNotAllowedException) {
                Log.e(TAG, "Service startup not allowed", e)
            } else {
                Log.e(TAG, "Service startup failed", e)
            }
            stopSelf()
            return
        }

        ShizukuUtils.getMacAddress("p2p0") {
            if (it != null) {
                updateMacAddress(it)
            }
        }

        startAdv()

        registerReceiver(internalReceiver, internalIntentFilter, getReceiverFlags())
        sendBroadcast(ServiceState.getUpdateIntent(true))
    }

    private fun createNotification(): Notification {
        val pi = PendingIntent.getBroadcast(
            this,
            0,
            ServiceState.getStopIntent(),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NotificationUtils.RECEIVER_FG_CHAN_ID)
            .setSmallIcon(R.drawable.ic_bluetooth_searching)
            .setContentTitle(getString(R.string.noti_receiver_title))
            .setContentText(getString(R.string.discoverable_desc))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_close, getString(R.string.stop), pi)
            .build()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    fun startAdv() {
        val advertiser = btAdvertiser ?: return

        val advData = AdvertiseData.Builder().apply {
            addServiceUuid(ParcelUuid(BleUtils.ADV_SERVICE_UUID))
            addServiceData(
                ParcelUuid.fromString(
                    String.format(
                        "0000011e-0000-1000-8000-00805f9b34fb",
                        java.lang.Byte.valueOf(0),
                        java.lang.Byte.valueOf(0),
                    )
                ), Arrays.copyOfRange(BleUtils.RANDOM_DATA, 0, 6)
            )
        }.build()
        val scanRespData = AdvertiseData.Builder().apply {
            val data = ByteArray(27)
            System.arraycopy(ByteArray(8), 0, data, 0, 8)
            System.arraycopy(BleUtils.RANDOM_DATA, 0, data, 8, 2)

            val name = "Phone"
            val nameRaw = name.toByteArray(Charsets.UTF_8)
            System.arraycopy(nameRaw, 0, data, 10, min(nameRaw.size, 16))

            data[26] = 1

            addServiceData(ParcelUuid.fromString("00000204-0000-1000-8000-00805f9b34fb"), data)
        }.build()

        val params = AdvertisingSetParameters.Builder().apply {
            setLegacyMode(true)
            setConnectable(true)
            setScannable(true)
            setInterval(160)
            setTxPowerLevel(1)
        }.build()

        try {
            advertiser.startAdvertisingSet(
                params, advData, scanRespData, null, null, 0, 0, advSetCallback
            )

            gattServer = btManager.openGattServer(this, gattServerCallback).apply {
                addService(buildGattService())
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Got SecurityException when trying to advertise", e)
            stopSelf()
        }
    }

    private fun buildGattService(): BluetoothGattService {
        val svc = BluetoothGattService(
            BleUtils.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        svc.addCharacteristic(
            BluetoothGattCharacteristic(BleUtils.CHAR_STATUS_UUID, 10, 17)
        )
        svc.addCharacteristic(
            BluetoothGattCharacteristic(BleUtils.CHAR_P2P_UUID, 10, 17)
        )
        return svc
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(internalReceiver)
        sendBroadcast(ServiceState.getUpdateIntent(false))

        advertisingSet?.run {
            btAdvertiser?.stopAdvertisingSet(advSetCallback)
        }
        advertisingSet = null

        gattServer?.close()
        gattServer = null
    }

    private fun updateMacAddress(mac: String) {
        Log.i(TAG, "Updating local MAC address to $mac")
        synchronized(localDeviceInfoLock) {
            localDeviceInfo = DeviceInfo(
                state = localDeviceInfo.state,
                mac = mac,
                key = localDeviceInfo.key
            )
            localDeviceStatusBytes = Json.encodeToString(localDeviceInfo).toByteArray()
        }
    }

    companion object {
        fun getIntent(context: Context): Intent {
            return Intent(context, GattServerService::class.java)
        }

        fun start(context: Context) {
            context.startService(getIntent(context))
        }

        fun stop(context: Context) {
            context.stopService(getIntent(context))
        }
    }
}