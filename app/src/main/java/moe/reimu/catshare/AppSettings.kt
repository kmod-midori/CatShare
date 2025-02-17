package moe.reimu.catshare

import android.content.Context
import android.content.SharedPreferences

class AppSettings(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app", Context.MODE_PRIVATE)

    var deviceName: String
        get() = prefs.getString(
            "deviceName",
            context.getString(R.string.device_name_default_value)
        )!!
        set(value) {
            prefs.edit().putString("deviceName", value).apply()
        }

    var verbose: Boolean
        get() = prefs.getBoolean("verbose", false)
        set(value) {
            prefs.edit().putBoolean("verbose", value).apply()
        }
}