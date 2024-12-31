package moe.reimu.naiveshare.models

import android.net.Uri

data class ReceivedFile(
    val name: String,
    val uri: Uri,
    val mimeType: String
)
