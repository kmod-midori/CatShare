package moe.reimu.catshare.utils

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import moe.reimu.catshare.BuildConfig

val INTERNAL_BROADCAST_PERMISSION = "${BuildConfig.APPLICATION_ID}.INTERNAL_BROADCASTS"

fun Context.checkBluetoothPermissions(): Boolean {
    if (Build.VERSION.SDK_INT <= 32) {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
    }

    if (Build.VERSION.SDK_INT >= 31) {
        for (perm in listOf(
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    perm
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
    }

    return true
}

fun Context.checkP2pPermissions(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
            this, Manifest.permission.NEARBY_WIFI_DEVICES
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return false
    }

    if (Build.VERSION.SDK_INT <= 32) {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
    }

    return true
}

fun Context.registerInternalBroadcastReceiver(receiver: BroadcastReceiver, filter: IntentFilter) {
    registerReceiver(receiver, filter, INTERNAL_BROADCAST_PERMISSION, null, getReceiverFlags())
}

fun getReceiverFlags(): Int {
    return if (Build.VERSION.SDK_INT >= 33) {
        Context.RECEIVER_EXPORTED
    } else {
        0
    }
}