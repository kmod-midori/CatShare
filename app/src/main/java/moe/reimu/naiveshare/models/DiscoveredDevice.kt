package moe.reimu.naiveshare.models

import android.bluetooth.BluetoothDevice

data class DiscoveredDevice(
    val device: BluetoothDevice,
    val name: String,
    val brand: String?,
    val supports5Ghz: Boolean
)
