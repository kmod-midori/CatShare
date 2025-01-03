package moe.reimu.catshare

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.reimu.catshare.ui.DefaultCard
import moe.reimu.catshare.ui.theme.CatShareTheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CatShareTheme {
                SettingsActivityContent()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsActivityContent() {
    val activity = LocalContext.current as Activity
    val settings = remember(activity) { AppSettings(activity) }

    var deviceNameValue by remember {
        mutableStateOf(settings.deviceName)
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(text = stringResource(R.string.title_activity_settings)) },
            actions = {
                IconButton(onClick = {
                    val nameValue = deviceNameValue
                    if (nameValue.isNotBlank()) {
                        settings.deviceName = nameValue
                    }

                    activity.finish()
                }) {
                    Icon(
                        imageVector = Icons.Outlined.Check, contentDescription = "Save"
                    )
                }
            })
    }) { innerPadding ->
        val listState = rememberLazyListState()



        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            item {
                DefaultCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = deviceNameValue,
                            onValueChange = { deviceNameValue = it },
                            label = { Text(stringResource(R.string.device_name)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

            }
        }
    }
}