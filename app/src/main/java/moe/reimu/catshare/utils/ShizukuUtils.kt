package moe.reimu.catshare.utils

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import moe.reimu.catshare.BuildConfig
import moe.reimu.catshare.IMacAddressService
import moe.reimu.catshare.services.MacAddressService
import rikka.shizuku.Shizuku

object ShizukuUtils {
    private val binderLock = Object()
    private val serviceNotify = Object()
    private var macService: IMacAddressService? = null

    init {
        Shizuku.addBinderReceivedListenerSticky {
            bindService()
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder?) {
            if (service != null && service.pingBinder()) {
                Log.d(ShizukuUtils.TAG, "Got service connection for $name")

                synchronized(binderLock) {
                    macService = IMacAddressService.Stub.asInterface(service)
                }

                synchronized(serviceNotify) {
                    serviceNotify.notifyAll()
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Log.d(ShizukuUtils.TAG, "Connection lost for $name")
            synchronized(binderLock) {
                macService = null
            }
        }
    }

    fun unsafeBindService() {
        val cn = ComponentName(
            BuildConfig.APPLICATION_ID, MacAddressService::class.java.name
        )
        val args = Shizuku.UserServiceArgs(cn)
            .daemon(false)
            .processNameSuffix("service")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
        Shizuku.bindUserService(args, serviceConnection)
    }

    fun bindService() {
        try {
            unsafeBindService()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to bind service", e)
        }
    }

    fun unsafeGetMacAddress(name: String): String? {
        synchronized(binderLock) {
            val svc = macService
            if (svc == null) {
                Log.d(TAG, "MAC service is null, trying to bind")
                unsafeBindService()
            } else {
                return svc.getMacAddressByName(name)
            }
        }

        synchronized(serviceNotify) {
            serviceNotify.wait(1000 * 20)
        }

        synchronized(binderLock) {
            return macService?.p2pMacAddress
        }
    }

    fun getMacAddress(name: String, l: (String?) -> Unit) {
        val th = Thread {
            val res = try {
                unsafeGetMacAddress(name)
            } catch (e: Throwable) {
                Log.e(ShizukuUtils.TAG, "Failed to obtain MAC address for $name", e)
                null
            }

            l(res)
        }
        th.start()
    }

    suspend fun getMacAddress(name: String): String? {
        val fut = CompletableDeferred<String?>()
        getMacAddress(name) {
            fut.complete(it)
        }
        return fut.await()
    }
}