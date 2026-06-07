package moe.reimu.catshare.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import moe.reimu.catshare.R
import moe.reimu.catshare.ui.components.CatCard
import moe.reimu.catshare.ui.components.CatCardVariant
import moe.reimu.catshare.ui.components.CatHeroCard
import moe.reimu.catshare.ui.components.CatIcon
import moe.reimu.catshare.ui.components.CatIconSize
import moe.reimu.catshare.ui.components.CatTopAppBar
import moe.reimu.catshare.ui.viewmodel.MainViewModel
import rikka.shizuku.Shizuku

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(
    onNavigateSettings: () -> Unit,
    onPickFiles: () -> Unit,
    viewModel: MainViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val macPermissionState = rememberPermissionState("android.permission.LOCAL_MAC_ADDRESS")
    val scrollState = rememberLazyListState()

    Scaffold(
        topBar = {
            CatTopAppBar(
                title = stringResource(R.string.app_name),
                actions = {
                    IconButton(onClick = onNavigateSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.title_activity_settings),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            state = scrollState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                CatHeroCard(
                    title = stringResource(R.string.send),
                    subtitle = stringResource(R.string.send_desc),
                    onClick = onPickFiles,
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                        )
                    },
                )
            }

            item {
                CatCard(variant = CatCardVariant.Filled) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CatIcon(
                            imageVector = Icons.AutoMirrored.Filled.BluetoothSearching,
                            contentDescription = null,
                            size = CatIconSize.Medium,
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.discoverable),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = stringResource(R.string.discoverable_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = uiState.isReceiverRunning,
                            onCheckedChange = { target ->
                                viewModel.toggleReceiver(target)
                            },
                        )
                    }
                }
            }

            if (!macPermissionState.status.isGranted) {
                item {
                    val (stateIcon, stateTitle, stateDesc) = when {
                        uiState.shizukuAvailable && uiState.shizukuGranted -> Triple(
                            Icons.Filled.Done,
                            stringResource(R.string.shizuku_available),
                            stringResource(R.string.shizuku_desc),
                        )
                        uiState.shizukuAvailable && !uiState.shizukuGranted -> Triple(
                            Icons.Filled.Close,
                            stringResource(R.string.shizuku_not_granted),
                            stringResource(R.string.shizuku_desc),
                        )
                        else -> Triple(
                            Icons.Filled.Close,
                            stringResource(R.string.shizuku_unavailable),
                            stringResource(R.string.shizuku_desc),
                        )
                    }
                    CatCard(
                        variant = CatCardVariant.Outlined,
                        onClick = if (!uiState.shizukuGranted) {
                            {
                                try {
                                    Shizuku.requestPermission(0)
                                } catch (_: Throwable) {
                                }
                            }
                        } else null,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CatIcon(
                                imageVector = stateIcon,
                                contentDescription = null,
                                size = CatIconSize.Medium,
                                containerColor = if (uiState.shizukuGranted) {
                                    MaterialTheme.colorScheme.tertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.errorContainer
                                },
                            )
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = stateTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = stateDesc,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
