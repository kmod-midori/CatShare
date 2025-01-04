package moe.reimu.catshare.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Binder
import android.text.format.Formatter
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.network.tls.certificates.buildKeyStore
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import moe.reimu.catshare.AppSettings
import moe.reimu.catshare.BleSecurity
import moe.reimu.catshare.BuildConfig
import moe.reimu.catshare.exceptions.CancelledByUserException
import moe.reimu.catshare.MyApplication
import moe.reimu.catshare.R
import moe.reimu.catshare.models.DeviceInfo
import moe.reimu.catshare.models.P2pInfo
import moe.reimu.catshare.models.TaskInfo
import moe.reimu.catshare.models.WebSocketMessage
import moe.reimu.catshare.utils.BleUtils
import moe.reimu.catshare.utils.DeviceUtils
import moe.reimu.catshare.utils.JsonWithUnknownKeys
import moe.reimu.catshare.utils.NotificationUtils
import moe.reimu.catshare.utils.ShizukuUtils
import moe.reimu.catshare.utils.TAG
import moe.reimu.catshare.utils.awaitWithTimeout
import moe.reimu.catshare.utils.createGroupSuspend
import moe.reimu.catshare.utils.registerInternalBroadcastReceiver
import moe.reimu.catshare.utils.removeGroup
import moe.reimu.catshare.utils.requestGroupInfo
import moe.reimu.catshare.utils.withTimeoutReason
import no.nordicsemi.android.kotlin.ble.client.main.callback.ClientBleGatt
import no.nordicsemi.android.kotlin.ble.core.RealServerDevice
import no.nordicsemi.android.kotlin.ble.core.data.util.DataByteArray
import org.json.JSONObject
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random

class P2pSenderService : BaseP2pService() {
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): P2pSenderService = this@P2pSenderService
    }

    override fun onBind(intent: Intent) = binder

    private var groupInfoFuture = CompletableDeferred<WifiP2pGroup>()

    private val currentTaskLock = Object()
    private var currentJob: Job? = null
    private var curreentTaskId: Int? = null

    private lateinit var notificationManager: NotificationManagerCompat

    private val internalReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_CANCEL_SENDING -> {
                    cancel(intent.getIntExtra("taskId", -1))
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        notificationManager = NotificationManagerCompat.from(this)

        registerInternalBroadcastReceiver(internalReceiver, IntentFilter().apply {
            addAction(ACTION_CANCEL_SENDING)
        })
    }

    @Suppress("DEPRECATION")
    override fun onP2pBroadcast(intent: Intent) {
        when (intent.action) {
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                val connInfo = intent.getParcelableExtra<WifiP2pInfo>(
                    WifiP2pManager.EXTRA_WIFI_P2P_INFO
                )!!
                val group = intent.getParcelableExtra<WifiP2pGroup>(
                    WifiP2pManager.EXTRA_WIFI_P2P_GROUP
                )

                if (group != null) {
                    groupInfoFuture?.complete(group)
                }

                Log.d(P2pSenderService.TAG, "P2P info: $connInfo, P2P group: $group")
            }

            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                val peers =
                    intent.getParcelableExtra<WifiP2pDeviceList>(WifiP2pManager.EXTRA_P2P_DEVICE_LIST)!!
                Log.d(P2pSenderService.TAG, "P2P peers: $peers")
            }

            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                val device =
                    intent.getParcelableExtra<WifiP2pDevice>(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)!!
                Log.d(P2pSenderService.TAG, "Local P2P device: $device")
            }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun runTask(task: TaskInfo) = coroutineScope {
        val taskIdStr = task.id.toString()
        var totalSize = 0L
        var fileCount = 0
        var mimeType: String? = null

        for (fi in task.files) {
            totalSize += fi.size
            fileCount += 1

            if (mimeType == null) {
                mimeType = fi.mimeType
            } else if (mimeType != fi.mimeType) {
                mimeType = "*/*"
            }
        }

        val settings = AppSettings(this@P2pSenderService)

        val taskObj =
            JSONObject()
                .put("taskId", taskIdStr)
                .put("id", taskIdStr)
                .put("senderId", BleUtils.getSenderId())
                .put("senderName", settings.deviceName)
                .put("fileName", task.files.first().name)
                .put("mimeType", mimeType)
                .put("fileCount", fileCount)
                .put("totalSize", totalSize)

        val websocketConnectFuture = CompletableDeferred<Unit>()
        val handshakeCompleteFuture = CompletableDeferred<Unit>()
        val transferStartFuture = CompletableDeferred<Unit>()
        val statusFuture = CompletableDeferred<Pair<Int, String>>()
        val transferCompleteFuture = CompletableDeferred<Unit>()

        val httpServer = embeddedServer(Netty, configure = {
            val keyStore = buildKeyStore {
                certificate("sampleAlias") {
                    password = "foobar"
                    domains = listOf("127.0.0.1", "0.0.0.0", "localhost")
                }
            }

            sslConnector(keyStore = keyStore,
                keyAlias = "sampleAlias",
                keyStorePassword = { "123456".toCharArray() },
                privateKeyPassword = { "foobar".toCharArray() }) {
                port = 0
            }

            enableHttp2 = false
        }) {
            install(WebSockets)

            routing {
                webSocket("/websocket") {
                    Log.i(TAG, "Got WS request from ${call.request.local.remoteAddress}")
                    websocketConnectFuture.complete(Unit)

                    val versionNegotiationFuture = CompletableDeferred<Unit>()

                    launch {
                        try {
                            while (true) {
                                val rawMessage = incoming.receive() as? Frame.Text
                                    ?: throw IllegalArgumentException("Invalid frame type")
                                val message = WebSocketMessage.fromText(rawMessage.readText())
                                    ?: throw IllegalArgumentException("Failed to parse message")
                                Log.d(P2pSenderService.TAG, "Incoming message: $message")

                                when (message.type) {
                                    "action" -> {
                                        if (message.name.contentEquals("status")) {
                                            val payload = message.payload ?: continue
                                            statusFuture.complete(
                                                Pair(
                                                    payload.optInt("type"),
                                                    payload.optString("reason")
                                                )
                                            )
                                        }

                                        val ackMsg = WebSocketMessage(
                                            "ack", message.id, message.name, null
                                        )
                                        send(Frame.Text(ackMsg.toText()))
                                    }

                                    "ack" -> {
                                        val isVn = message.name.contentEquals(
                                            ACTION_VERSION_NEGOTIATION, true
                                        )
                                        if (isVn) {
                                            versionNegotiationFuture.complete(Unit)
                                        }
                                    }
                                }
                            }
                        } catch (e: Throwable) {
                            Log.e(TAG, "WebSocket failed", e)
                            throw e
                        } finally {
                            outgoing.close()
                        }
                    }

                    send(
                        Frame.Text(
                            WebSocketMessage(
                                "action",
                                0,
                                "versionNegotiation",
                                JSONObject()
                                    .put("version", 1)
                                    .put("versions", listOf(1))
                            ).toText()
                        )
                    )
                    versionNegotiationFuture.await()
                    send(
                        Frame.Text(
                            WebSocketMessage(
                                "action", 1, "sendRequest", taskObj
                            ).toText()
                        )
                    )
                    handshakeCompleteFuture.complete(Unit)
                }

                get("/download") {
                    Log.i(TAG, "Got download request from ${call.request.local.remoteAddress}")
                    transferStartFuture.complete(Unit)

                    if (call.request.queryParameters["taskId"] != taskIdStr) {
                        call.respondText(
                            "Task ID not found",
                            ContentType.Text.Plain,
                            HttpStatusCode.NotFound
                        )
                        return@get
                    }

                    var processedSize = 0L
                    var lastProgressUpdate = 0L

                    call.respondOutputStream(ContentType.Application.Zip, HttpStatusCode.OK) {
                        val cr = contentResolver
                        ZipOutputStream(this).use { zo ->
                            for ((i, rf) in task.files.withIndex()) {
                                cr.openInputStream(rf.uri)!!.use { ist ->
                                    zo.putNextEntry(ZipEntry("$i/${rf.name}"))

                                    val buffer = ByteArray(1024 * 1024 * 4)
                                    while (true) {
                                        val readLen = ist.read(buffer)
                                        if (readLen == -1) {
                                            break
                                        }
                                        zo.write(buffer, 0, readLen)
                                        processedSize += readLen.toLong()

                                        // Update progress if needed
                                        val now = System.nanoTime()
                                        val elapsed = TimeUnit.SECONDS.convert(
                                            now - lastProgressUpdate, TimeUnit.NANOSECONDS
                                        )
                                        if (elapsed > 1) {
                                            updateNotification(
                                                createProgressNotification(
                                                    task.id,
                                                    task.device.name,
                                                    totalSize,
                                                    processedSize
                                                )
                                            )
                                            lastProgressUpdate = now
                                        }
                                    }

                                    zo.closeEntry()
                                }
                            }
                        }
                        transferCompleteFuture.complete(Unit)
                    }
                }
            }
        }

        try {
            httpServer.start()
            val serverPort = httpServer.engine.resolvedConnectors().first().port
            Log.d(TAG, "HTTP server listening on $serverPort")

            val groupInfo = p2pManager.requestGroupInfo(p2pChannel)
            if (groupInfo != null) {
                Log.i(TAG, "Removing existing group: $groupInfo")
                p2pManager.removeGroup(p2pChannel)
            }

            val ssid = "DIRECT-${DeviceUtils.getRandomChars(8)}"
            val psk = DeviceUtils.getRandomChars(8)

            val p2pConfig = WifiP2pConfig.Builder().setGroupOperatingBand(
                if (task.device.supports5Ghz) {
                    WifiP2pConfig.GROUP_OWNER_BAND_AUTO
                } else {
                    WifiP2pConfig.GROUP_OWNER_BAND_2GHZ
                }
            ).setNetworkName(ssid).setPassphrase(psk).enablePersistentMode(false).build()

            try {
                groupInfoFuture = CompletableDeferred()
                p2pManager.createGroupSuspend(p2pChannel, p2pConfig)
                groupInfoFuture.awaitWithTimeout(
                    Duration.ofSeconds(5),
                    "Waiting for P2P group info"
                )

                val p2pMac = ShizukuUtils.getMacAddress("p2p0") ?: "02:00:00:00:00:00"
                Log.d(TAG, "Advertised local MAC address: $p2pMac")

                withTimeoutReason(Duration.ofSeconds(10), "BLE operations") {
                    var gBleClient: ClientBleGatt? = null
                    try {
                        val bleClient = ClientBleGatt.connect(
                            this@P2pSenderService,
                            RealServerDevice(task.device.device),
                            this@withTimeoutReason,
                        )
                        gBleClient = bleClient

                        bleClient.requestMtu(512)
                        val services = bleClient.discoverServices()
                        val p2pService = services.findService(BleUtils.SERVICE_UUID)
                            ?: throw IllegalStateException("BLE service not found")
                        val deviceInfoChar =
                            p2pService.findCharacteristic(BleUtils.CHAR_STATUS_UUID)
                                ?: throw IllegalStateException("BLE device info char not found")
                        val p2pInfoChar = p2pService.findCharacteristic(BleUtils.CHAR_P2P_UUID)
                            ?: throw IllegalStateException("BLE P2P info char not found")
                        val rdInfo: DeviceInfo =
                            JsonWithUnknownKeys.decodeFromString(deviceInfoChar.read().value.decodeToString())
                        Log.i(TAG, "Remote device: $rdInfo")

                        val cipher = rdInfo.key?.let {
                            BleSecurity.deriveSessionKey(it)
                        }

                        val newP2pInfo = P2pInfo(
                            id = BleUtils.getSenderId(),
                            ssid = cipher?.encrypt(ssid) ?: ssid,
                            psk = cipher?.encrypt(psk) ?: psk,
                            mac = cipher?.encrypt(p2pMac) ?: p2pMac,
                            key = if (cipher != null) {
                                BleSecurity.getEncodedPublicKey()
                            } else {
                                null
                            },
                            port = serverPort
                        )

                        p2pInfoChar.write(
                            DataByteArray(
                                Json.encodeToString(newP2pInfo).toByteArray()
                            )
                        )
                    } finally {
                        gBleClient?.close()
                    }
                }

                val transferJob = async {
                    websocketConnectFuture.awaitWithTimeout(
                        Duration.ofSeconds(10),
                        "Waiting for WS connect"
                    )
                    handshakeCompleteFuture.awaitWithTimeout(
                        Duration.ofSeconds(5),
                        "Waiting for handshake"
                    )
                    transferStartFuture.awaitWithTimeout(
                        Duration.ofSeconds(30),
                        "Waiting for start transfer"
                    )
                    transferCompleteFuture.await()
                    withTimeoutOrNull(5000L) {
                        statusFuture.await()
                    }
                }
                val status = select<Pair<Int, String>?> {
                    transferJob.onAwait { it }
                    statusFuture.onAwait { it }
                }

                if (status != null) {
                    if (status.first == 3 && status.second == "user refuse") {
                        throw CancelledByUserException()
                    }
                    if (status.first == 1) {
                        return@coroutineScope
                    }
                    throw RuntimeException("Transfer terminated with $status")
                } else {
                    throw TimeoutException("Status timed out")
                }
            } finally {
                p2pManager.removeGroup(p2pChannel)
            }
        } finally {
            httpServer.stop(1000, 1000)
        }
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent == null) {
            return START_NOT_STICKY
        }

        if (!MyApplication.getInstance().setBusy()) {
            Log.i(TAG, "Application is busy, skipping")
            NotificationUtils.showBusyToast(this)
            return START_NOT_STICKY
        }

        val task = intent.getParcelableExtra<TaskInfo>("task") ?: return START_NOT_STICKY
        val job = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                startForeground(
                    NotificationUtils.SENDER_FG_ID,
                    createPendingNotification(task.id, task.device.name),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
                runTask(task)
                notificationManager.notify(
                    Random.nextInt(),
                    createCompletedNotification(task.device.name)
                )
            } catch (e: CancelledByUserException) {
                Log.i(TAG, "Cancelled by user")
                notificationManager.notify(
                    Random.nextInt(),
                    createFailedNotification(task.device.name)
                )
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to process task", e)
                notificationManager.notify(
                    Random.nextInt(),
                    createFailedNotification(task.device.name)
                )
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                MyApplication.getInstance().clearBusy()
                synchronized(currentTaskLock) {
                    curreentTaskId = null
                    currentJob = null
                }
            }
        }

        synchronized(currentTaskLock) {
            curreentTaskId = task.id
            currentJob = job
        }

        return START_NOT_STICKY
    }

    fun cancel(taskId: Int) {
        synchronized(currentTaskLock) {
            if (curreentTaskId == taskId) {
                currentJob?.cancel(CancelledByUserException())
            }
        }
    }

    private fun createNotificationBuilder(@DrawableRes icon: Int): NotificationCompat.Builder {
        return NotificationCompat.Builder(this, NotificationUtils.SENDER_CHAN_ID)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setSmallIcon(icon)
            .setPriority(NotificationCompat.PRIORITY_MAX)
    }

    private fun createCancelSendingAction(taskId: Int) = NotificationCompat.Action.Builder(
        R.drawable.ic_close,
        getString(android.R.string.cancel),
        PendingIntent.getBroadcast(
            this,
            taskId,
            Intent(ACTION_CANCEL_SENDING).putExtra("taskId", taskId),
            PendingIntent.FLAG_IMMUTABLE
        )
    ).build()

    private fun createPendingNotification(taskId: Int, targetName: String) =
        createNotificationBuilder(R.drawable.ic_upload_file)
            .setSubText(targetName)
            .setContentTitle(getString(R.string.preparing_transmission))
            .setContentText(getString(R.string.noti_connecting))
            .addAction(createCancelSendingAction(taskId))
            .setOngoing(true).build()

    private fun createProgressNotification(
        taskId: Int,
        targetName: String,
        totalSize: Long,
        processedSize: Long
    ): Notification {
        val n = createNotificationBuilder(R.drawable.ic_upload_file)
            .setContentTitle(getString(R.string.sending))
            .setSubText(targetName)
            .addAction(createCancelSendingAction(taskId))
            .setOnlyAlertOnce(true)
            .setOngoing(true)

        val progress = 100.0 * (processedSize.toDouble() / totalSize.toDouble())
        n.setProgress(100, progress.toInt(), false)

        val f1 = Formatter.formatShortFileSize(this, processedSize)
        val f2 = Formatter.formatShortFileSize(this, totalSize)
        n.setContentText("$f1 / $f2 | ${progress.toInt()}%")

        return n.build()
    }

    private fun createFailedNotification(targetName: String): Notification {
        return createNotificationBuilder(R.drawable.ic_warning)
            .setContentTitle(getString(R.string.send_fail))
            .setSubText(targetName)
            .setContentText(getString(R.string.noti_send_interrupted))
            .setAutoCancel(true)
            .build()
    }

    private fun createCompletedNotification(targetName: String) =
        createNotificationBuilder(R.drawable.ic_done)
            .setContentTitle(getString(R.string.send_ok))
            .setSubText(targetName)
            .setAutoCancel(true)
            .build()

    @SuppressLint("MissingPermission")
    private fun updateNotification(n: Notification) {
        notificationManager.notify(NotificationUtils.SENDER_FG_ID, n)
    }

    companion object {
        private const val ACTION_VERSION_NEGOTIATION = "versionNegotiation"

        private const val ACTION_CANCEL_SENDING = "${BuildConfig.APPLICATION_ID}.CANCEL_SENDING"

        fun getIntent(context: Context, task: TaskInfo): Intent {
            return Intent(context, P2pSenderService::class.java).apply {
                putExtra("task", task)
            }
        }

        fun startTaskChecked(context: Context, task: TaskInfo): Boolean {
            if (MyApplication.getInstance().getBusy()) {
                NotificationUtils.showBusyToast(context)
                return false
            }
            context.startService(getIntent(context, task))
            return true
        }
    }
}