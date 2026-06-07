package moe.reimu.catshare

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import moe.reimu.catshare.ui.navigation.CatNavHost
import moe.reimu.catshare.ui.theme.CatShareTheme

class MainActivity : ComponentActivity() {

    private val fileLauncher = registerForActivityResult(ChooseFilesContract()) { uris ->
        if (uris.isNotEmpty()) {
            val intent = Intent(this, ShareActivity::class.java)
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            startActivity(intent)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // 任何被拒绝的权限都会结束 Activity（沿用原逻辑）
        if (results.any { !it.value }) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val permissions = collectPermissions()
        enableEdgeToEdge()
        setContent {
            CatShareTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CatNavHost(onPickFiles = { fileLauncher.launch(null) })
                }
            }
        }
        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun collectPermissions(): List<String> {
        val out = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.NEARBY_WIFI_DEVICES
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            out.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            out.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT <= 32 &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            out.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= 31) {
            for (perm in listOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )) {
                if (ContextCompat.checkSelfPermission(this, perm)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    out.add(perm)
                }
            }
        }
        return out
    }
}

class ChooseFilesContract : ActivityResultContract<Void?, List<Uri>>() {
    override fun createIntent(context: android.content.Context, input: Void?): Intent {
        val cf = Intent(Intent.ACTION_GET_CONTENT)
            .setType("*/*")
            .addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        return Intent.createChooser(cf, context.getString(R.string.choose_files))
    }

    override fun getSynchronousResult(
        context: android.content.Context,
        input: Void?,
    ): ActivityResultContract.SynchronousResult<List<Uri>>? = null

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        val ret = mutableListOf<Uri>()
        val clipData = intent.clipData
        if (clipData != null) {
            for (i in 0 until clipData.itemCount) {
                clipData.getItemAt(i).uri?.let { ret.add(it) }
            }
        } else {
            intent.data?.let { ret.add(it) }
        }
        return ret
    }
}
