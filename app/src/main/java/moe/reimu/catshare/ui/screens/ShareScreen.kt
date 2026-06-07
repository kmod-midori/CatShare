package moe.reimu.catshare.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import moe.reimu.catshare.R
import moe.reimu.catshare.models.DiscoveredDevice
import moe.reimu.catshare.models.FileInfo
import moe.reimu.catshare.ui.components.CatCard
import moe.reimu.catshare.ui.components.CatCardVariant
import moe.reimu.catshare.ui.components.CatChildAppBar
import moe.reimu.catshare.ui.components.CatIcon
import moe.reimu.catshare.ui.components.CatIconSize
import moe.reimu.catshare.ui.components.CatPullRefresh
import moe.reimu.catshare.ui.components.EmptyState
import moe.reimu.catshare.ui.components.ScanningState
import moe.reimu.catshare.ui.viewmodel.ShareViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(
    files: List<FileInfo>,
    onBack: () -> Unit,
    onDeviceSelected: () -> Unit,
    viewModel: ShareViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.startScan()
    }

    Scaffold(
        topBar = {
            CatChildAppBar(
                title = stringResource(R.string.choose_recipient),
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // 顶部信息：正在发送的文件数
            if (files.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.sending),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "${files.size} ${
                            if (files.size == 1) {
                                files.firstOrNull()?.name ?: ""
                            } else {
                                "files"
                            }
                        }",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val onPick: (DiscoveredDevice) -> Unit = { device ->
                viewModel.startTask(device, files)
                onDeviceSelected()
            }

            CatPullRefresh(
                isRefreshing = false,
                onRefresh = { viewModel.startScan() },
                contentPadding = PaddingValues(16.dp),
            ) {
                when {
                    uiState.devices.isEmpty() -> {
                        item {
                            if (uiState.isScanning) {
                                ScanningState(
                                    icon = Icons.Filled.AccountCircle,
                                    title = stringResource(R.string.scanning_desc),
                                )
                            } else {
                                EmptyState(
                                    icon = Icons.Filled.AccountCircle,
                                    title = stringResource(R.string.choose_recipient),
                                    description = stringResource(R.string.scanning_desc),
                                    actionLabel = "Refresh",
                                    onAction = { viewModel.startScan() },
                                )
                            }
                        }
                    }
                    else -> {
                        items(
                            items = uiState.devices,
                            key = { it.id },
                        ) { device ->
                            DeviceRow(
                                device = device,
                                onClick = { onPick(device) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: DiscoveredDevice,
    onClick: () -> Unit,
) {
    CatCard(
        variant = CatCardVariant.Clickable,
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            CatIcon(
                imageVector = Icons.Filled.AccountCircle,
                contentDescription = null,
                size = CatIconSize.Medium,
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = device.brand ?: stringResource(R.string.unknown),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
