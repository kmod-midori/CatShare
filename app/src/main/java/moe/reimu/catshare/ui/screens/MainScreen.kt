package moe.reimu.catshare.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import moe.reimu.catshare.ui.components.CatCard
import moe.reimu.catshare.ui.components.CatCardStyle
import moe.reimu.catshare.ui.components.CatIconContainer
import moe.reimu.catshare.ui.components.ScanningState
import moe.reimu.catshare.viewmodel.MainEvent
import moe.reimu.catshare.viewmodel.MainViewModel

// ============================================================
// MainScreen — CatShare 主界面
// 重构亮点：
//  - 大标题 Hero 区域 + 品牌卡牌式列表
//  - 动画过渡：每张卡片依次滑入
//  - 状态驱动的 UI 反馈
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToShare: (List<Uri>) -> Unit,
    viewModel: MainViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // 处理一次性事件
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is MainEvent.ShowToast -> {
                    android.widget.Toast.makeText(context, event.message, android.widget.Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }
    }

    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            onNavigateToShare(uris)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CatShareTopAppBar(
                onSettingsClick = onNavigateToSettings
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Hero 区域 ──
            item(key = "hero") {
                HeroSection()
            }

            // ── 发送文件卡片 ──
            item(key = "send_card") {
                CatCard(
                    title = "发送文件",
                    subtitle = "选择文件发送到附近的互传设备",
                    icon = Icons.Filled.Share,
                    style = CatCardStyle.Elevated,
                    onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                )
            }

            // ── 接收开关卡片 ──
            item(key = "receiver_card") {
                CatCard(
                    title = "可被发现",
                    subtitle = if (uiState.isReceiverRunning) "正在广播，附近设备可发现你" else "关闭后其他设备将无法发现你",
                    icon = Icons.Filled.BluetoothSearching,
                    style = CatCardStyle.Elevated,
                    trailing = {
                        Switch(
                            checked = uiState.isReceiverRunning,
                            onCheckedChange = { viewModel.toggleReceiver() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        )
                    }
                )
            }

            // ── Shizuku 权限卡片（条件显示） ──
            item(key = "shizuku_card") {
                AnimatedVisibility(
                    visible = uiState.isShizukuRequired,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    CatCard(
                        title = if (uiState.shizukuGranted) "Shizuku 已授权" else "需要 Shizuku 权限",
                        subtitle = if (uiState.shizukuGranted) "已获得系统级权限，可以正常发送文件"
                        else "发送文件需要 Shizuku 获取 MAC 地址，请点击授权",
                        icon = if (uiState.shizukuGranted) Icons.Filled.Done else Icons.Filled.Close,
                        style = if (uiState.shizukuGranted) CatCardStyle.Filled else CatCardStyle.Elevated,
                        onClick = if (!uiState.shizukuGranted) {
                            { viewModel.requestShizukuPermission() }
                        } else null,
                    )
                }
            }

            // ── 状态卡片区域 ──
            item(key = "status_section") {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "连接状态",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            item(key = "status_bluetooth") {
                StatusCard(
                    label = "蓝牙",
                    isActive = uiState.isBluetoothEnabled,
                )
            }

            item(key = "status_wifi") {
                StatusCard(
                    label = "Wi-Fi",
                    isActive = uiState.isWifiEnabled,
                )
            }

            // 底部间距
            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * 品牌 Hero 区域 — 大标题 + 描述
 */
@Composable
private fun HeroSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 猫图标占位
        CatIconContainer(
            icon = Icons.Filled.Share,
            size = 80,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "CatShare",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "让每台 Android 设备都能互传",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 状态指示卡片 — 简洁的蓝牙/WiFi 状态
 */
@Composable
private fun StatusCard(
    label: String,
    isActive: Boolean,
) {
    val alpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.5f,
        animationSpec = spring(),
        label = "statusAlpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = if (isActive) "已开启" else "未开启",
            style = MaterialTheme.typography.labelMedium,
            color = if (isActive) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 自定义顶栏 — 透明背景 + 设置按钮
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatShareTopAppBar(
    onSettingsClick: () -> Unit,
) {
    TopAppBar(
        title = {},
        modifier = Modifier.statusBarsPadding(),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
        ),
        actions = {
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "设置",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}