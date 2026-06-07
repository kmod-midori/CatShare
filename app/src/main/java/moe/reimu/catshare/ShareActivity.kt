package moe.reimu.catshare

import android.bluetooth.BluetoothManager
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import moe.reimu.catshare.models.FileInfo
import moe.reimu.catshare.ui.theme.CatShareTheme
import moe.reimu.catshare.ui.screens.ShareScreen

class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val btManager = getSystemService(BluetoothManager::class.java)
        val btAdapter = btManager.adapter
        if (btAdapter == null || !btAdapter.isEnabled) {
            Toast.makeText(this, R.string.bluetooth_disabled, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val wifiManager = getSystemService(WifiManager::class.java)
        if (!wifiManager.isWifiEnabled) {
            Toast.makeText(this, R.string.wifi_disabled, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val files = try {
            parseSharedFiles(intent)
        } catch (e: Throwable) {
            Log.e("ShareActivity", "Failed to parse files", e)
            emptyList()
        }
        if (files.isEmpty()) {
            Toast.makeText(this, R.string.no_file_shared, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        enableEdgeToEdge()
        setContent {
            CatShareTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ShareScreen(
                        files = files,
                        onBack = { finish() },
                        onDeviceSelected = {
                            finish()
                        },
                    )
                }
            }
        }
    }

    private fun parseSharedFiles(intent: Intent): List<FileInfo> {
        return if (intent.action == Intent.ACTION_SEND) {
            @Suppress("DEPRECATION")
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (uri != null) {
                listOfNotNull(extractFileInfo(uri))
            } else {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (text != null) {
                    listOf(FileInfo(Uri.EMPTY, "", "", 0, text))
                } else {
                    emptyList()
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            uris?.mapNotNull { extractFileInfo(it) } ?: emptyList()
        }
    }

    private fun extractFileInfo(uri: Uri): FileInfo? {
        val cr = contentResolver
        val proj = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
        )
        return cr.query(uri, proj, null, null, null)?.use {
            if (it.moveToFirst()) {
                val mimeIdx = it.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                FileInfo(
                    uri = uri,
                    name = it.getString(it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)),
                    mimeType = if (mimeIdx < 0) "application/octet-stream"
                    else it.getString(mimeIdx) ?: "application/octet-stream",
                    size = it.getLong(it.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)),
                    textContent = null,
                )
            } else null
        }
    }
}
