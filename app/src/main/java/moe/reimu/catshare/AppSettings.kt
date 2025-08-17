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
}