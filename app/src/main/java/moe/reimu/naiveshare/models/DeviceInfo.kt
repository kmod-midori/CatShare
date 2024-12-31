package moe.reimu.naiveshare.models

import kotlinx.serialization.Serializable

@Serializable
data class DeviceInfo(val state: Int, val key: String?, val mac: String)
