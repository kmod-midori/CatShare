package moe.reimu.naiveshare.utils

import android.content.Intent

object ServiceState {
    const val ACTION_QUERY_RECEIVER_STATE = "moe.reimu.naiveshare.QUERY_RECEIVER_STATE"
    const val ACTION_UPDATE_RECEIVER_STATE = "moe.reimu.naiveshare.UPDATE_RECEIVER_STATE"
    const val ACTION_STOP_SERVICE = "moe.reimu.naiveshare.STOP_SERVICE"

    fun getQueryIntent() = Intent(ACTION_QUERY_RECEIVER_STATE)
    fun getUpdateIntent(isRunning: Boolean) = Intent(ACTION_UPDATE_RECEIVER_STATE).apply {
        putExtra("isRunning", isRunning)
    }
    fun getStopIntent() = Intent(ACTION_STOP_SERVICE)
}