package moe.reimu.catshare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import moe.reimu.catshare.ui.screens.SettingsScreen
import moe.reimu.catshare.ui.theme.CatShareTheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CatShareTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SettingsScreen(
                        onBack = { finish() },
                        onSaved = { finish() },
                    )
                }
            }
        }
    }
}
