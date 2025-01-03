package moe.reimu.catshare.models

import android.bluetooth.BluetoothDevice
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class DiscoveredDevice(
    val device: BluetoothDevice,
    val id: String,
    val name: String,
    val brand: String?,
    val supports5Ghz: Boolean
) : Parcelable
