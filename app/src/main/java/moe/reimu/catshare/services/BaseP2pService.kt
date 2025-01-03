package moe.reimu.catshare.services

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import moe.reimu.catshare.utils.getReceiverFlags


abstract class BaseP2pService : Service() {
    private val p2pReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("BaseP2pService", "Action: ${intent.action}")
            onP2pBroadcast(intent)
        }
    }

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    protected lateinit var p2pManager: WifiP2pManager
    protected lateinit var p2pChannel: WifiP2pManager.Channel

    protected abstract fun onP2pBroadcast(intent: Intent)

    override fun onCreate() {
        super.onCreate()
        registerReceiver(p2pReceiver, intentFilter, getReceiverFlags())
        p2pManager = getSystemService(WifiP2pManager::class.java)
        p2pChannel = p2pManager.initialize(this, mainLooper, null)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(p2pReceiver)
    }
}