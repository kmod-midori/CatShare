package moe.reimu.naiveshare.utils

import java.nio.ByteBuffer
import java.util.Arrays
import java.util.Random
import java.util.UUID
import kotlin.math.abs


object BleUtils {
    val ADV_SERVICE_UUID = UUID.fromString("00003331-0000-1000-8000-008123456789")
    val SERVICE_UUID = UUID.fromString("00009955-0000-1000-8000-00805f9b34fb")
    val CHAR_STATUS_UUID = UUID.fromString("00009954-0000-1000-8000-00805f9b34fb")
    val CHAR_P2P_UUID = UUID.fromString("00009953-0000-1000-8000-00805f9b34fb")

    val RANDOM_DATA: ByteArray = run {
        val random = Random()
        Arrays.copyOfRange(
            ByteBuffer.allocate(8).putLong(abs(random.nextLong())).array(),
            0,
            2
        )
    }

    fun getSenderId(): String {
        val senderIdRaw = RANDOM_DATA[0].toInt().shl(8).or(RANDOM_DATA[1].toInt())
        return String.format("%04x", senderIdRaw)
    }
}