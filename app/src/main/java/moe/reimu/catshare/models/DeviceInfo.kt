package moe.reimu.catshare.models

import kotlinx.serialization.Serializable

@Serializable
data class DeviceInfo(val state: Int, val key: String?, val mac: String)
