package moe.reimu.naiveshare

import android.app.Application
import android.util.Log
import moe.reimu.naiveshare.utils.NotificationUtils
import moe.reimu.naiveshare.utils.TAG
import java.util.concurrent.atomic.AtomicBoolean

class MyApplication : Application() {
    private val isBusy = AtomicBoolean()

    override fun onCreate() {
        super.onCreate()

        instance = this

        NotificationUtils.createChannels(this)
    }

    fun setBusy() = if (isBusy.compareAndSet(false, true)) {
        Log.i(TAG, "Setting busy flag")
        true
    } else {
        false
    }

    fun clearBusy() {
        Log.i(TAG, "Clearing busy flag")
        isBusy.set(false)
    }

    fun getBusy() = isBusy.get()

    companion object {
        private var instance: MyApplication? = null

        fun getInstance() = instance!!
    }
}