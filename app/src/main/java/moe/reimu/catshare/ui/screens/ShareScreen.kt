package moe.reimu.catshare.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import moe.reimu.catshare.models.DiscoveredDevice
import moe.reimu.catshare.models.FileInfo
import moe.reimu.catshare.ui.components.CatCard
import moe.reimu.catshare.ui.components.CatCardStyle
import moe.reimu.catshare.ui.components.CatIconContainer
import moe.reimu.catshare.ui.components.EmptyState
import moe.reimu.catshare.ui.components.ErrorState
import moe.reimu.catshare.ui.components.ScanningState
import moe.reimu.catshare.utils.BleUtils
import moe.reimu.catshare.viewmodel.ShareViewModel

// ============================================================
// ShareScreen — 选择接收设备界面
// 重构亮点：
//  - 文件预览卡片（显示待发送文件信息）
//  - 扫描动画与设备列表的分区布局
//  - 设备卡片显示品牌、连接能力等信息
//  - 发送中状态过渡
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(
    fileUris: List<Uri>,
    fileNames: List<String>,
    fileSizes: List<Long>,
    onBack: () -> Unit,
    onStartSending: (DiscoveredDevice, List<FileInfo>) -> Unit,
    viewModel: ShareViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    // 初始化文件信息
    LaunchedEffect(fileUris) {
        val files = fileUris.indices.map { i ->
            FileInfo(
                uri = fileUris[i],
                name = fileNames.getOrElse(i) { "unknown" },
                size = fileSizes.getOrElse(i) { 0L },
            )
        }
        viewModel.setFiles(files)
        viewModel.startScanning()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "选择接收者",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.stopScanning()
                        onBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                )
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
            // ── 文件预览区域 ──
            item(key = "file_preview_header") {
                Text(
                    text = "待发送文件",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            itemsIndexed(
                items = uiState.files,
                key = { index, _ -> "file_$index" }
            ) { _, file ->
                FilePreviewCard(file = file)
            }

            // ── 设备列表区域 ──
            item(key = "device_list_header") {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "附近设备",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (uiState.isScanning) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                strokeCap = StrokeCap.Round,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "扫描中",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // ── 扫描中 / 空状态 / 设备列表 ──
            when {
                uiState.isSending -> {
                    item(key = "sending") {
                        SendingCard(device = uiState.sendingDevice)
                    }
                }
                !uiState.isBluetoothEnabled -> {
                    item(key = "bluetooth_error") {
                        ErrorState(
                            title = "蓝牙未开启",
                            message = "请开启蓝牙以扫描附近设备",
                            icon = Icons.Filled.Bluetooth,
                            retryLabel = "重试",
                            onRetry = { viewModel.startScanning() }
                        )
                    }
                }
                uiState.isScanning && uiState.discoveredDevices.isEmpty() -> {
                    item(key = "scanning") {
                        ScanningState(
                            message = "正在扫描附近设备...",
                            modifier = Modifier.height(240.dp)
                        )
                    }
                }
                !uiState.isScanning && uiState.discoveredDevices.isEmpty() -> {
                    item(key = "empty") {
                        EmptyState(
                            title = "未发现设备",
                            message = "请确保附近设备已开启互传功能",
                            icon = Icons.Filled.Devices,
                            actionLabel = "重新扫描",
                            onAction = { viewModel.startScanning() },
                            modifier = Modifier.height(240.dp)
                        )
                    }
                }
                else -> {
                    items(
                        items = uiState.discoveredDevices,
                        key = { it.address }
                    ) { device ->
                        DeviceCard(
                            device = device,
                            onClick = {
                                onStartSending(device, uiState.files)
                            }
                        )
                    }
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * 文件预览卡片
 */
@Composable
private fun FilePreviewCard(file: FileInfo) {
    CatCard(
        title = file.name,
        subtitle = formatFileSize(file.size),
        icon = Icons.Filled.InsertDriveFile,
        style = CatCardStyle.Filled,
    )
}

/**
 * 设备卡片
 */
@Composable
private fun DeviceCard(
    device: DiscoveredDevice,
    onClick: () -> Unit,
) {
    val brandName = BleUtils.getBrandName(device.brandId)

    CatCard(
        title = device.name,
        subtitle = buildString {
            append(brandName)
            if (device.supports5Ghz) {
                append(" · 5GHz")
            }
        },
        icon = when (brandName) {
            "小米" -> Icons.Filled.PhoneAndroid
            "OPPO" -> Icons.Filled.Smartphone
            "vivo" -> Icons.Filled.Smartphone
            else -> Icons.Filled.AccountCircle
        },
        style = CatCardStyle.Elevated,
        onClick = onClick,
    )
}

/**
 * 发送中状态卡片
 */
@Composable
private fun SendingCard(device: DiscoveredDevice?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CatIconContainer(
            icon = Icons.Filled.Send,
            size = 72,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "正在发送到 ${device?.name ?: "..."}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(16.dp))
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(6.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
        )
    }
}

/**
 * 文件大小格式化
 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
        else -> "${"%.2f".format(bytes.toDouble() / (1024 * 1024 * 1024))} GB"
    }
}