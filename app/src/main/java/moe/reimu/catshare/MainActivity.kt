package moe.reimu.catshare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import moe.reimu.catshare.ui.navigation.MainRoute
import moe.reimu.catshare.ui.navigation.SettingsRoute
import moe.reimu.catshare.ui.navigation.ShareRoute
import moe.reimu.catshare.ui.screens.MainScreen
import moe.reimu.catshare.ui.screens.SettingsScreen
import moe.reimu.catshare.ui.screens.ShareScreen
import moe.reimu.catshare.ui.theme.CatShareTheme

// ============================================================
// MainActivity — 重构后的主入口
// 使用 Jetpack Navigation Compose 类型安全路由
// ============================================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 处理来自 ShareActivity 的 Intent
        val shareUris = intent.getParcelableArrayExtra(Intent.EXTRA_STREAM)
            ?.filterIsInstance<android.net.Uri>() ?: emptyList()
        val shareNames = intent.getStringArrayExtra("fileNames")?.toList() ?: emptyList()
        val shareSizes = intent.getLongArrayExtra("fileSizes")?.toList() ?: emptyList()

        setContent {
            CatShareTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    val startDestination: Any = if (shareUris.isNotEmpty()) {
                        ShareRoute(
                            fileUris = shareUris.map { it.toString() },
                            fileNames = shareNames,
                            fileSizes = shareSizes,
                        )
                    } else {
                        MainRoute
                    }

                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                    ) {
                        composable<MainRoute> {
                            MainScreen(
                                onNavigateToSettings = {
                                    navController.navigate(SettingsRoute)
                                },
                                onNavigateToShare = { uris ->
                                    // 暂时简化：直接跳转，文件名和大小从 URI 解析
                                    navController.navigate(
                                        ShareRoute(
                                            fileUris = uris.map { it.toString() },
                                            fileNames = uris.map { uri ->
                                                resolveFileName(uri) ?: "unknown"
                                            },
                                            fileSizes = uris.map { resolveFileSize(it) },
                                        )
                                    )
                                }
                            )
                        }

                        composable<ShareRoute> { backStackEntry ->
                            val route: ShareRoute = backStackEntry.toRoute()
                            ShareScreen(
                                fileUris = route.fileUris.map { android.net.Uri.parse(it) },
                                fileNames = route.fileNames,
                                fileSizes = route.fileSizes,
                                onBack = { navController.popBackStack() },
                                onStartSending = { device, files ->
                                    // 启动 P2pSenderService
                                    startSendingFiles(device, files)
                                }
                            )
                        }

                        composable<SettingsRoute> {
                            SettingsScreen(
                                onBack = { navController.popBackStack() },
                                onCaptureLogs = {
                                    captureAndShareLogs()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun resolveFileName(uri: android.net.Uri): String? {
        return try {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) it.getString(nameIndex) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveFileSize(uri: android.net.Uri): Long {
        return try {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (sizeIndex >= 0) it.getLong(sizeIndex) else 0L
                } else 0L
            } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun startSendingFiles(
        device: moe.reimu.catshare.models.DiscoveredDevice,
        files: List<moe.reimu.catshare.models.FileInfo>
    ) {
        // 启动 P2pSenderService
        val intent = android.content.Intent(this, moe.reimu.catshare.services.P2pSenderService::class.java).apply {
            putExtra("device", device)
            putParcelableArrayListExtra("files", ArrayList(files))
        }
        startService(intent)
    }

    private fun captureAndShareLogs() {
        try {
            val process = Runtime.getRuntime().exec("logcat -d -v time")
            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            val logs = reader.readText()
            reader.close()

            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, logs)
                putExtra(android.content.Intent.EXTRA_SUBJECT, "CatShare Logs")
            }
            startActivity(android.content.Intent.createChooser(shareIntent, "分享日志"))
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "捕获日志失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}