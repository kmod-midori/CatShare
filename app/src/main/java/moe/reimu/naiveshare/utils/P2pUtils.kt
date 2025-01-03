package moe.reimu.naiveshare.utils

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
        deferred.completeExceptionally(RuntimeException("Operation failed with code $reason"))
    }
}

@SuppressLint("MissingPermission")
suspend fun WifiP2pManager.createGroupSuspend(channel: WifiP2pManager.Channel, config: WifiP2pConfig) {
    val l = P2pFutureActionListener()
    createGroup(channel, config, l)
    l.deferred.await()
}

suspend fun WifiP2pManager.removeGroup(channel: WifiP2pManager.Channel) {
    val l = P2pFutureActionListener()
    removeGroup(channel, l)
    l.deferred.await()
}

@SuppressLint("MissingPermission")
suspend fun WifiP2pManager.connectSuspend(channel: WifiP2pManager.Channel, config: WifiP2pConfig) {
    val l = P2pFutureActionListener()
    connect(channel, config, l)
    l.deferred.await()
}