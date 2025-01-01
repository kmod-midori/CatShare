package moe.reimu.naiveshare.services

import android.os.IBinder
import android.util.Log
import moe.reimu.naiveshare.IMacAddressService
import moe.reimu.naiveshare.utils.TAG
import java.net.NetworkInterface
import kotlin.system.exitProcess

class MacAddressService: IMacAddressService.Stub() {
    override fun destroy() {
        exit()
    }

    override fun exit() {
        Log.i(TAG, "Exit")
        exitProcess(0)
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun getP2pMacAddress(): String? {
        val ifs = NetworkInterface.getNetworkInterfaces()
        for (intf in ifs) {
            if (intf.name == "p2p0") {
                return intf.hardwareAddress?.toHexString(HexFormat {
                    bytes.byteSeparator = ":"
                })
            }
        }
        return null
    }
}