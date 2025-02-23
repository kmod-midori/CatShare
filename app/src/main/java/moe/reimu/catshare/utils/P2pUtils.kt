package moe.reimu.catshare.utils

import android.annotation.SuppressLint
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import kotlinx.coroutines.CompletableDeferred

@SuppressLint("MissingPermission")
suspend fun WifiP2pManager.requestGroupInfo(channel: WifiP2pManager.Channel): WifiP2pGroup? {
    val groupInfoFuture = CompletableDeferred<WifiP2pGroup?>()
    requestGroupInfo(channel) {
        groupInfoFuture.complete(it)
    }
    return groupInfoFuture.await()
}

class P2pFutureActionListener: WifiP2pManager.ActionListener {
    val deferred = CompletableDeferred<Unit>()

    override fun onSuccess() {
        deferred.complete(Unit)
    }

    override fun onFailure(reason: Int) {
        val message = when (reason) {
            WifiP2pManager.ERROR -> "ERROR"
            WifiP2pManager.P2P_UNSUPPORTED -> "P2P_UNSUPPORTED"
            WifiP2pManager.BUSY -> "BUSY"
            else -> "code $reason"
        }
        deferred.completeExceptionally(RuntimeException("WiFi P2P operation failed: $message"))
    }
}

@SuppressLint("MissingPermission")
suspend fun WifiP2pManager.createGroupSuspend(channel: WifiP2pManager.Channel, config: WifiP2pConfig) {
    val l = P2pFutureActionListener()
    createGroup(channel, config, l)
    try {
        l.deferred.await()
    } catch (e: Throwable) {
        throw RuntimeException("Failed to create P2P group", e)
    }
}

suspend fun WifiP2pManager.removeGroupSuspend(channel: WifiP2pManager.Channel) {
    val l = P2pFutureActionListener()
    removeGroup(channel, l)
    try {
        l.deferred.await()
    } catch (e: Throwable) {
        throw RuntimeException("Failed to remove P2P group", e)
    }
}

@SuppressLint("MissingPermission")
suspend fun WifiP2pManager.connectSuspend(channel: WifiP2pManager.Channel, config: WifiP2pConfig) {
    val l = P2pFutureActionListener()
    connect(channel, config, l)
    try {
        l.deferred.await()
    } catch (e: Throwable) {
        throw RuntimeException("Failed to connect P2P", e)
    }
}