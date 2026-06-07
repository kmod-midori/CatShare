package moe.reimu.catshare.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import moe.reimu.catshare.R
import moe.reimu.catshare.ui.components.CatCard
import moe.reimu.catshare.ui.components.CatCardVariant
import moe.reimu.catshare.ui.components.CatTopAppBar
import moe.reimu.catshare.ui.viewmodel.SettingsViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.load(context)
    }

    Scaffold(
        topBar = {
            CatTopAppBar(
                title = stringResource(R.string.title_activity_settings),
                actions = {
                    IconButton(onClick = {
                        viewModel.save(context)
                        onSaved()
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = "Save",
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                CatCard(variant = CatCardVariant.Filled) {
                    OutlinedTextField(
                        value = uiState.deviceName,
                        onValueChange = viewModel::updateDeviceName,
                        label = { Text(stringResource(R.string.device_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            }

            item {
                CatCard(variant = CatCardVariant.Filled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.verbose_name),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = uiState.verbose,
                            onCheckedChange = { viewModel.toggleVerbose() },
                        )
                    }
                }
            }

            item {
                CatCard(variant = CatCardVariant.Filled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.auto_accept_name),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = uiState.autoAccept,
                            onCheckedChange = { viewModel.toggleAutoAccept() },
                        )
                    }
                }
            }

            item {
                CatCard(
                    variant = CatCardVariant.Outlined,
                    onClick = {
                        Thread {
                            try {
                                val logDir = File(context.cacheDir, "logs")
                                logDir.mkdirs()
                                val logFile = File(logDir, "logcat.txt")
                                logFile.outputStream().use {
                                    val proc = Runtime.getRuntime().exec("logcat -d")
                                    try {
                                        proc.inputStream.copyTo(it)
                                    } finally {
                                        proc.destroy()
                                    }
                                }
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileProvider",
                                    logFile,
                                )
                                val intent = Intent(Intent.ACTION_SEND)
                                    .putExtra(Intent.EXTRA_STREAM, uri)
                                    .setType("text/plain")
                                    .setFlags(
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                                Intent.FLAG_ACTIVITY_NEW_TASK
                                    )
                                context.startActivity(intent)
                            } catch (_: Throwable) {
                                // 静默失败
                            }
                        }.start()
                    },
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.capture_logs),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.capture_logs_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
