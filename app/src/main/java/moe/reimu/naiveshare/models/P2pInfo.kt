package moe.reimu.naiveshare.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class P2pInfo(
    val ssid: String,
    val psk: String,
    val mac: String,
    val port: Int,
    val key: String? = null,
) : Parcelable