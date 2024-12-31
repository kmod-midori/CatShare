package moe.reimu.naiveshare.utils

import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat

object NotificationUtils {
    const val RECEIVER_FG_CHAN_ID = "RECEIVER_FG"
    const val SENDER_CHAN_ID = "SENDER"
    const val RECEIVER_CHAN_ID = "RECEIVER"
    const val OTHER_CHAN_ID = "OTHER"

    const val GATT_SERVER_FG_ID = 1
    const val RECEIVER_FG_ID = 2

    fun createChannels(context: Context) {
        val manager = NotificationManagerCompat.from(context)

        val channels = listOf(
            NotificationChannelCompat.Builder(
                RECEIVER_FG_CHAN_ID,
                NotificationManagerCompat.IMPORTANCE_LOW
            ).setName("Receiver persistent notification (can be disabled)").build(),
            NotificationChannelCompat.Builder(
                SENDER_CHAN_ID,
                NotificationManagerCompat.IMPORTANCE_HIGH
            ).setName("Sending files").build(),
            NotificationChannelCompat.Builder(
                RECEIVER_CHAN_ID,
                NotificationManagerCompat.IMPORTANCE_HIGH
            ).setName("Receiving files").build(),
            NotificationChannelCompat.Builder(
                OTHER_CHAN_ID,
                NotificationManagerCompat.IMPORTANCE_DEFAULT
            ).setName("Other notifications").build(),
        )

        manager.createNotificationChannelsCompat(channels)
    }
}