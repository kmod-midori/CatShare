package moe.reimu.naiveshare.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.NetworkInfo
import android.net.Uri
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.text.format.Formatter
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import moe.reimu.naiveshare.FakeTrustManager
import moe.reimu.naiveshare.MyApplication
import moe.reimu.naiveshare.R
import moe.reimu.naiveshare.models.P2pInfo
import moe.reimu.naiveshare.models.ReceivedFile
import moe.reimu.naiveshare.models.WebSocketMessage
import moe.reimu.naiveshare.utils.NotificationUtils
import moe.reimu.naiveshare.utils.ServiceState
import moe.reimu.naiveshare.utils.TAG
import moe.reimu.naiveshare.utils.checkP2pPermissions
import moe.reimu.naiveshare.utils.getReceiverFlags
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.File
import java.net.InetAddress
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import javax.net.ssl.SSLContext
import kotlin.math.min
import kotlin.random.Random

class P2pReceiverService : Service() {
    private val p2pReceiver = object : BroadcastReceiver() {
        @Suppress("DEPRECATION")
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(P2pReceiverService.TAG, "Action: ${intent.action}")

            when (intent.action) {
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val connInfo =
                        intent.getParcelableExtra<WifiP2pInfo>(WifiP2pManager.EXTRA_WIFI_P2P_INFO)!!
                    val netInfo =
                        intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)!!
                    Log.d(P2pReceiverService.TAG, "P2P info: $connInfo, Net info: $netInfo")

                    if (connInfo.groupFormed && netInfo.isConnected) {
                        targetAddress = connInfo.groupOwnerAddress
                        synchronized(p2pConnNotify) {
                            p2pConnNotify.notifyAll()
                        }
                    }
                }
            }
        }
    }

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    private lateinit var p2pManager: WifiP2pManager
    private lateinit var p2pChannel: WifiP2pManager.Channel
    private lateinit var notificationManager: NotificationManagerCompat

    private val p2pConnNotify = Object()
    private var targetAddress: InetAddress? = null
    private val okHttpClient = run {
        val sslContext = SSLContext.getInstance("TLSv1.2")
        val tm = FakeTrustManager()
        sslContext.init(null, arrayOf(tm), SecureRandom())

        OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS).connectionPool(
            ConnectionPool(5, 10, TimeUnit.SECONDS)
        ).sslSocketFactory(sslContext.socketFactory, tm).hostnameVerifier { _, _ -> true }.build()
    }

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "onCreate")

        if (!checkP2pPermissions()) {
            stopSelf()
            return
        }

        registerReceiver(p2pReceiver, intentFilter, getReceiverFlags())

        p2pManager = getSystemService(WifiP2pManager::class.java)
        p2pChannel = p2pManager.initialize(this, mainLooper, null)
        notificationManager = NotificationManagerCompat.from(this)
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && intent.hasExtra("p2p_info")) {
            val info = intent.getParcelableExtra<P2pInfo>("p2p_info")
            if (info != null) {
                beginReceive(info)
            }
        }

        return START_NOT_STICKY
    }

    private fun beginReceive(p2pInfo: P2pInfo) {
        Log.d(TAG, "beginReceive: $p2pInfo")

        if (!MyApplication.getInstance().setBusy()) {
            Log.i(TAG, "Application is busy, skipping")
            return
        }

        startForeground(
            NotificationUtils.RECEIVER_FG_ID,
            createPrepareNotification(getString(R.string.noti_connecting))
        )

        val th = Thread {
            try {
                mainThread(p2pInfo)
            } catch (e: Exception) {
                Log.e(TAG, "Receive failed", e)
            } finally {
                Thread.sleep(1000)
                cleanup()
            }
        }
        th.start()
    }

    @SuppressLint("MissingPermission")
    private fun mainThread(p2pInfo: P2pInfo) {
        val p2pConfig =
            WifiP2pConfig.Builder().setNetworkName(p2pInfo.ssid).setPassphrase(p2pInfo.psk).build()

        // 1. Connect to remote hotspot
        p2pManager.connect(p2pChannel, p2pConfig, null)
        synchronized(p2pConnNotify) {
            p2pConnNotify.wait(1000 * 10)
        }

        val targetAddress = this.targetAddress ?: throw RuntimeException("P2P connection failed")

        val hostPort = "${targetAddress.hostAddress}:${p2pInfo.port}"
        var ws: WebSocket? = null

        var requestObject: JSONObject? = null
        val requestNotify = Object()

        // 2. WebSocket connection
        okHttpClient.newWebSocket(Request.Builder().url("wss://${hostPort}/websocket").build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    ws = webSocket
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(P2pReceiverService.TAG, "WebSocket onClosed $code $reason")
                    ws = null
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.d(P2pReceiverService.TAG, "WebSocket onFailure", t)
                    ws = null
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val message = WebSocketMessage.fromText(text) ?: return
                    Log.d(P2pReceiverService.TAG, "WebSocket onMessage $message")

                    if (!message.type.equals("action", true)) {
                        return
                    }

                    val payload = message.payload ?: return

                    val ack = when (message.name.lowercase()) {
                        // 3. Version negotiation
                        "versionnegotiation" -> {
                            val inVersion = payload.optInt("version", 1)
                            val currentVersion = min(inVersion, 1)
                            WebSocketMessage(
                                "ack",
                                message.id,
                                message.name,
                                JSONObject().put("version", currentVersion).put("threadLimit", 5)
                            )
                        }

                        // 4. Receive request
                        "sendrequest" -> {
                            requestObject = payload
                            synchronized(requestNotify) {
                                requestNotify.notifyAll()
                            }
                            WebSocketMessage(
                                "ack", message.id, message.name, null
                            )
                        }

                        "status" -> {
                            null
                        }

                        else -> {
                            null
                        }
                    }

                    if (ack != null) {
                        webSocket.send(ack.toText())
                    }
                }
            })

        synchronized(requestNotify) {
            requestNotify.wait(1000 * 5)
        }

        val sendRequestPayload = requestObject ?: throw RuntimeException("File information failed")
        val taskId = sendRequestPayload.optString("taskId", sendRequestPayload.optString("id"))
        val taskCode = taskId.hashCode()

        // 5. Receive thumbnail
        val thumbPath = sendRequestPayload.optString("thumbnail")
        val bigPicture = if (thumbPath.isNotEmpty()) {
            val thumbUrl = "https://${hostPort}$thumbPath"
            Log.d(TAG, "Fetching thumbnail from $thumbUrl")

            val req = Request.Builder().url(thumbUrl).build()
            okHttpClient.newCall(req).execute().use { thumbResp ->
                if (!thumbResp.isSuccessful) {
                    return@use null
                }
                val byteArrayBody: ByteArray = thumbResp.body!!.bytes()
                BitmapFactory.decodeByteArray(byteArrayBody, 0, byteArrayBody.size)
            }
        } else null


        val senderName = sendRequestPayload.getString("senderName")
        val fileName = sendRequestPayload.getString("fileName")
        val totalSize = sendRequestPayload.getLong("totalSize")
        val fileCount = sendRequestPayload.getInt("fileCount")

        var accepted: Boolean? = null
        val acceptedNotify = Object()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.getIntExtra("task_code", 0) != taskCode) {
                    return
                }

                accepted = when (intent.action) {
                    ACTION_ACCEPTED -> true
                    ACTION_DISMISSED -> false
                    else -> return
                }

                synchronized(acceptedNotify) {
                    acceptedNotify.notifyAll()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(ACTION_DISMISSED)
            addAction(ACTION_ACCEPTED)
        }

        try {
            ContextCompat.registerReceiver(
                this, receiver, filter, getReceiverFlags()
            )

            updateNotification(
                createAskingNotification(
                    taskCode, senderName, fileName, fileCount, totalSize, bigPicture
                )
            )

            for (i in 0..600) {
                synchronized(acceptedNotify) {
                    acceptedNotify.wait(100)
                }

                if (accepted != null || ws == null) {
                    break
                }
            }
        } finally {
            unregisterReceiver(receiver)
        }

        if (ws == null) {
            throw RuntimeException("WebSocket closed by remote")
        }

        if (accepted == null || accepted == false) {
            ws?.send(
                WebSocketMessage.makeStatus(
                    0, taskId, 3, "user refuse"
                ).toText()
            )
            throw RuntimeException("User has rejected transmission")
        }

        updateNotification(
            createProgressNotification(
                taskCode, senderName, totalSize, null
            )
        )

        val downloadUrl = "https://${hostPort}/download?taskId=${taskId}"
        val dlReq = Request.Builder().url(downloadUrl).build()
        val receivedFiles = mutableListOf<ReceivedFile>()
        var lastException: Throwable? = null

        okHttpClient.newCall(dlReq).execute().use { dlRes ->
            if (!dlRes.isSuccessful) {
                throw RuntimeException("Download request failed")
            }

            val zipStream = ZipInputStream(dlRes.body!!.byteStream())
            val contentResolver = contentResolver
            var processedSize = 0L
            var lastProgressUpdate = 0L

            while (true) {
                val entry = zipStream.nextEntry ?: break
                if (entry.isDirectory) {
                    continue
                }

                Log.d(P2pReceiverService.TAG, "Entry ${entry.name}")

                val entryFile = File(entry.name)
                val values = createContentValues(entryFile)
                try {
                    val uri = contentResolver.insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                    ) ?: throw RuntimeException("Failed to write ${entryFile.name} to media store")

                    try {
                        val os = contentResolver.openOutputStream(uri)
                            ?: throw RuntimeException("Failed to open ${entryFile.name}")
                        val buffer = ByteArray(1024 * 1024 * 4)

                        os.use {
                            while (true) {
                                val readLen = zipStream.read(buffer)
                                if (readLen == -1) {
                                    break
                                }
                                os.write(buffer, 0, readLen)
                                processedSize += readLen.toLong()

                                // Update progress if needed
                                val now = System.nanoTime()
                                val elapsed = TimeUnit.SECONDS.convert(
                                    now - lastProgressUpdate, TimeUnit.NANOSECONDS
                                )
                                if (elapsed > 1) {
                                    updateNotification(
                                        createProgressNotification(
                                            taskCode, senderName, totalSize, processedSize
                                        )
                                    )
                                    lastProgressUpdate = now
                                }
                            }
                        }

                        receivedFiles.add(
                            ReceivedFile(
                                entryFile.name,
                                uri,
                                values.getAsString(MediaStore.Downloads.MIME_TYPE)
                            )
                        )
                    } catch (e: Throwable) {
                        // Remove failed files
                        contentResolver.delete(uri, null, null)
                        throw e
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to receive ${entryFile.name}, stopping", e)
                    lastException = e
                    break
                }
            }
        }

        if (receivedFiles.isNotEmpty()) {
            notificationManager.notify(
                Random.nextInt(),
                createCompletedNotification(senderName, receivedFiles, lastException != null)
            )

        } else {
            notificationManager.notify(
                Random.nextInt(), createFailedNotification(senderName)
            )
        }

        lastException?.run { throw this }

        ws?.send(
            WebSocketMessage.makeStatus(0, taskId, 1, "done").toText()
        )
    }

    private fun createContentValues(file: File): ContentValues {
        val extension = file.extension
        val mimeType = if (extension.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        } else null

        return ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, mimeType ?: "application/octet-stream")
            put(
                MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS
            )
        }
    }

    private fun createNotificationBuilder(@DrawableRes icon: Int): NotificationCompat.Builder {
        return NotificationCompat.Builder(this, NotificationUtils.RECEIVER_CHAN_ID)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setSmallIcon(icon)
            .setPriority(NotificationCompat.PRIORITY_MAX)
    }

    private fun createPrepareNotification(description: String) =
        createNotificationBuilder(R.drawable.ic_downloading)
            .setOngoing(true)
            .setContentTitle(getString(R.string.preparing_transmission))
            .setContentText(description).build()

    private fun createAskingNotification(
        taskCode: Int,
        senderName: String,
        fileName: String,
        fileCount: Int,
        totalSize: Long,
        thumbnail: Bitmap?
    ): Notification {
        val fmtSize = Formatter.formatShortFileSize(this, totalSize)
        val contentText = resources.getQuantityString(
            R.plurals.noti_request_desc, fileCount, fileCount, fmtSize
        )

        val dismissIntent = PendingIntent.getBroadcast(
            this,
            taskCode,
            Intent(ACTION_DISMISSED).apply { putExtra("task_code", taskCode) },
            PendingIntent.FLAG_IMMUTABLE
        )

        val acceptIntent = PendingIntent.getBroadcast(
            this,
            taskCode,
            Intent(ACTION_ACCEPTED).apply { putExtra("task_code", taskCode) },
            PendingIntent.FLAG_IMMUTABLE
        )

        val n = createNotificationBuilder(R.drawable.ic_downloading)
            .setContentTitle(senderName)
            .setContentText(contentText)
            .addAction(R.drawable.ic_done, getString(R.string.accept), acceptIntent)
            .addAction(R.drawable.ic_close, getString(R.string.reject), dismissIntent)
            .setDeleteIntent(dismissIntent)

        if (thumbnail != null) {
            n.setStyle(NotificationCompat.BigPictureStyle().bigPicture(thumbnail))
        }

        return n.build()
    }

    private fun createProgressNotification(
        taskCode: Int, senderName: String, totalSize: Long, processedSize: Long?
    ): Notification {
        val cancelIntent = PendingIntent.getBroadcast(
            this,
            taskCode,
            Intent(ACTION_CANCELLED).apply { putExtra("task_code", taskCode) },
            PendingIntent.FLAG_IMMUTABLE
        )

        val n = createNotificationBuilder(R.drawable.ic_downloading)
            .setContentTitle(getString(R.string.receiving))
            .setSubText(senderName)
            .addAction(R.drawable.ic_close, getString(android.R.string.cancel), cancelIntent)
            .setOngoing(true).setOnlyAlertOnce(true)
        var text = getString(R.string.preparing)

        if (processedSize != null) {
            val progress = 100.0 * (processedSize.toDouble() / totalSize.toDouble())
            n.setProgress(100, progress.toInt(), false)

            val f1 = Formatter.formatShortFileSize(this, processedSize)
            val f2 = Formatter.formatShortFileSize(this, totalSize)
            text = "$f1 / $f2 | ${progress.toInt()}%"
        } else {
            n.setProgress(0, 0, true)
        }
        n.setContentText(text)

        return n.build()
    }

    private fun createCompletedNotification(
        senderName: String, receivedFiles: List<ReceivedFile>, isPartial: Boolean
    ): Notification {
        val style = NotificationCompat.BigTextStyle()
            .bigText(receivedFiles.take(5).joinToString("\n") { it.name })
        val builder = createNotificationBuilder(R.drawable.ic_done)
            .setContentTitle(getString(R.string.recv_ok))
            .setSubText(senderName)
            .setAutoCancel(true)
            .setContentText(
                if (isPartial) {
                    resources.getQuantityString(
                        R.plurals.noti_complete_partial,
                        receivedFiles.size,
                        receivedFiles.size
                    )
                } else {
                    resources.getQuantityString(
                        R.plurals.noti_complete,
                        receivedFiles.size,
                        receivedFiles.size
                    )
                }
            ).setStyle(style)

        val intent = if (receivedFiles.size == 1) {
            val rf = receivedFiles.first()
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(rf.uri, rf.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                putExtra(
                    "android.provider.extra.INITIAL_URI",
                    Uri.parse("content://downloads/public_downloads")
                )
            }
        }
        builder.setContentIntent(
            PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE
            )
        )
        return builder.build()
    }

    private fun createFailedNotification(senderName: String): Notification {
        return createNotificationBuilder(R.drawable.ic_warning)
            .setContentTitle(getString(R.string.recv_fail))
            .setSubText(senderName)
            .setContentText(getString(R.string.noti_recv_interrupted))
            .setAutoCancel(true)
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun updateNotification(n: Notification) {
        notificationManager.notify(NotificationUtils.RECEIVER_FG_ID, n)
    }

    private fun cleanup() {
        if (!MyApplication.getInstance().getBusy()) {
            Log.d(TAG, "Already cleaned up, skipping")
        }

        okHttpClient.dispatcher.cancelAll()
        p2pManager.cancelConnect(p2pChannel, null)
        p2pManager.removeGroup(p2pChannel, null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationManager.cancel(NotificationUtils.RECEIVER_FG_ID)
        targetAddress = null

        MyApplication.getInstance().clearBusy()
    }

    override fun onDestroy() {
        super.onDestroy()

        Log.d(TAG, "onDestroy")

        unregisterReceiver(p2pReceiver)

        sendBroadcast(ServiceState.getUpdateIntent(false))
    }

    companion object {
        fun getIntent(context: Context, p2pInfo: P2pInfo): Intent {
            return Intent(context, P2pReceiverService::class.java).apply {
                putExtra("p2p_info", p2pInfo)
            }
        }

        private val ACTION_DISMISSED = "moe.reimu.naiveshare.NOTIFICATION_DISMISSED"
        private val ACTION_ACCEPTED = "moe.reimu.naiveshare.NOTIFICATION_ACCEPTED"
        private val ACTION_CANCELLED = "moe.reimu.naiveshare.NOTIFICATION_CANCELLED"
    }
}