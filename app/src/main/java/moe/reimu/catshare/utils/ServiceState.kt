package moe.reimu.catshare.utils

import android.content.Intent
import moe.reimu.catshare.BuildConfig

object ServiceState {
    const val ACTION_QUERY_RECEIVER_STATE = "${BuildConfig.APPLICATION_ID}.QUERY_RECEIVER_STATE"
    const val ACTION_UPDATE_RECEIVER_STATE = "${BuildConfig.APPLICATION_ID}.UPDATE_RECEIVER_STATE"
    const val ACTION_STOP_SERVICE = "${BuildConfig.APPLICATION_ID}.STOP_SERVICE"

    fun getQueryIntent() = Intent(ACTION_QUERY_RECEIVER_STATE)
    fun getUpdateIntent(isRunning: Boolean) = Intent(ACTION_UPDATE_RECEIVER_STATE).apply {
        putExtra("isRunning", isRunning)
    }

    fun getStopIntent() = Intent(ACTION_STOP_SERVICE)
}