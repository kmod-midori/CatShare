package moe.reimu.catshare.utils

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import dalvik.system.ZipPathValidator.Callback

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
object ZipPathValidatorCallback : Callback {
    override fun onZipEntryAccess(path: String) {
        Log.d(TAG, path)
        super.onZipEntryAccess(path)
    }
}