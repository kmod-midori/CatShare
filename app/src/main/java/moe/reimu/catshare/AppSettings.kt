package moe.reimu.catshare

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class AppSettings(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app", Context.MODE_PRIVATE)

    var deviceName: String
        get() = prefs.getString(
            "deviceName",
            context.getString(R.string.device_name_default_value)
        )!!
        set(value) {
            prefs.edit { putString("deviceName", value) }
        }

    var manualMacAddress: String
        get() = prefs.getString("manualMacAddress", "")?.takeIf { it.isNotBlank() } ?: ""
        set(value) {
            val normalized = normalizeMacAddress(value)
            prefs.edit { putString("manualMacAddress", normalized ?: "") }
        }

    var verbose: Boolean
        get() = prefs.getBoolean("verbose", false)
        set(value) {
            prefs.edit { putBoolean("verbose", value) }
        }

    var autoAccept: Boolean
        get() = prefs.getBoolean("autoAccept", false)
        set(value) {
            prefs.edit { putBoolean("autoAccept", value) }
        }

    companion object {
        fun normalizeMacAddress(value: String?): String? {
            if (value == null) {
                return null
            }

            val cleaned = value.trim().replace(Regex("[\\s:.-]"), "")
            if (cleaned.length != 12) {
                return null
            }

            if (!cleaned.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }) {
                return null
            }

            return cleaned.chunked(2).joinToString(":") { it.lowercase() }
        }
    }
}