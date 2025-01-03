package moe.reimu.catshare.services


import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import moe.reimu.catshare.StartReceiverActivity
import moe.reimu.catshare.utils.ServiceState
import moe.reimu.catshare.utils.TAG
import moe.reimu.catshare.utils.registerInternalBroadcastReceiver
import java.lang.ref.WeakReference
import kotlin.random.Random

class ReceiverTileService : TileService() {
    private class MyReceiver(tileService: ReceiverTileService) : BroadcastReceiver() {
        private val serviceRef = WeakReference(tileService)

        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ServiceState.ACTION_UPDATE_RECEIVER_STATE) {
                serviceRef.get()?.setState(
                    intent.getBooleanExtra("isRunning", false)
                )
            }
        }

    }

    private var receiver: MyReceiver? = null

    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    override fun onClick() {
        val intent = when (qsTile.state) {
            Tile.STATE_ACTIVE -> StartReceiverActivity.getIntent(this, true)
            Tile.STATE_INACTIVE -> StartReceiverActivity.getIntent(this, false)
            else -> null
        }

        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)

        if (intent != null) {
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(
                        this,
                        Random.nextInt(),
                        intent,
                        PendingIntent.FLAG_IMMUTABLE
                    )
                )
            } else {
                startActivityAndCollapse(intent)
            }
        }
    }

    private fun setState(enabled: Boolean) {
        qsTile?.state = if (enabled) {
            Tile.STATE_ACTIVE
        } else {
            Tile.STATE_INACTIVE
        }
        qsTile?.updateTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        Log.d(TAG, "onStartListening")
        setState(false)

        val r = MyReceiver(this)
        receiver = r
        registerInternalBroadcastReceiver(
            r, IntentFilter().apply {
                addAction(ServiceState.ACTION_UPDATE_RECEIVER_STATE)
            }
        )

        sendBroadcast(ServiceState.getQueryIntent())
    }

    override fun onStopListening() {
        super.onStopListening()
        Log.d(TAG, "onStopListening")
        receiver?.let {
            unregisterReceiver(it)
        }
        receiver = null
    }
}