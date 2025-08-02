package moe.reimu.catshare.models

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class FileInfo(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val size: Long,
    val textContent: String?,
) : Parcelable

