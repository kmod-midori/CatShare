package moe.reimu.catshare.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pGroup
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
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import moe.reimu.catshare.BuildConfig
import moe.reimu.catshare.FakeTrustManager
import moe.reimu.catshare.MyApplication
import moe.reimu.catshare.R
import moe.reimu.catshare.exceptions.CancelledByUserException
import moe.reimu.catshare.models.P2pInfo
import moe.reimu.catshare.models.ReceivedFile
import moe.reimu.catshare.models.WebSocketMessage
import moe.reimu.catshare.utils.NotificationUtils
import moe.reimu.catshare.utils.ProgressCounter
import moe.reimu.catshare.utils.ServiceState
import moe.reimu.catshare.utils.TAG
import moe.reimu.catshare.utils.awaitWithTimeout
import moe.reimu.catshare.utils.checkP2pPermissions
import moe.reimu.catshare.utils.connectSuspend
import moe.reimu.catshare.utils.registerInternalBroadcastReceiver
import okhttp3.ConnectionPool
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import javax.net.ssl.SSLContext
import kotlin.math.min
import kotlin.random.Random

class P2pReceiverService : BaseP2pService() {
    private lateinit var notificationManager: NotificationManagerCompat

    private val internalReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_CANCEL_RECEIVING -> {
                    cancel(intent.getIntExtra("taskId", -1))
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "onCreate")

        if (!checkP2pPermissions()) {
            stopSelf()
            return
        }

        notificationManager = NotificationManagerCompat.from(this)

        registerInternalBroadcastReceiver(internalReceiver, IntentFilter().apply {
            addAction(ACTION_CANCEL_RECEIVING)
        })
    }

    private var p2pFuture = CompletableDeferred<Pair<WifiP2pInfo, WifiP2pGroup>>()

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
                Log.d(P2pReceiverService.TAG, "P2P info: $connInfo, P2P group: $group")

                if (connInfo.groupFormed && !connInfo.isGroupOwner && group != null) {
                    p2pFuture.complete(Pair(connInfo, group))
                }
            }
        }
    }



    private val currentTaskLock = Object()
    private var currentJob: Job? = null
    private var curreentTaskId: Int? = null

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
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

        val info = intent.getParcelableExtra<P2pInfo>("p2p_info") ?: return START_NOT_STICKY
        val localTaskId = Random.nextInt()
        val job = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                startForeground(
                    NotificationUtils.RECEIVER_FG_ID,
                    createPrepareNotification(getString(R.string.noti_connecting)),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
                runReceive(info, localTaskId)
            } catch (e: CancelledByUserException) {
                Log.i(TAG, "Cancelled by user")
                notificationManager.notify(
                    Random.nextInt(),
                    createFailedNotification()
                )
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to process task", e)
                notificationManager.notify(
                    Random.nextInt(),
                    createFailedNotification()
                )
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                MyApplication.getInstance().clearBusy()
            }
        }

        synchronized(currentTaskLock) {
            curreentTaskId = localTaskId
            currentJob = job
        }


        return START_NOT_STICKY
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
            .setSmallIcon(icon).setPriority(NotificationCompat.PRIORITY_MAX)
    }

    private fun createPrepareNotification(description: String) =
        createNotificationBuilder(R.drawable.ic_downloading).setOngoing(true)
            .setContentTitle(getString(R.string.preparing_transmission)).setContentText(description)
            .build()

    private fun createAskingNotification(
        taskId: Int,
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
            taskId,
            Intent(ACTION_DISMISSED).apply { putExtra("taskId", taskId) },
            PendingIntent.FLAG_IMMUTABLE
        )

        val acceptIntent = PendingIntent.getBroadcast(
            this,
            taskId,
            Intent(ACTION_ACCEPTED).apply { putExtra("taskId", taskId) },
            PendingIntent.FLAG_IMMUTABLE
        )

        val n = createNotificationBuilder(R.drawable.ic_downloading).setContentTitle(senderName)
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
        taskId: Int, senderName: String, totalSize: Long, processedSize: Long?
    ): Notification {
        val cancelIntent = PendingIntent.getBroadcast(
            this,
            taskId,
            Intent(ACTION_CANCEL_RECEIVING).apply { putExtra("taskId", taskId) },
            PendingIntent.FLAG_IMMUTABLE
        )

        val n =
            createNotificationBuilder(R.drawable.ic_downloading).setContentTitle(getString(R.string.receiving))
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
        val builder =
            createNotificationBuilder(R.drawable.ic_done).setContentTitle(getString(R.string.recv_ok))
                .setSubText(senderName).setAutoCancel(true).setContentText(
                    if (isPartial) {
                        resources.getQuantityString(
                            R.plurals.noti_complete_partial, receivedFiles.size, receivedFiles.size
                        )
                    } else {
                        resources.getQuantityString(
                            R.plurals.noti_complete, receivedFiles.size, receivedFiles.size
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

    private fun createFailedNotification(): Notification {
        return createNotificationBuilder(R.drawable.ic_warning)
            .setContentTitle(getString(R.string.recv_fail))
            .setContentText(getString(R.string.noti_recv_interrupted))
            .setAutoCancel(true).build()
    }

    @SuppressLint("MissingPermission")
    private fun updateNotification(n: Notification) {
        notificationManager.notify(NotificationUtils.RECEIVER_FG_ID, n)
    }

    @SuppressLint("MissingPermission")
    private suspend fun runReceive(p2pInfo: P2pInfo, localTaskId: Int) = coroutineScope {
        val client = HttpClient(OkHttp) {
            install(WebSockets)
            engine {
                config {
                    val sslContext = SSLContext.getInstance("TLSv1.2")
                    val tm = FakeTrustManager()
                    sslContext.init(null, arrayOf(tm), SecureRandom())

                    connectTimeout(3, TimeUnit.SECONDS)
                    connectionPool(
                        ConnectionPool(5, 10, TimeUnit.SECONDS)
                    )
                    sslSocketFactory(sslContext.socketFactory, tm)
                    hostnameVerifier { _, _ -> true }
                }
            }
        }

        val p2pConfig =
            WifiP2pConfig.Builder().setNetworkName(p2pInfo.ssid).setPassphrase(p2pInfo.psk).build()

        try {
            p2pFuture = CompletableDeferred()
            p2pManager.connectSuspend(p2pChannel, p2pConfig)
            try {
                val (wifiP2pInfo, wifiP2pGroup) = p2pFuture.awaitWithTimeout(
                    Duration.ofSeconds(10), "Waiting for P2P connect"
                )

                val hostPort = "${wifiP2pInfo.groupOwnerAddress.hostAddress}:${p2pInfo.port}"

                val sendRequestFuture = CompletableDeferred<JSONObject>()
                val statusFuture = CompletableDeferred<Pair<Int, String>>()

                val wsSession = client.webSocketSession("wss://${hostPort}/websocket")

                val downloadJob = async {
                    val sendRequestPayload = sendRequestFuture.awaitWithTimeout(
                        Duration.ofSeconds(5), "Waiting for send request"
                    )

                    val taskId = sendRequestPayload.optString(
                        "taskId", sendRequestPayload.optString("id")
                    )
                    val senderName = sendRequestPayload.getString("senderName")
                    val fileName = sendRequestPayload.getString("fileName")
                    val totalSize = sendRequestPayload.getLong("totalSize")
                    val fileCount = sendRequestPayload.getInt("fileCount")

                    val thumbPath = sendRequestPayload.optString("thumbnail")
                    val bigPicture = if (thumbPath.isNotEmpty()) {
                        val thumbUrl = "https://${hostPort}$thumbPath"
                        Log.d(TAG, "Fetching thumbnail from $thumbUrl")

                        val body = client.get(thumbUrl).bodyAsBytes()
                        BitmapFactory.decodeByteArray(body, 0, body.size)
                    } else null

                    updateNotification(
                        createAskingNotification(
                            localTaskId, senderName, fileName, fileCount, totalSize, bigPicture
                        )
                    )

                    val userResponse = withTimeoutOrNull(10000L) {
                        waitForAction(localTaskId)
                    }

                    if (userResponse != true) {
                        wsSession.send(Frame.Text(WebSocketMessage.makeStatus(99, taskId, 3, "user refuse").toText()))
                        wsSession.flush()
                        throw CancelledByUserException()
                    }

                    updateNotification(
                        createProgressNotification(
                            localTaskId, senderName, totalSize, null
                        )
                    )

                    val downloadUrl = "https://${hostPort}/download?taskId=${taskId}"

                    val files = client.prepareGet(downloadUrl).execute { downloadRes ->
                        val ist = downloadRes.bodyAsChannel().toInputStream()

                        val progress = ProgressCounter(totalSize) { total, processed ->
                            updateNotification(
                                createProgressNotification(
                                    localTaskId,
                                    senderName,
                                    total,
                                    processed
                                )
                            )
                        }

                        ZipInputStream(ist).use { zipStream ->
                            saveArchive(zipStream, progress)
                        }
                    }

                    if (files.isNotEmpty()) {
                        notificationManager.notify(
                            Random.nextInt(), createCompletedNotification(
                                senderName, files, files.size != fileCount
                            )
                        )

                        try {
                            val st = WebSocketMessage.makeStatus(0, taskId, 1, "done")
                            wsSession.send(Frame.Text(st.toText()))
                            wsSession.flush()
                        } catch (e: Throwable) {
                            // ignore
                        }
                    } else {
                        throw IllegalStateException("Failed to receive any file")
                    }
                }

                while (true) {
                    val run = select {
                        wsSession.incoming.onReceive { frame ->
                            val text = (frame as? Frame.Text)?.readText()
                                ?: throw IllegalArgumentException("Got non-text frame")
                            val message = WebSocketMessage.fromText(text)
                                ?: throw IllegalArgumentException("Failed to parse message")

                            Log.d(TAG, "WS message: $message")

                            if (message.type != "action") {
                                return@onReceive true// We only care about `action`
                            }

                            val payload = message.payload ?: return@onReceive true

                            val r = when (message.name.lowercase()) {
                                "versionnegotiation" -> {
                                    val inVersion = payload.optInt("version", 0)
                                    val currentVersion = min(inVersion, 1)

                                    JSONObject()
                                        .put("version", currentVersion)
                                        .put("threadLimit", 5)
                                }

                                "sendrequest" -> {
                                    sendRequestFuture.complete(payload)
                                    null
                                }

                                "status" -> {
                                    statusFuture.complete(
                                        Pair(
                                            payload.optInt("type"), payload.optString("reason")
                                        )
                                    )
                                    null
                                }

                                else -> {
                                    null
                                }
                            }

                            val ack = WebSocketMessage("ack", message.id, message.name, r)
                            wsSession.send(Frame.Text(ack.toText()))
                            true
                        }
                        downloadJob.onAwait {
                            // Completed successfully
                            false
                        }
                        statusFuture.onAwait { status ->
                            if (status.first == 3 && status.second == "user refuse") {
                                throw CancelledByUserException()
                            }
                            throw RuntimeException("Transfer terminated with $status")
                        }
                    }

                    if (!run) {
                        break
                    }
                }
            } finally {
                p2pManager.removeGroup(p2pChannel, null)
                p2pManager.cancelConnect(p2pChannel, null)
            }
        } finally {
            client.close()
        }
    }

    private fun saveArchive(
        zipStream: ZipInputStream,
        progress: ProgressCounter
    ): List<ReceivedFile> {
        val receivedFiles = mutableListOf<ReceivedFile>()
        var processedSize = 0L

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
                )
                    ?: throw RuntimeException("Failed to write ${entryFile.name} to media store")

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
                            progress.update(processedSize)
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
                break
            }
        }

        return receivedFiles
    }

    private suspend fun waitForAction(taskId: Int) = suspendCancellableCoroutine {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.getIntExtra("taskId", -1) != taskId) {
                    return
                }

                when (intent.action) {
                    ACTION_ACCEPTED -> it.resume(true) {}
                    ACTION_DISMISSED -> it.resume(false) {}
                }
            }
        }

        registerInternalBroadcastReceiver(receiver, IntentFilter().apply {
            addAction(ACTION_ACCEPTED)
            addAction(ACTION_DISMISSED)
        })

        it.invokeOnCancellation {
            unregisterReceiver(receiver)
        }
    }

    fun cancel(taskId: Int) {
        synchronized(currentTaskLock) {
            if (curreentTaskId == taskId) {
                currentJob?.cancel(CancelledByUserException())
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        Log.d(TAG, "onDestroy")

        sendBroadcast(ServiceState.getUpdateIntent(false))

        unregisterReceiver(internalReceiver)
    }

    companion object {
        fun getIntent(context: Context, p2pInfo: P2pInfo): Intent {
            return Intent(context, P2pReceiverService::class.java).apply {
                putExtra("p2p_info", p2pInfo)
            }
        }

        private val ACTION_DISMISSED = "${BuildConfig.APPLICATION_ID}.NOTIFICATION_DISMISSED"
        private val ACTION_ACCEPTED = "${BuildConfig.APPLICATION_ID}.NOTIFICATION_ACCEPTED"
        private val ACTION_CANCEL_RECEIVING = "${BuildConfig.APPLICATION_ID}.CANCEL_RECEIVING"
    }
}