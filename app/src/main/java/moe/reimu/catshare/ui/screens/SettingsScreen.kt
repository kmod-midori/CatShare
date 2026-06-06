package moe.reimu.catshare.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import moe.reimu.catshare.ui.components.CatCard
import moe.reimu.catshare.ui.components.CatCardStyle
import moe.reimu.catshare.viewmodel.SettingsViewModel

// ============================================================
// SettingsScreen — 设置界面
// 重构亮点：
//  - 使用 OutlinedTextField 替代基础 TextField
//  - 设置项卡片分组
//  - 保存成功反馈动画
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onCaptureLogs: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    // 保存成功后自动返回
    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            kotlinx.coroutines.delay(300)
            onBack()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "设置",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.saveSettings()
                        }
                    ) {
                        Icon(
                            Icons.Filled.Save,
                            contentDescription = "保存",
                            tint = MaterialTheme.colorScheme.primary
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
                .padding(innerPadding)
                .animateContentSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 设备名称 ──
            item(key = "device_name_section") {
                SectionHeader(title = "设备信息")
            }

            item(key = "device_name_card") {
                CatCard(
                    icon = Icons.Filled.Devices,
                    title = "设备名称",
                    subtitle = "其他设备在扫描时看到的名称",
                    style = CatCardStyle.Filled,
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.deviceName,
                    onValueChange = { viewModel.updateDeviceName(it) },
                    label = { Text("设备名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    shape = MaterialTheme.shapes.medium,
                )
            }

            // ── 功能开关 ──
            item(key = "features_section") {
                Spacer(modifier = Modifier.height(4.dp))
                SectionHeader(title = "功能设置")
            }

            item(key = "verbose_logging") {
                CatCard(
                    title = "详细日志",
                    subtitle = "启用后记录更详细的调试信息，便于排查问题",
                    icon = Icons.Filled.BugReport,
                    style = CatCardStyle.Filled,
                    trailing = {
                        Switch(
                            checked = uiState.verboseLogging,
                            onCheckedChange = { viewModel.toggleVerboseLogging() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                            )
                        )
                    }
                )
            }

            item(key = "auto_accept") {
                CatCard(
                    title = "自动接收",
                    subtitle = "收到文件时自动接受，无需手动确认",
                    icon = Icons.Filled.AutoAwesome,
                    style = CatCardStyle.Filled,
                    trailing = {
                        Switch(
                            checked = uiState.autoAccept,
                            onCheckedChange = { viewModel.toggleAutoAccept() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                            )
                        )
                    }
                )
            }

            // ── 调试工具 ──
            item(key = "debug_section") {
                Spacer(modifier = Modifier.height(4.dp))
                SectionHeader(title = "调试工具")
            }

            item(key = "capture_logs") {
                CatCard(
                    title = "捕获日志",
                    subtitle = "收集 logcat 日志并分享，用于问题诊断",
                    icon = Icons.Filled.BugReport,
                    style = CatCardStyle.Filled,
                    onClick = onCaptureLogs,
                )
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

/**
 * 分区标题
 */
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}