package moe.reimu.naiveshare

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import moe.reimu.naiveshare.services.GattServerService

class StartReceiverActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.getBooleanExtra("shouldStop", false)) {
            stopService(GattServerService.getIntent(this))
            Toast.makeText(this, R.string.receiver_stopped, Toast.LENGTH_SHORT).show()
        } else {
            startService(GattServerService.getIntent(this))
            Toast.makeText(this, R.string.receiver_started, Toast.LENGTH_SHORT).show()
        }

        finish()
    }

    companion object {
        fun getIntent(context: Context, shouldStop: Boolean): Intent {
            return Intent(context, StartReceiverActivity::class.java).apply {
                putExtra("shouldStop", shouldStop)
            }
        }
    }
}